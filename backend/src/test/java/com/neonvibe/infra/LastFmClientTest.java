package com.neonvibe.infra;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link LastFmClient}: MD5 api_sig signing, config gating, the
 * OAuth token/session calls, scrobbling and image lookup.
 */
class LastFmClientTest {

    private MockRestServiceServer server;
    private LastFmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LastFmClient(builder.build(), "mykey", "mysecret");
    }

    private LastFmClient plainClient(String key, String secret) {
        return new LastFmClient(RestClient.create(), key, secret);
    }

    @Test
    void notConfigured_whenSecretMissing() {
        assertFalse(plainClient("k", "").configured());
        assertFalse(plainClient("", "s").configured());
        assertTrue(plainClient("k", "s").configured());
    }

    @Test
    void enabled_onlyNeedsApiKey() {
        assertTrue(plainClient("k", "").enabled());
        assertFalse(plainClient("", "s").enabled());
    }

    @Test
    void sign_sortsParamsAndAppendsSecret() {
        // api_sig = md5("api_keymykeyartistMy Artistmethodtrack.scrobble" + "mysecret")
        String expected = "989e03bfabf6eb91ed19dd6b6f720249";
        String actual = plainClient("mykey", "mysecret").sign(Map.of(
                "method", "track.scrobble",
                "artist", "My Artist",
                "api_key", "mykey"));
        assertEquals(expected, actual);
    }

    @Test
    void sign_excludesFormatAndCallback() {
        String bare = client.sign(Map.of("method", "auth.gettoken", "api_key", "mykey"));
        String withFormat = client.sign(Map.of(
                "method", "auth.gettoken", "api_key", "mykey", "format", "json"));
        String withCallback = client.sign(Map.of(
                "method", "auth.gettoken", "api_key", "mykey", "callback", "cb"));

        assertEquals(bare, withFormat);
        assertEquals(bare, withCallback);
    }

    @Test
    void authUrl_containsKeyAndToken() {
        assertThat(client.authUrl("tok"))
                .isEqualTo("https://www.last.fm/api/auth/?api_key=mykey&token=tok");
    }

    @Test
    void requestToken_returnsToken() {
        server.expect(requestTo(containsString("method=auth.gettoken")))
                .andRespond(withSuccess("{\"token\":\"tok\"}", MediaType.APPLICATION_JSON));

        assertThat(client.requestToken()).isEqualTo("tok");
        server.verify();
    }

    @Test
    void getSession_returnsSession() {
        server.expect(requestTo(containsString("method=auth.getsession")))
                .andRespond(withSuccess("{\"session\":{\"name\":\"dj\",\"key\":\"sk\"}}",
                        MediaType.APPLICATION_JSON));

        LastFmClient.Session session = client.getSession("tok");

        assertThat(session.username()).isEqualTo("dj");
        assertThat(session.key()).isEqualTo("sk");
    }

    @Test
    void scrobble_postsSignedRequest() {
        server.expect(requestTo(containsString("method=track.scrobble")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(requestTo(containsString("api_sig=")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.scrobble("sk", "Artist", "Track", "Album", 200, 123L);

        server.verify();
    }

    @Test
    void scrobble_swallowsFailures() {
        server.expect(requestTo(containsString("method=track.scrobble")))
                .andRespond(withServerError());

        assertThatCode(() -> client.scrobble("sk", "Artist", "Track", "Album", 200, 123L))
                .doesNotThrowAnyException();
    }

    @Test
    void fetchAlbumImage_picksLargestAndReturnsBytes() {
        server.expect(requestTo(containsString("method=album.getinfo")))
                .andRespond(withSuccess("{\"album\":{\"image\":["
                        + "{\"size\":\"small\",\"#text\":\"http://img/s.jpg\"},"
                        + "{\"size\":\"mega\",\"#text\":\"http://img/m.jpg\"}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://img/m.jpg"))
                .andRespond(withSuccess(new byte[]{5, 6}, MediaType.IMAGE_JPEG));

        assertThat(client.fetchAlbumImage("Album", "Artist")).containsExactly(5, 6);
        server.verify();
    }

    @Test
    void fetchArtistImage_returnsBytes() {
        server.expect(requestTo(containsString("method=artist.getinfo")))
                .andRespond(withSuccess("{\"artist\":{\"image\":["
                        + "{\"size\":\"extralarge\",\"#text\":\"http://img/a.jpg\"}]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://img/a.jpg"))
                .andRespond(withSuccess(new byte[]{1}, MediaType.IMAGE_JPEG));

        assertThat(client.fetchArtistImage("Artist")).containsExactly(1);
    }

    @Test
    void fetchImage_notEnabled_returnsNull() {
        LastFmClient disabled = plainClient("", "");

        assertThat(disabled.fetchAlbumImage("Album", "Artist")).isNull();
        assertThat(disabled.fetchArtistImage("Artist")).isNull();
    }
}
