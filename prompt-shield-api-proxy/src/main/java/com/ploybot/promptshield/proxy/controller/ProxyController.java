package com.ploybot.promptshield.proxy.controller;

import com.ploybot.promptshield.proxy.client.AiProviderClient.ProviderResponse;
import com.ploybot.promptshield.proxy.service.ProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("${prompt-shield.proxy.base-path:/v1}")
public class ProxyController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);
    private static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"message\":\"Missing or invalid proxy token\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}";

    private final ProxyService proxyService;
    private final ProxyAuthorization authorization;

    public ProxyController(ProxyService proxyService, ProxyAuthorization authorization) {
        this.proxyService = proxyService;
        this.authorization = authorization;
    }

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object chatCompletion(@RequestBody String request,
                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String clientAuthorization) {
        Optional<ProxyAuthorization.Upstream> upstream = authorization.resolve(clientAuthorization);
        if (upstream.isEmpty()) {
            return unauthorized();
        }
        if (ProxyService.isStreamRequest(request)) {
            return stream(request, upstream.get().authorization());
        }
        return toResponseEntity(proxyService.chatCompletion(request, upstream.get().authorization()));
    }

    private SseEmitter stream(String request, String upstreamAuthorization) {
        // No timeout: model responses can stream for minutes
        SseEmitter emitter = new SseEmitter(0L);
        Disposable subscription = proxyService.chatCompletionStream(request, upstreamAuthorization)
                .subscribe(
                        data -> send(emitter, data),
                        error -> {
                            logger.warn("ProxyController: Streaming from provider failed: {}", error.getMessage());
                            emitter.completeWithError(error);
                        },
                        () -> {
                            send(emitter, "[DONE]");
                            emitter.complete();
                        });
        // Stop reading from the provider when the client goes away
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(subscription::dispose);
        emitter.onError(error -> subscription.dispose());
        return emitter;
    }

    private static void send(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listModels(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String clientAuthorization) {
        Optional<ProxyAuthorization.Upstream> upstream = authorization.resolve(clientAuthorization);
        if (upstream.isEmpty()) {
            return unauthorized();
        }
        return toResponseEntity(proxyService.listModels(upstream.get().authorization()));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "prompt-shield-api-proxy");
    }

    /** The message names rules and sources only, never the flagged text. */
    @ExceptionHandler(com.ploybot.promptshield.injection.PromptInjectionException.class)
    public ResponseEntity<String> injectionBlocked(com.ploybot.promptshield.injection.PromptInjectionException e) {
        String categories = e.getVerdicts().stream()
                .flatMap(v -> v.categories().stream())
                .distinct()
                .map(c -> "\"" + c.name().toLowerCase(java.util.Locale.ROOT) + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"Request blocked by Prompt Shield: possible prompt injection\","
                        + "\"type\":\"invalid_request_error\",\"code\":\"prompt_injection_detected\","
                        + "\"categories\":[" + categories + "]}}");
    }

    @ExceptionHandler(ProxyService.InvalidRequestException.class)
    public ResponseEntity<String> invalidRequest(ProxyService.InvalidRequestException e) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"" + e.getMessage() + "\",\"type\":\"invalid_request_error\"}}");
    }

    private static ResponseEntity<String> toResponseEntity(ProviderResponse response) {
        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    private static ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(UNAUTHORIZED_BODY);
    }
}
