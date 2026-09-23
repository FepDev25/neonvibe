package com.neonvibe.config;

import java.net.Proxy;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provides a {@link RestClient} for outbound calls to external services
 * (iTunes, MusicBrainz, Last.fm, LRCLIB) with short timeouts so a slow third
 * party never blocks a request for long.
 *
 * <p>The factory forces direct connections ({@link Proxy#NO_PROXY}) because the
 * JVM may inherit a broken system ProxySelector that would make every external
 * call fail with "Failed to select a proxy".</p>
 */
@Configuration
public class HttpClientConfig {

    @Bean
    RestClient externalRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(8));
        factory.setProxy(Proxy.NO_PROXY);
        return RestClient.builder().requestFactory(factory).build();
    }
}
