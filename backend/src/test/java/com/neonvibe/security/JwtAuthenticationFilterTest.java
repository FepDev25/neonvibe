package com.neonvibe.security;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>Covers the two token sources (Authorization header and the {@code ?token=}
 * query param restricted to media endpoints), rejection of invalid/refresh
 * tokens, and the guarantee that an already-populated SecurityContext is left
 * untouched.</p>
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "test-secret-test-secret-test-secret-test-secret-1234567890"; // >= 32 bytes for HS256

    private final UUID userId = UUID.randomUUID();

    private JwtTokenProvider provider;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 900_000, 604_800_000);
        filter = new JwtAuthenticationFilter(provider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String accessToken() {
        return provider.generateAccessToken(userId, "u@example.com", "User");
    }

    @Test
    void bearerHeader_validAccessToken_authenticates() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks");
        request.addHeader("Authorization", "Bearer " + accessToken());

        Authentication auth = run(request);

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);
        assertThat(((UserPrincipal) auth.getPrincipal()).id()).isEqualTo(userId);
    }

    @Test
    void queryParamToken_onStreamEndpoint_authenticates() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks/1/stream");
        request.setParameter("token", accessToken());

        Authentication auth = run(request);

        assertThat(auth).isNotNull();
        assertThat(((UserPrincipal) auth.getPrincipal()).id()).isEqualTo(userId);
    }

    @Test
    void queryParamToken_onCoverEndpoint_authenticates() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/albums/1/cover");
        request.setParameter("token", accessToken());

        assertThat(run(request)).isNotNull();
    }

    @Test
    void queryParamToken_onTrackCoverEndpoint_authenticates() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks/1/cover");
        request.setParameter("token", accessToken());

        assertThat(run(request)).isNotNull();
    }

    @Test
    void queryParamToken_onNonMediaPath_isIgnored() throws Exception {
        // A cover-looking path under an unrelated resource must NOT accept the
        // token from the query string (keeps the JWT surface minimal).
        MockHttpServletRequest request = request("GET", "/api/v1/playlists/1/cover");
        request.setParameter("token", accessToken());

        assertThat(run(request)).isNull();
    }

    @Test
    void queryParamToken_onPlainApiPath_isIgnored() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks");
        request.setParameter("token", accessToken());

        assertThat(run(request)).isNull();
    }

    @Test
    void noToken_leavesRequestUnauthenticated() throws Exception {
        assertThat(run(request("GET", "/api/v1/tracks"))).isNull();
    }

    @Test
    void invalidToken_leavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks");
        request.addHeader("Authorization", "Bearer not-a-jwt");

        assertThat(run(request)).isNull();
    }

    @Test
    void refreshTokenInBearerHeader_isRejected() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks");
        request.addHeader("Authorization", "Bearer " + provider.generateRefreshToken(userId, "u@example.com"));

        assertThat(run(request)).isNull();
    }

    @Test
    void nonBearerHeader_fallsBackToQueryParam() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/tracks/1/stream");
        request.addHeader("Authorization", "Basic abc");
        request.setParameter("token", accessToken());

        assertThat(run(request)).isNotNull();
    }

    @Test
    void existingAuthentication_isNotOverwritten() throws Exception {
        UserPrincipal existing = new UserPrincipal(UUID.randomUUID(), "e@example.com", "Existing");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(existing, null, List.of()));

        MockHttpServletRequest request = request("GET", "/api/v1/tracks");
        request.addHeader("Authorization", "Bearer " + accessToken());

        run(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(((UserPrincipal) auth.getPrincipal()).id()).isEqualTo(existing.id());
    }
}
