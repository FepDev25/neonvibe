package com.neonvibe.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link LrclibClient}: synced/plain preference, the fallback
 * query when the primary one misses, blank-title short-circuit and error
 * tolerance.
 */
class LrclibClientTest {

    private MockRestServiceServer server;
    private LrclibClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LrclibClient(builder.build());
    }

    @Test
    void fetch_prefersSyncedLyrics() {
        server.expect(requestTo(containsString("lrclib.net/api/get")))
                .andRespond(withSuccess("{\"syncedLyrics\":\"[00:01.00] hi\",\"plainLyrics\":\"hi\"}",
                        MediaType.APPLICATION_JSON));

        LrclibClient.LyricsResult result = client.fetch("Artist", "Title", "Album", 200);

        assertThat(result.synced()).isTrue();
        assertThat(result.text()).isEqualTo("[00:01.00] hi");
    }

    @Test
    void fetch_plainOnly_isNotSynced() {
        server.expect(requestTo(containsString("lrclib.net/api/get")))
                .andRespond(withSuccess("{\"plainLyrics\":\"just text\"}", MediaType.APPLICATION_JSON));

        LrclibClient.LyricsResult result = client.fetch("Artist", "Title", "Album", 200);

        assertThat(result.synced()).isFalse();
        assertThat(result.text()).isEqualTo("just text");
    }

    @Test
    void fetch_primaryMiss_fallsBackToSecondQuery() {
        server.expect(requestTo(containsString("lrclib.net/api/get")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(containsString("lrclib.net/api/get")))
                .andRespond(withSuccess("{\"plainLyrics\":\"fallback\"}", MediaType.APPLICATION_JSON));

        LrclibClient.LyricsResult result = client.fetch("Artist", "Title", "Album", 200);

        assertThat(result.text()).isEqualTo("fallback");
        server.verify();
    }

    @Test
    void fetch_bothMisses_returnsNull() {
        server.expect(requestTo(containsString("lrclib.net/api/get")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(containsString("lrclib.net/api/get")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetch("Artist", "Title", "Album", 200)).isNull();
    }

    @Test
    void fetch_blankTitle_returnsNullWithoutRequest() {
        assertThat(client.fetch("Artist", "  ", "Album", 200)).isNull();
        server.verify();
    }
}
