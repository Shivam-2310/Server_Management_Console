package com.management.console.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    // Maximum buffer limit for large logfile responses (unlimited = -1)
    private static final int MAX_BUFFER_SIZE = -1; // Unlimited - maximum memory limit

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        // Configure exchange strategies with increased buffer limit
        // This is CRITICAL for large logfile responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(MAX_BUFFER_SIZE);
                    // Also configure for text/plain responses
                    configurer.defaultCodecs().enableLoggingRequestDetails(true);
                })
                .build();

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60)) // Increased for large responses
                .followRedirect(true);

        WebClient.Builder builder = WebClient.builder()
                .exchangeStrategies(strategies) // This MUST be set before building
                .clientConnector(new ReactorClientHttpConnector(httpClient));
        
        // Log configuration
        System.out.println("WebClient.Builder configured with buffer limit: " + 
                (MAX_BUFFER_SIZE == -1 ? "UNLIMITED" : MAX_BUFFER_SIZE + " bytes"));
        
        return builder;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}

