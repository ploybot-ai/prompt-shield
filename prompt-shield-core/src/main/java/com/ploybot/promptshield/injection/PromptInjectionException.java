package com.ploybot.promptshield.injection;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a request is rejected because a text was flagged and the configured
 * action is {@link InjectionAction#BLOCK}. The message only names rules and sources,
 * never the flagged text, so it is safe to log.
 */
public class PromptInjectionException extends RuntimeException {

    private final transient List<InjectionVerdict> verdicts;

    public PromptInjectionException(List<InjectionVerdict> verdicts) {
        super("Possible prompt injection blocked: " + verdicts.stream()
                .map(InjectionVerdict::summary)
                .collect(Collectors.joining("; ")));
        this.verdicts = List.copyOf(verdicts);
    }

    public List<InjectionVerdict> getVerdicts() {
        return verdicts;
    }
}
