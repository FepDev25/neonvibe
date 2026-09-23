package com.neonvibe.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.neonvibe.domain.User;
import com.neonvibe.dto.AuthResponse;
import com.neonvibe.dto.GoogleTokenRequest;
import com.neonvibe.dto.RefreshTokenRequest;
import com.neonvibe.dto.UserResponse;
import com.neonvibe.exception.AccountNotAllowedException;
import com.neonvibe.exception.InvalidTokenException;
import com.neonvibe.repository.UserRepository;
import com.neonvibe.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Authentication service: validates Google id_tokens (or mock tokens in dev/test),
 * creates/updates users and issues internal JWT access + refresh tokens.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final boolean googleEnabled;
    private final String tokenInfoUrl;
    private final String googleClientId;
    private final Set<String> allowedEmails;

    public AuthService(JwtTokenProvider tokenProvider,
                       UserRepository userRepository,
                       @Value("${neonvibe.auth.google.enabled:true}") boolean googleEnabled,
                       @Value("${neonvibe.auth.google.tokeninfo-url:https://oauth2.googleapis.com/tokeninfo}") String tokenInfoUrl,
                       @Value("${neonvibe.auth.google.client-id:}") String googleClientId,
                       @Value("${neonvibe.auth.allowed-emails:}") String allowedEmails) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.googleEnabled = googleEnabled;
        this.tokenInfoUrl = tokenInfoUrl;
        this.googleClientId = googleClientId == null ? "" : googleClientId.trim();
        this.allowedEmails = parseAllowedEmails(allowedEmails);
        if (this.allowedEmails.isEmpty()) {
            log.warn("neonvibe.auth.allowed-emails is empty: ANY Google account can register. "
                    + "Set ALLOWED_EMAILS before exposing this server publicly.");
        }
    }

    private static Set<String> parseAllowedEmails(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Validates a Google id_token, creates or updates the user and issues tokens.
     */
    @Transactional
    public AuthResponse googleLogin(GoogleTokenRequest request) {
        GoogleUserInfo info = resolveGoogleUser(request.idToken());
        assertAllowed(info.email());
        User user = findOrCreate(info);
        return issueTokens(user);
    }

    /**
     * Enforces the email allowlist. An empty allowlist means "no restriction"
     * (dev/local); production requires a non-empty list — see {@code ProdStartupGuard}.
     */
    private void assertAllowed(String email) {
        if (allowedEmails.isEmpty()) {
            return;
        }
        if (!allowedEmails.contains(email.toLowerCase(Locale.ROOT))) {
            log.warn("Rejected login for non-allowlisted account: {}", email);
            throw new AccountNotAllowedException("This account is not authorized for this server");
        }
    }

    /**
     * Exchanges a valid refresh token for a new access token (and returns the same
     * refresh token; no rotation in the MVP).
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String subject;
        try {
            subject = tokenProvider.validateRefreshToken(request.refreshToken());
        } catch (Exception ex) {
            throw new InvalidTokenException("Invalid or expired refresh token", ex);
        }
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid refresh token subject", ex);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User for refresh token not found"));
        return new AuthResponse(
                tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName()),
                request.refreshToken(),
                "Bearer",
                tokenProvider.getAccessTokenExpirationMs());
    }

    /**
     * Returns the current user from the authenticated principal (used by GET /auth/me).
     */
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user not found"));
        return toResponse(user);
    }

    private GoogleUserInfo resolveGoogleUser(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidTokenException("id_token must not be blank");
        }
        if (!googleEnabled) {
            // Dev/test mock: accept any non-blank token and derive a stable test user.
            log.debug("Google auth disabled; accepting mock token");
            String email = "dev-" + Integer.toHexString(idToken.hashCode()) + "@neonvibe.local";
            return new GoogleUserInfo(idToken, email, "Dev User", null);
        }
        return fetchGoogleUserInfo(idToken);
    }

    private GoogleUserInfo fetchGoogleUserInfo(String idToken) {
        if (googleClientId.isEmpty()) {
            // Without the client id we cannot verify the token's audience, which
            // would let id_tokens minted for ANY other Google app log in here.
            throw new InvalidTokenException(
                    "Google login is not configured on this server (missing GOOGLE_CLIENT_ID)");
        }
        Map<?, ?> payload;
        try {
            RestClient client = RestClient.create();
            payload = client.get()
                    .uri(tokenInfoUrl, uri -> uri.queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            log.warn("Google token validation failed", ex);
            throw new InvalidTokenException("Failed to validate Google id_token", ex);
        }
        if (payload == null) {
            throw new InvalidTokenException("Google did not validate the id_token");
        }

        // Google's tokeninfo proves the token is authentic, NOT that it was issued
        // for us. Without this check any id_token from any Google-Sign-In site
        // would be accepted (confused deputy).
        Object aud = payload.get("aud");
        if (aud == null || !googleClientId.equals(aud.toString())) {
            log.warn("Rejected id_token issued for a different audience: {}", aud);
            throw new InvalidTokenException("id_token was not issued for this application");
        }
        if (!isTrue(payload.get("email_verified"))) {
            throw new InvalidTokenException("Google account email is not verified");
        }

        Object sub = payload.get("sub");
        Object email = payload.get("email");
        if (sub == null || email == null) {
            throw new InvalidTokenException("Google id_token missing required claims");
        }
        String name = payload.get("name") != null ? payload.get("name").toString() : null;
        String picture = payload.get("picture") != null ? payload.get("picture").toString() : null;
        return new GoogleUserInfo(sub.toString(), email.toString(), name, picture);
    }

    /** tokeninfo returns claims as strings ({@code "true"}); the JWK path returns booleans. */
    private static boolean isTrue(Object claim) {
        return claim instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(claim));
    }

    private User findOrCreate(GoogleUserInfo info) {
        return userRepository.findByGoogleId(info.googleId())
                .orElseGet(() -> userRepository.findByEmail(info.email())
                        .map(user -> {
                            user.setGoogleId(info.googleId());
                            return user;
                        })
                        .orElseGet(() -> {
                            User user = new User();
                            user.setGoogleId(info.googleId());
                            user.setEmail(info.email());
                            user.setName(info.name() != null ? info.name() : info.email());
                            user.setAvatarUrl(info.avatarUrl());
                            return user;
                        }));
    }

    private AuthResponse issueTokens(User user) {
        User saved = userRepository.save(user);
        String access = tokenProvider.generateAccessToken(saved.getId(), saved.getEmail(), saved.getName());
        String refresh = tokenProvider.generateRefreshToken(saved.getId(), saved.getEmail());
        return new AuthResponse(access, refresh, "Bearer", tokenProvider.getAccessTokenExpirationMs());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getAvatarUrl(), user.getCreatedAt());
    }

    /**
     * Minimal representation of the identity resolved from Google (or the mock).
     */
    record GoogleUserInfo(String googleId, String email, String name, String avatarUrl) {
    }
}
