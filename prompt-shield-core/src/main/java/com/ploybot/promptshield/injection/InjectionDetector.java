package com.ploybot.promptshield.injection;

/**
 * Scores text for prompt-injection attempts. Implementations must be thread safe.
 *
 * <p>{@link HeuristicInjectionDetector} is the built-in, dependency-free
 * implementation. A model-based classifier (Llama Prompt Guard, a fine-tuned DeBERTa
 * served by ONNX Runtime, a hosted moderation API...) can be plugged in by implementing
 * this interface and combined with the heuristics through
 * {@link CompositeInjectionDetector}.
 */
@FunctionalInterface
public interface InjectionDetector {

    InjectionVerdict analyze(String text, ContentSource source);

    default InjectionVerdict analyze(String text) {
        return analyze(text, ContentSource.USER);
    }
}
