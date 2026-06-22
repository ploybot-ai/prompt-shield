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

    private final WebClient webClient;

    public AiProviderClient(String baseUrl, String apiKey) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        logger.debug("AiProviderClient: Sending request to provider, model={}", request.model());

        return webClient.post()
                .uri("/v1/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatCompletionResponse.class)
                .block();
    }

    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        logger.debug("AiProviderClient: Sending streaming request to provider, model={}", request.model());

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
                .bodyValue(streamRequest)
                .retrieve()
                .bodyToFlux(ChatCompletionResponse.class);
    }

    public Object listModels() {
        logger.debug("AiProviderClient: Fetching available models");

        return webClient.get()
                .uri("/v1/models")
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }
}
