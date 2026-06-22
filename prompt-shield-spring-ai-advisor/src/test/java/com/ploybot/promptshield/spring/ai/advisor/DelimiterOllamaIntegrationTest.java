package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that evaluates different delimiter formats
 * using a local Ollama instance with qwen3.5:0.8b-mlx.
 *
 * Uses direct HTTP API to handle thinking models that return
 * content in the 'thinking' field instead of 'content'.
 *
 * Tests use exact match: response must contain the FULL placeholder literal.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DelimiterOllamaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DelimiterOllamaIntegrationTest.class);

    private static final String MODEL_NAME = "qwen3.5:0.8b-mlx";
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";

    private HttpClient httpClient;

    @BeforeAll
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        log.info("Using local Ollama at {} with model {}", OLLAMA_BASE_URL, MODEL_NAME);
    }

    private record TestScenario(String name, String response, String expectedPlaceholder) {}

    private record DelimiterTestResult(
            String format,
            int score,
            boolean preservedInPlain,
            boolean preservedInHtml,
            boolean preservedInXml,
            boolean preservedInJson,
            String plainResponse,
            String htmlResponse,
            String xmlResp,
            String jsonResponse,
            String expectedPlaceholder
    ) {}

    private String buildSystemPrompt(ObfuscationEngine engine) {
        return engine.generateSystemPrompt("en");
    }

    /**
     * Call Ollama API directly and extract response from content OR thinking field.
     */
    private String callOllama(String systemPrompt, String userMessage) {
        try {
            String json = """
                    {
                        "model": "%s",
                        "messages": [
                            {"role": "system", "content": "%s"},
                            {"role": "user", "content": "%s"}
                        ],
                        "stream": false,
                        "options": {
                            "temperature": 0.0,
                            "num_predict": 200
                        }
                    }
                    """.formatted(
                    MODEL_NAME,
                    escapeJson(systemPrompt),
                    escapeJson(userMessage));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_BASE_URL + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Try content field first
            String content = extractJsonField(body, "content");
            if (content != null && !content.isEmpty()) {
                return content;
            }

            // Fall back to thinking field
            String thinking = extractJsonField(body, "thinking");
            if (thinking != null && !thinking.isEmpty()) {
                return thinking;
            }

            return "";
        } catch (Exception e) {
            log.error("Error calling Ollama: {}", e.getMessage());
            return "";
        }
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\"";
        int start = json.indexOf(key);
        if (start < 0) return null;

        int colon = json.indexOf(":", start + key.length());
        if (colon < 0) return null;

        int quoteStart = json.indexOf("\"", colon + 1);
        if (quoteStart < 0) return null;

        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        while (quoteEnd > 0 && json.charAt(quoteEnd - 1) == '\\') {
            quoteEnd = json.indexOf("\"", quoteEnd + 1);
        }

        return json.substring(quoteStart + 1, quoteEnd).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private TestScenario testPlain(ObfuscationEngine engine) {
        String obfuscated = engine.ofuscar("Email: juan@example.com");
        String placeholder = extractPlaceholder(obfuscated, engine);

        String response = callOllama(
                buildSystemPrompt(engine),
                obfuscated + "\n\nWhat is the email address? Reply only with the exact placeholder.");

        return new TestScenario("Plain", response, placeholder);
    }

    private TestScenario testHtml(ObfuscationEngine engine) {
        String obfuscated = engine.ofuscar("Email: juan@example.com");
        String placeholder = extractPlaceholder(obfuscated, engine);

        String htmlPrompt = """
                <html>
                <body>
                <p>User data: %s</p>
                <div class="email-section">
                    <span>Please process the email above.</span>
                </div>
                </body>
                </html>
                
                Based on the HTML above, what is the email? Reply only with the exact placeholder.
                """.formatted(obfuscated);

        String response = callOllama(buildSystemPrompt(engine), htmlPrompt);
        return new TestScenario("HTML", response, placeholder);
    }

    private TestScenario testXml(ObfuscationEngine engine) {
        String obfuscated = engine.ofuscar("Email: juan@example.com");
        String placeholder = extractPlaceholder(obfuscated, engine);

        String xmlPrompt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <user>
                    <name>Juan</name>
                    <contact>%s</contact>
                    <action>process</action>
                </user>
                
                From the XML above, extract the contact placeholder. Reply only with the exact placeholder.
                """.formatted(obfuscated);

        String response = callOllama(buildSystemPrompt(engine), xmlPrompt);
        return new TestScenario("XML", response, placeholder);
    }

    private TestScenario testJson(ObfuscationEngine engine) {
        String obfuscated = engine.ofuscar("Email: juan@example.com");
        String placeholder = extractPlaceholder(obfuscated, engine);

        String jsonPrompt = """
                {
                    "user": {
                        "name": "Juan",
                        "email": "%s",
                        "action": "send_welcome"
                    },
                    "metadata": {
                        "source": "registration_form",
                        "timestamp": "2025-01-15T10:30:00Z"
                    }
                }
                
                From the JSON above, what is the email field value? Reply only with the exact placeholder.
                """.formatted(obfuscated);

        String response = callOllama(buildSystemPrompt(engine), jsonPrompt);
        return new TestScenario("JSON", response, placeholder);
    }

    private String extractPlaceholder(String obfuscated, ObfuscationEngine engine) {
        String tagOpen = engine.getConfig().getTagOpen();
        String tagClose = engine.getConfig().getTagClose();
        int start = obfuscated.indexOf(tagOpen);
        if (start < 0) return obfuscated;

        int searchFrom = start + tagOpen.length();
        int end = obfuscated.indexOf(tagClose, searchFrom);
        if (end > start) {
            return obfuscated.substring(start, end + tagClose.length());
        }
        return obfuscated;
    }

    private boolean exactMatch(String response, String expectedPlaceholder) {
        if (response == null || expectedPlaceholder == null) return false;
        return response.contains(expectedPlaceholder);
    }

    private DelimiterTestResult testDelimiter(String open, String close) {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setTagOpen(open);
        config.setTagClose(close);
        ObfuscationEngine engine = new ObfuscationEngine(config, new InMemoryStorageService());

        log.info("Testing delimiter {}...{}", open, close);

        TestScenario plain, html, xml, json;
        try {
            plain = testPlain(engine);
            html = testHtml(engine);
            xml = testXml(engine);
            json = testJson(engine);
        } catch (Exception e) {
            log.error("Error testing delimiter {}...{}: {}", open, close, e.getMessage());
            return new DelimiterTestResult(open + close, 0, false, false, false, false, "ERROR", "ERROR", "ERROR", "ERROR", "");
        }

        String placeholder = plain.expectedPlaceholder();
        log.info("  Expected: {}", placeholder);
        log.info("  Plain: {}", plain.response());
        log.info("  HTML:  {}", html.response());
        log.info("  XML:   {}", xml.response());
        log.info("  JSON:  {}", json.response());

        boolean pPlain = exactMatch(plain.response(), placeholder);
        boolean pHtml = exactMatch(html.response(), placeholder);
        boolean pXml = exactMatch(xml.response(), placeholder);
        boolean pJson = exactMatch(json.response(), placeholder);

        int score = 0;
        if (pPlain) score += 25;
        if (pHtml) score += 25;
        if (pXml) score += 25;
        if (pJson) score += 25;

        return new DelimiterTestResult(open + close, score, pPlain, pHtml, pXml, pJson,
                plain.response(), html.response(), xml.response(), json.response(), placeholder);
    }

    @Test
    void evaluateAllDelimiters() {

        String[][] delimiters = {
            {"{{", "}}"},
            {"<<", ">>"},
            {"[", "]"},
            {"[[", "]]"},
            {"«", "»"},
            {"⟦", "⟧"},
            {"__", "__"},
            {"@@", "@@"},
            {"##", "##"},
            {"|", "|"},
            {"||", "||"},
            {"~", "~"},
            {"~~", "~~"},
            {"^", "^"},
            {"^^", "^^"},
            {"`", "`"},
            {"``", "``"},
            {"*", "*"},
            {"**", "**"},
            {"$", "$"},
            {"$$", "$$"},
            {"%", "%"},
            {"%%", "%%"}
        };

        Map<String, DelimiterTestResult> results = new LinkedHashMap<>();

        for (String[] delimiter : delimiters) {
            DelimiterTestResult result = testDelimiter(delimiter[0], delimiter[1]);
            results.put(delimiter[0] + delimiter[1], result);
        }

        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║        OLLAMA DELIMITER EVALUATION RESULTS (EXACT MATCH)        ║");
        System.out.println("║        Model: " + MODEL_NAME + " ".repeat(Math.max(0, 44 - MODEL_NAME.length())) + "║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Scoring: +25 per context (Plain, HTML, XML, JSON) = 100 max    ║");
        System.out.println("║ Method: response must contain EXACT placeholder literal         ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣\n");

        System.out.printf("%-10s │ %-6s │ %-6s │ %-5s │ %-5s │ %-5s │ %s%n",
                "Format", "Plain", "HTML", "XML", "JSON", "Score", "Verdict");
        System.out.println("─".repeat(75));

        for (DelimiterTestResult r : results.values()) {
            String verdict;
            if (r.score() == 100) verdict = "★★★ PERFECT";
            else if (r.score() >= 75) verdict = "★★  GOOD";
            else if (r.score() >= 50) verdict = "★   MIXED";
            else if (r.score() > 0) verdict = "    POOR";
            else verdict = "    FAIL";

            System.out.printf("%-10s │ %-6s │ %-6s │ %-5s │ %-5s │  %3d  │ %s%n",
                    r.format(),
                    boolIcon(r.preservedInPlain()),
                    boolIcon(r.preservedInHtml()),
                    boolIcon(r.preservedInXml()),
                    boolIcon(r.preservedInJson()),
                    r.score(),
                    verdict);
        }

        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         FINAL RANKING                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣\n");

        results.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().score(), a.getValue().score()))
                .forEachOrdered(entry -> {
                    DelimiterTestResult r = entry.getValue();
                    String bar = "█".repeat(r.score() / 5) + "░".repeat(20 - r.score() / 5);
                    System.out.printf("  %s  %s %3d/100%n", bar, r.format(), r.score());
                });

        System.out.println("\n╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║                      DETAILED RESPONSES                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣\n");

        results.forEach((format, r) -> {
            System.out.println("┌─ " + format + " ─  expected: " + r.expectedPlaceholder());
            System.out.println("│ Plain: " + truncate(r.plainResponse(), 120));
            System.out.println("│ HTML:  " + truncate(r.htmlResponse(), 120));
            System.out.println("│ XML:   " + truncate(r.xmlResp(), 120));
            System.out.println("│ JSON:  " + truncate(r.jsonResponse(), 120));
            System.out.println("└──────");
            System.out.println();
        });

        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        assertTrue(true, "Evaluation complete");
    }

    private String boolIcon(boolean value) {
        return value ? "  ✓  " : "  ✗  ";
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
