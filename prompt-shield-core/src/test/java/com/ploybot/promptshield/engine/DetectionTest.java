package com.ploybot.promptshield.engine;

import com.ploybot.promptshield.exception.ObfuscationException;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.ner.DetectedEntity;
import com.ploybot.promptshield.ner.regex.RegexEntityDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionTest {

    private final ObfuscationScope scope = new ObfuscationEngine().forScope("conv-1");

    @Test
    void laterPatternsNeverCorruptTagsProducedByEarlierTypes() {
        // README example: a custom type without word boundaries used to match the
        // digits inside the hex id of an EMAIL tag (~12% of tags)
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHmacSecret("fixed-secret");
        config.addCustomType("CODIGO_POSTAL", "\\d{5}");
        ObfuscationEngine engine = new ObfuscationEngine(config);

        for (int i = 0; i < 1_000; i++) {
            ObfuscationScope s = engine.forScope("s" + i);
            String original = "mail user" + i + "@x.com";
            String obfuscated = s.obfuscate(original);
            assertEquals(original, s.restore(obfuscated), "corrupted tag: " + obfuscated);
        }
    }

    @Test
    void tagsAlreadyPresentInTheInputAreLeftUntouched() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.addCustomType("REFERENCIA", "\\d{3}");
        ObfuscationScope s = new ObfuscationEngine(config).forScope("conv-1");

        String history = s.obfuscate("email juan@example.com");
        String again = s.obfuscate(history + " y referencia 123");

        assertTrue(again.startsWith(history), "the existing tag must be preserved verbatim");
        assertEquals("email juan@example.com y referencia 123", s.restore(again));
    }

    @Test
    void customTypesWithInvalidNamesAreRejected() {
        ObfuscationConfig lowercase = new ObfuscationConfig();
        lowercase.addCustomType("Matricula", "\\d{4}[A-Z]{3}");
        assertThrows(IllegalArgumentException.class, () -> new ObfuscationEngine(lowercase));

        ObfuscationConfig withDigits = new ObfuscationConfig();
        withDigits.addCustomType("MATRICULA2", "\\d{4}[A-Z]{3}");
        ObfuscationScope s = new ObfuscationEngine(withDigits).forScope("conv-1");
        String obfuscated = s.obfuscate("coche 1234BCD");
        assertTrue(obfuscated.contains("MATRICULA2"));
        assertEquals("coche 1234BCD", s.restore(obfuscated), "names with digits must be restorable");
    }

    @Test
    void obfuscatingOnlySomeTypes() {
        String result = scope.obfuscate("DNI 12345678Z, email a@b.com, tel 612345678", List.of("DNI", "TELEFONO"));
        assertFalse(result.contains("12345678Z"));
        assertFalse(result.contains("612345678"));
        assertTrue(result.contains("a@b.com"));
        assertThrows(ObfuscationException.class, () -> scope.obfuscate("x", List.of("NOPE")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "612345678", "612 345 678", "612 34 56 78", "612-345-678",
            "+34 612 34 56 78", "+34612345678", "0034 612345678", "91 123 45 67"})
    void spanishPhoneFormats(String phone) {
        assertDetected("Llámame al " + phone + ", gracias", phone, "TELEFONO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678Z", "12345678-Z", "12.345.678-Z", "12.345.678Z"})
    void dniFormats(String dni) {
        assertDetected("Mi DNI es " + dni + ".", dni, "DNI");
    }

    @Test
    void nieWithHyphens() {
        assertDetected("NIE X-1234567-L", "X-1234567-L", "NIE");
    }

    @Test
    void dniInsideALongerReferenceIsNotDetected() {
        String text = "Pedido REF-9912345678Z";
        assertEquals(text, scope.obfuscate(text));
    }

    @Test
    void groupedSpanishIban() {
        assertDetected("IBAN ES91 2100 0418 4502 0005 1332 para el pago", "ES91 2100 0418 4502 0005 1332", "N_CUENTA");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE89370400440532013000", "DE89 3704 0044 0532 0130 00", "GB82 WEST 1234 5698 7654 32"})
    void foreignIbans(String iban) {
        assertDetected("Transferir a " + iban + " hoy", iban, "IBAN");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sk-proj-abc123def456ghi789jkl012mno",
            "sk-svcacct-abc123def456ghi789jkl012mno",
            "sk-abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL"})
    void openAiKeyVariants(String key) {
        assertDetected("key " + key, key, "OPENAI_API_KEY");
    }

    @Test
    void anthropicKeysAreNotReportedAsOpenAi() {
        assertDetected("key sk-ant-api03-abc123def456ghi789jkl012", "sk-ant-api03-abc123def456ghi789jkl012", "ANTHROPIC_API_KEY");
    }

    @Test
    void hyphenatedWordsAreNotOpenAiKeys() {
        String text = "the task-some-very-long-hyphenated-identifier and sk-some-very-long-hyphenated-name";
        assertEquals(text, scope.obfuscate(text));
    }

    @Test
    void regexDetectorAppliesValidatorsServiceKeyToggleAndPriority() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setServiceKeysEnabled(false);
        RegexEntityDetector detector = new RegexEntityDetector(config);

        List<DetectedEntity> entities = detector.detect(
                "DNI 12345678Z, falso 12345678A, key sk-proj-abc123def456ghi789jkl012mno, tel 612345678");

        assertEquals(List.of("DNI", "TELEFONO"), entities.stream().map(DetectedEntity::getType).toList());
        assertEquals("12345678Z", entities.get(0).getValue());
    }

    private void assertDetected(String text, String value, String type) {
        String obfuscated = scope.obfuscate(text);
        assertFalse(obfuscated.contains(value), () -> type + " not detected in: " + obfuscated);
        List<ObfuscationTag> tags = scope.extractTags(obfuscated);
        assertEquals(1, tags.size(), () -> "unexpected tags in: " + obfuscated);
        assertEquals(type, tags.get(0).getType());
        assertEquals(value, tags.get(0).getOriginalValue());
        assertEquals(text, scope.restore(obfuscated));
    }
}
