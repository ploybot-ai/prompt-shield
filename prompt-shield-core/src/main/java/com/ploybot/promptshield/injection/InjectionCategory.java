package com.ploybot.promptshield.injection;

/** The kind of manipulation a signal points at. */
public enum InjectionCategory {

    /** "Ignore your previous instructions", "your new task is...". */
    INSTRUCTION_OVERRIDE,

    /** Fake chat-template tokens, fake system/developer messages. */
    ROLE_SPOOFING,

    /** Attempts to extract the system prompt or hidden instructions. */
    PROMPT_LEAK,

    /** Persona jailbreaks (DAN, developer mode, "no restrictions"). */
    JAILBREAK,

    /** Sending data out: markdown images, links, "send the conversation to...". */
    EXFILTRATION,

    /** Making the agent call tools or take actions the user did not ask for. */
    TOOL_HIJACK,

    /** Third-party content speaking to the AI ("note to the assistant reading this"). */
    AI_ADDRESSED,

    /** Instructions hidden by encoding, invisible characters or lookalike letters. */
    OBFUSCATION,

    /** Prompt Shield placeholders planted in untrusted content or routed into URLs. */
    PLACEHOLDER_ABUSE
}
