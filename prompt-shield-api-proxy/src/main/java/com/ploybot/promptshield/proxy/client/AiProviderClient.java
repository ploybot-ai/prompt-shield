package com.ploybot.promptshield.proxy.client;

import com.ploybot.promptshield.proxy.model.ChatCompletionRequest;
import com.ploybot.promptshield.proxy.model.ChatCompletionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

public class AiProviderClient {

    private static final Logger logger = LoggerFactory.getLogger(AiProviderClient.class);

    private final String baseUrl;
    private final String defaultApiKey;
    private final WebClient webClient;

    public AiProviderClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.defaultApiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request, String apiKey) {
        logger.debug("AiProviderClient: Sending request to provider, model={}", request.model());

        String effectiveApiKey = apiKey != null ? apiKey : defaultApiKey;

        return webClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + effectiveApiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .block();
    }

    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request, String apiKey) {
        logger.debug("AiProviderClient: Sending streaming request to provider, model={}", request.model());

        String effectiveApiKey = apiKey != null ? apiKey : defaultApiKey;

        ChatCompletionRequest streamRequest = new ChatCompletionRequest(
                request.model(),
                request.messages(),
                request.maxTokens(),
                request.temperature(),
                request.topP(),
                true,
                request.tools(),
                request.toolChoice()
        );

        return webClient.post()
                .uri("/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + effectiveApiKey)
                .bodyValue(streamRequest)
                .retrieve()
                .bodyToFlux(ChatCompletionResponse.class);
    }

    public Object listModels(String apiKey) {
        logger.debug("AiProviderClient: Fetching available models");

        String effectiveApiKey = apiKey != null ? apiKey : defaultApiKey;

        return webClient.get()
                .uri("/v1/models")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + effectiveApiKey)
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }
}
