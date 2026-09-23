package com.neonvibe.infra;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Last.fm MD5 signing (api_sig) and config gating.
 */
class LastFmClientTest {

    private LastFmClient client(String key, String secret) {
        return new LastFmClient(RestClient.create(), key, secret);
    }

    @Test
    void notConfigured_whenSecretMissing() {
        assertFalse(client("k", "").configured());
        assertFalse(client("", "s").configured());
        assertTrue(client("k", "s").configured());
    }

    @Test
    void sign_sortsParamsAndAppendsSecret() {
        // api_sig = md5("api_keymykeyartistMy Artistmethodtrack.scrobble" + "mysecret")
        String expected = "989e03bfabf6eb91ed19dd6b6f720249";
        String actual = client("mykey", "mysecret").sign(Map.of(
                "method", "track.scrobble",
                "artist", "My Artist",
                "api_key", "mykey"));
        assertEquals(expected, actual);
    }
}
