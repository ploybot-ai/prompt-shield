package com.ploybot.promptshield.proxy.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * Forwards raw OpenAI-compatible JSON to the provider, so fields the proxy does not
 * know about ({@code top_p}, {@code response_format}, multimodal content...) are kept.
 */
public class AiProviderClient {

    private static final Logger logger = LoggerFactory.getLogger(AiProviderClient.class);
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE = new ParameterizedTypeReference<>() {
    };

    /** Status and body returned by the provider. */
    public record ProviderResponse(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    private final WebClient webClient;

    public AiProviderClient(String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * @param authorization value for the upstream {@code Authorization} header, or
     *                      {@code null} to send none
     */
    public ProviderResponse chatCompletion(String requestJson, String authorization) {
        logger.debug("AiProviderClient: Sending chat completion request to provider");
        return webClient.post()
                .uri("/v1/chat/completions")
                .headers(headers -> setAuthorization(headers, authorization))
                .bodyValue(requestJson)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new ProviderResponse(response.statusCode().value(), body)))
                .block();
    }

    /**
     * Streams the {@code data} payload of each server-sent event, stopping at the
     * {@code [DONE]} sentinel, which is not emitted.
     */
    public Flux<String> chatCompletionStream(String requestJson, String authorization) {
        logger.debug("AiProviderClient: Sending streaming chat completion request to provider");
        return webClient.post()
                .uri("/v1/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> setAuthorization(headers, authorization))
                .bodyValue(requestJson)
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .map(ServerSentEvent::data)
                .filter(Objects::nonNull)
                .takeWhile(data -> !"[DONE]".equals(data.strip()));
    }

    public ProviderResponse listModels(String authorization) {
        logger.debug("AiProviderClient: Fetching available models");
        return webClient.get()
                .uri("/v1/models")
                .headers(headers -> setAuthorization(headers, authorization))
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new ProviderResponse(response.statusCode().value(), body)))
                .block();
    }

    private static void setAuthorization(HttpHeaders headers, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }
}
