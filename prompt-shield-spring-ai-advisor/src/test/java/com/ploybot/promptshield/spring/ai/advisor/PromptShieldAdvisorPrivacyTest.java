package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptShieldAdvisorPrivacyTest {

    private static final Pattern TAG = Pattern.compile("~REDACTED:[A-Z_0-9]+#[a-f0-9]+~");

    private InMemoryStorageService storage;
    private PromptShieldAdvisor advisor;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorageService();
        advisor = new PromptShieldAdvisor(new ObfuscationEngine(new ObfuscationConfig(), storage));
    }

    @Test
    void toolResultsAndSystemPromptAreObfuscated() {
        ToolResponseMessage toolResult = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "getCustomer",
                        "{\"dni\":\"12345678Z\",\"telefono\":\"612345678\",\"total\":12000,\"nombre\":\"Juan\"}")))
                .build();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(
                        new SystemMessage("Atiendes a juan@example.com"),
                        new UserMessage("¿Cuál es mi DNI?"),
                        toolResult)))
                .context(ChatMemory.CONVERSATION_ID, "conv-1")
                .build();

        AtomicReference<Prompt> sent = new AtomicReference<>();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenAnswer(invocation -> {
            sent.set(invocation.<ChatClientRequest>getArgument(0).prompt());
            return textResponse("OK");
        });

        advisor.adviseCall(request, chain);

        List<Message> messages = sent.get().getInstructions();
        String system = messages.get(0).getText();
        assertFalse(system.contains("juan@example.com"), "user data in the system prompt must be obfuscated");
        assertTrue(system.contains("e5a3b2"), "the instructions' example tag must be kept");

        String toolData = ((ToolResponseMessage) messages.get(2)).getResponses().get(0).responseData();
        assertFalse(toolData.contains("12345678Z"), "tool results must not reach the model in clear");
        assertFalse(toolData.contains("612345678"));
        assertTrue(toolData.contains("\"nombre\":\"Juan\""), "tool results must stay valid JSON");
        assertTrue(toolData.contains("\"total\":12000"), "numeric amounts must not become tags");
    }

    @Test
    void systemAndToolObfuscationCanBeDisabled() {
        PromptShieldAdvisor lenient = new PromptShieldAdvisor(
                new ObfuscationEngine(new ObfuscationConfig(), storage), 0, true, true, null, false, false);
        ToolResponseMessage toolResult = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse("call_1", "getCustomer", "DNI 12345678Z")))
                .build();
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new SystemMessage("Atiendes a juan@example.com"),
                        new UserMessage("Mi email es juan@example.com"), toolResult)))
                .context(ChatMemory.CONVERSATION_ID, "conv-1")
                .build();
        AtomicReference<Prompt> sent = new AtomicReference<>();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenAnswer(invocation -> {
            sent.set(invocation.<ChatClientRequest>getArgument(0).prompt());
            return textResponse("OK");
        });

        lenient.adviseCall(request, chain);

        List<Message> messages = sent.get().getInstructions();
        assertTrue(messages.get(0).getText().startsWith("Atiendes a juan@example.com"));
        assertFalse(messages.get(1).getText().contains("juan@example.com"), "user messages are always obfuscated");
        assertEquals("DNI 12345678Z", ((ToolResponseMessage) messages.get(2)).getResponses().get(0).responseData());
    }

    @Test
    void toolLoopRestoresArgumentsForTheToolAndHidesItsResultFromTheModel() {
        List<Prompt> prompts = new ArrayList<>();
        ChatModel model = mock(ChatModel.class);
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().build();
        when(model.getOptions()).thenReturn(options);
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            prompts.add(prompt);
            if (prompts.size() == 1) {
                String emailTag = firstTag(prompt.getUserMessage().getText());
                AssistantMessage toolCall = AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call_1", "function", "lookupCustomer",
                                "{\"email\":\"" + emailTag + "\"}")))
                        .build();
                return new ChatResponse(List.of(new Generation(toolCall)));
            }
            ToolResponseMessage result = (ToolResponseMessage) prompt.getInstructions().get(prompt.getInstructions().size() - 1);
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("Datos del cliente: " + result.getResponses().get(0).responseData()))));
        });
        CustomerTools tools = new CustomerTools();

        String content = ChatClient.builder(model).defaultAdvisors(advisor).build()
                .prompt()
                .user("Busca el cliente juan@example.com")
                .tools(tools)
                .call()
                .content();

        assertEquals("juan@example.com", tools.receivedEmail.get(), "the tool must receive the real value");
        assertEquals(2, prompts.size());
        String secondPrompt = prompts.get(1).getInstructions().toString();
        assertFalse(secondPrompt.contains("12345678Z"), "the tool result must be obfuscated before the model sees it");
        assertFalse(secondPrompt.contains("juan@example.com"));
        assertTrue(content.contains("12345678Z"), "the final answer must be restored");
        assertEquals(0, storage.size(), "per-request mappings must be released");
    }

    @Test
    void perRequestScopeIsReleasedAfterACall() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenAnswer(invocation -> {
            String text = invocation.<ChatClientRequest>getArgument(0).prompt().getUserMessage().getText();
            return textResponse("Recibido " + firstTag(text));
        });

        ChatClientResponse response = advisor.adviseCall(userRequest("Mi DNI es 12345678Z", null), chain);

        assertEquals("Recibido 12345678Z", response.chatResponse().getResult().getOutput().getText());
        assertEquals(0, storage.size());
    }

    @Test
    void perRequestScopeIsReleasedWhenAStreamCompletes() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenAnswer(invocation -> {
            String tag = firstTag(invocation.<ChatClientRequest>getArgument(0).prompt().getUserMessage().getText());
            return Flux.just(textResponse("Recibido " + tag.substring(0, 5)), textResponse(tag.substring(5)));
        });

        StepVerifier.create(advisor.adviseStream(userRequest("Mi DNI es 12345678Z", null), chain)
                        .map(r -> r.chatResponse().getResult().getOutput().getText())
                        .reduce(String::concat))
                .expectNext("Recibido 12345678Z")
                .verifyComplete();
        assertEquals(0, storage.size());
    }

    @Test
    void conversationScopesKeepTheirMappings() {
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(textResponse("OK"));

        advisor.adviseCall(userRequest("Mi DNI es 12345678Z", "conv-1"), chain);

        assertEquals(1, storage.size(), "mappings of a named scope are kept for later turns");
    }

    private static ChatClientRequest userRequest(String text, String conversationId) {
        ChatClientRequest.Builder builder = ChatClientRequest.builder().prompt(new Prompt(new UserMessage(text)));
        if (conversationId != null) {
            builder.context(ChatMemory.CONVERSATION_ID, conversationId);
        }
        return builder.build();
    }

    private static ChatClientResponse textResponse(String text) {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))))
                .build();
    }

    private static String firstTag(String text) {
        Matcher matcher = TAG.matcher(text);
        assertTrue(matcher.find(), () -> "no tag in: " + text);
        return matcher.group();
    }

    static class CustomerTools {
        final AtomicReference<String> receivedEmail = new AtomicReference<>();

        @Tool(description = "Looks up a customer by email")
        String lookupCustomer(String email) {
            receivedEmail.set(email);
            return "{\"nombre\":\"Juan\",\"dni\":\"12345678Z\",\"iban\":\"ES9121000418450200051332\"}";
        }
    }
}
