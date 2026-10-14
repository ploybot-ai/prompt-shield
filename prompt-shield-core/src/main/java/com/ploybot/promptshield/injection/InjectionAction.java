package com.ploybot.promptshield.injection;

/** What an integration does with a text the detector flags. */
public enum InjectionAction {

    /** Log and report the verdict; the text reaches the model unchanged. */
    MONITOR,

    /** Reject the whole request with a {@link PromptInjectionException}. */
    BLOCK,

    /**
     * Replace the flagged text with a short notice and continue. Meant for tool results
     * and documents: the agent keeps working without ever reading the payload. Applied to
     * user input it behaves like {@link #BLOCK}.
     */
    QUARANTINE
}
