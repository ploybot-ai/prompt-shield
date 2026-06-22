package com.ploybot.promptshield.proxy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        Double temperature,
        Double topP,
        Boolean stream,
        List<Map<String, Object>> tools,
        @JsonProperty("tool_choice") Object toolChoice
) {
    public ChatCompletionRequest(String model, List<ChatMessage> messages) {
        this(model, messages, null, null, null, null, null, null);
    }
}
