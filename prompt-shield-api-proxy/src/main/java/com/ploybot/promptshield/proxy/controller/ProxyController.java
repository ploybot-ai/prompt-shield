package com.ploybot.promptshield.proxy.controller;

import com.ploybot.promptshield.proxy.model.ChatCompletionRequest;
import com.ploybot.promptshield.proxy.model.ChatCompletionResponse;
import com.ploybot.promptshield.proxy.service.ProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("${prompt-shield.proxy.base-path:/v1}")
public class ProxyController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping(value = "/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object chatCompletion(@RequestBody ChatCompletionRequest request) {
        logger.debug("ProxyController: Received chat completion request, model={}", request.model());

        if (Boolean.TRUE.equals(request.stream())) {
            return chatCompletionStream(request);
        }

        return proxyService.chatCompletion(request);
    }

    private SseEmitter chatCompletionStream(ChatCompletionRequest request) {
        SseEmitter emitter = new SseEmitter();

        proxyService.chatCompletionStream(request)
                .subscribe(
                        response -> {
                            try {
                                emitter.send(response);
                            } catch (Exception e) {
                                logger.error("ProxyController: Error sending stream event", e);
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        return emitter;
    }

    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object listModels() {
        logger.debug("ProxyController: Received list models request");
        return proxyService.listModels();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "prompt-shield-api-proxy");
    }
}
