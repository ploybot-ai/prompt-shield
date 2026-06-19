package com.ploybot.promptshield.example;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationTag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/obfuscador")
public class ObfuscadorController {

    private final ObfuscationEngine engine;

    public ObfuscadorController(ObfuscationEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/ofuscar")
    public ObfuscacionResponse ofuscar(@RequestBody ObfuscacionRequest request) {
        String ofuscado = engine.ofuscar(request.text());
        List<ObfuscationTag> tags = engine.extractTags(ofuscado);
        return new ObfuscacionResponse(ofuscado, tags);
    }

    @PostMapping("/restaurar")
    public RestauracionResponse restaurar(@RequestBody RestauracionRequest request) {
        String restaurado = engine.restaurar(request.text());
        return new RestauracionResponse(restaurado);
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    public record ObfuscacionRequest(String text) {}
    public record ObfuscacionResponse(String text, List<ObfuscationTag> tags) {}
    public record RestauracionRequest(String text) {}
    public record RestauracionResponse(String text) {}
}
