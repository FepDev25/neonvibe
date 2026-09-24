package com.neonvibe.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link MusicBrainzClient}: release-group search, Cover Art
 * Archive fetch, required User-Agent, empty/error handling.
 */
class MusicBrainzClientTest {

    private MockRestServiceServer server;
    private MusicBrainzClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MusicBrainzClient(builder.build());
    }

    @Test
    void fetchArtwork_usesFirstReleaseGroupAndSendsUserAgent() {
        server.expect(requestTo(containsString("musicbrainz.org/ws/2/release-group")))
                .andExpect(header("User-Agent", containsString("NeonVibe")))
                .andRespond(withSuccess("{\"release-groups\":[{\"id\":\"mbid-1\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://coverartarchive.org/release-group/mbid-1/front-500"))
                .andRespond(withSuccess(new byte[]{9, 8}, MediaType.IMAGE_JPEG));

        assertThat(client.fetchArtwork("Album", "Artist")).containsExactly(9, 8);
        server.verify();
    }

    @Test
    void fetchArtwork_noGroups_returnsNull() {
        server.expect(requestTo(containsString("musicbrainz.org/ws/2/release-group")))
                .andRespond(withSuccess("{\"release-groups\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchArtwork("Album", "Artist")).isNull();
    }

    @Test
    void fetchArtwork_notFound_returnsNull() {
        server.expect(requestTo(containsString("musicbrainz.org/ws/2/release-group")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.fetchArtwork("Album", "Artist")).isNull();
    }

    @Test
    void fetchArtwork_blankAlbum_returnsNullWithoutRequest() {
        assertThat(client.fetchArtwork("", "Artist")).isNull();
        server.verify();
    }
}
