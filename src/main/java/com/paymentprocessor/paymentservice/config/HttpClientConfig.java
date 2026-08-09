package com.paymentprocessor.paymentservice.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * Builds {@link RestClient} / {@link WebClient} instances for outbound connector
 * calls. Each client is created with per-endpoint connect/read timeouts so a slow
 * gateway can never exhaust the request-handling threads of the payment service.
 */
@Configuration
@EnableConfigurationProperties(ConnectorProperties.class)
public class HttpClientConfig {

    /**
     * Factory bean the connectors use to build a configured {@link RestClient}
     * for a given endpoint (base URL, timeouts, API key header).
     */
    @Bean
    public RestClientFactory restClientFactory() {
        return new RestClientFactory();
    }

    /**
     * Factory bean for the in-process-service connectors (fraud, vault, limit),
     * which are built on {@link WebClient} per the platform convention for
     * calling sibling Spring services in this repo.
     */
    @Bean
    public WebClientFactory webClientFactory() {
        return new WebClientFactory();
    }

    /** Small factory that turns an {@link ConnectorProperties.Endpoint} into a RestClient. */
    public static class RestClientFactory {
        public RestClient build(ConnectorProperties.Endpoint ep) {
            ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofMillis(ep.getConnectTimeoutMs()))
                    .withReadTimeout(Duration.ofMillis(ep.getReadTimeoutMs()));
            ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

            RestClient.Builder builder = RestClient.builder()
                    .baseUrl(ep.getBaseUrl())
                    .requestFactory(requestFactory);
            if (ep.getApiKey() != null && !ep.getApiKey().isBlank()) {
                builder = builder.defaultHeader("Authorization", "Bearer " + ep.getApiKey());
            }
            return builder.build();
        }
    }

    /** Small factory that turns an {@link ConnectorProperties.Endpoint} into a WebClient. */
    public static class WebClientFactory {
        public WebClient build(ConnectorProperties.Endpoint ep) {
            HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, ep.getConnectTimeoutMs())
                    .responseTimeout(Duration.ofMillis(ep.getReadTimeoutMs()))
                    .doOnConnected(conn -> conn
                            .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(
                                    ep.getReadTimeoutMs(), TimeUnit.MILLISECONDS)));

            WebClient.Builder builder = WebClient.builder()
                    .baseUrl(ep.getBaseUrl())
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));
            if (ep.getApiKey() != null && !ep.getApiKey().isBlank()) {
                builder = builder.defaultHeader("Authorization", "Bearer " + ep.getApiKey());
            }
            return builder.build();
        }
    }
}
