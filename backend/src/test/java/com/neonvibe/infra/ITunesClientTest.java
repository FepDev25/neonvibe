package com.neonvibe.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link iTunesClient}: search parsing, the 100x100 -> 600x600
 * artwork upgrade, empty results and error tolerance.
 */
class ITunesClientTest {

    private MockRestServiceServer server;
    private iTunesClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new iTunesClient(builder.build());
    }

    @Test
    void fetchArtwork_upgradesThumbnailAndReturnsBytes() {
        server.expect(requestTo(containsString("itunes.apple.com/search")))
                .andExpect(requestTo(containsString("term=Album%20Artist")))
                .andRespond(withSuccess("{\"results\":[{\"artworkUrl100\":\"https://img/100x100bb.jpg\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://img/600x600bb.jpg"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG));

        byte[] art = client.fetchArtwork("Album", "Artist");

        assertThat(art).containsExactly(1, 2, 3);
        server.verify();
    }

    @Test
    void fetchArtwork_noResults_returnsNull() {
        server.expect(requestTo(containsString("itunes.apple.com/search")))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchArtwork("Album", "Artist")).isNull();
    }

    @Test
    void fetchArtwork_missingArtworkUrl_returnsNull() {
        server.expect(requestTo(containsString("itunes.apple.com/search")))
                .andRespond(withSuccess("{\"results\":[{\"collectionName\":\"x\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchArtwork("Album", "Artist")).isNull();
    }

    @Test
    void fetchArtwork_blankAlbum_returnsNullWithoutRequest() {
        assertThat(client.fetchArtwork("  ", "Artist")).isNull();
        server.verify();
    }

    @Test
    void fetchArtwork_httpError_returnsNull() {
        server.expect(requestTo(containsString("itunes.apple.com/search")))
                .andRespond(withServerError());

        assertThat(client.fetchArtwork("Album", "Artist")).isNull();
    }
}
