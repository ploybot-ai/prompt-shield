package com.ploybot.promptshield.engine;

import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscationEngineTest {

    private ObfuscationEngine engine;
    private InMemoryStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new InMemoryStorageService();
        ObfuscationConfig config = new ObfuscationConfig();
        engine = new ObfuscationEngine(config, storageService);
    }

    @Test
    void ofuscarDNI() {
        String result = engine.ofuscar("Mi DNI es 12345678Z");
        assertNotNull(result);
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("12345678Z"));
    }

    @Test
    void ofuscarNIE() {
        String result = engine.ofuscar("Mi NIE es X1234567A");
        assertNotNull(result);
        assertTrue(engine.containsTags(result));
    }

    @Test
    void ofuscarEmail() {
        String result = engine.ofuscar("Mi email es user@email.com");
        assertNotNull(result);
        assertTrue(engine.containsTags(result));
    }

    @Test
    void ofuscarTelefono() {
        String result = engine.ofuscar("Mi teléfono es 612345678");
        assertNotNull(result);
        assertTrue(engine.containsTags(result));
    }

    @Test
    void restaurarValor() {
        String ofuscado = engine.ofuscar("DNI: 12345678Z");
        String restaurado = engine.restaurar(ofuscado);
        assertEquals("DNI: 12345678Z", restaurado);
    }

    @Test
    void restaurarMultiplesValores() {
        String ofuscado = engine.ofuscar("DNI: 12345678Z, Tel: 612345678");
        String restaurado = engine.restaurar(ofuscado);
        assertEquals("DNI: 12345678Z, Tel: 612345678", restaurado);
    }

    @Test
    void containsTags() {
        String ofuscado = engine.ofuscar("DNI: 12345678Z");
        assertTrue(engine.containsTags(ofuscado));
        assertFalse(engine.containsTags("No hay datos sensibles"));
    }

    @Test
    void extractTags() {
        String ofuscado = engine.ofuscar("DNI: 12345678Z, Tel: 612345678");
        List<ObfuscationTag> tags = engine.extractTags(ofuscado);
        assertFalse(tags.isEmpty());
        assertEquals(2, tags.size());
    }

    @Test
    void textoVacio() {
        assertEquals("", engine.ofuscar(""));
        assertEquals("", engine.restaurar(""));
        assertNull(engine.ofuscar(null));
        assertNull(engine.restaurar(null));
    }

    @Test
    void clearStorage() {
        engine.ofuscar("DNI: 12345678Z");
        assertFalse(storageService.size() == 0);
        engine.clearStorage();
        assertEquals(0, storageService.size());
    }

    @Test
    void ofuscarTipoEspecifico() {
        String result = engine.ofuscar("DNI: 12345678Z, Tel: 612345678", "DNI");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertEquals(1, tags.size());
        assertEquals("DNI", tags.get(0).getType());
    }

    @Test
    void ofuscarMultiplesTipos() {
        String result = engine.ofuscar("DNI: 12345678Z, Email: test@example.com");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("DNI")));
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("EMAIL")));
    }

    @Test
    void hashConsistente() {
        String ofuscado1 = engine.ofuscar("DNI: 12345678Z");
        engine.clearStorage();
        String ofuscado2 = engine.ofuscar("DNI: 12345678Z");
        assertEquals(ofuscado1, ofuscado2);
    }

    @Test
    void valoresOriginalesAlmacenados() {
        String ofuscado = engine.ofuscar("DNI: 12345678Z");
        List<ObfuscationTag> tags = engine.extractTags(ofuscado);
        assertFalse(tags.isEmpty());
        assertEquals("12345678Z", tags.get(0).getOriginalValue());
    }

    @Test
    void ofuscarObjeto() {
        Persona persona = new Persona("Juan García", "12345678Z", "612345678", "juan@email.com");
        String ofuscado = engine.ofuscarObjeto(persona);

        assertNotNull(ofuscado);
        assertTrue(engine.containsTags(ofuscado));
        assertFalse(ofuscado.contains("12345678Z"));
        assertFalse(ofuscado.contains("612345678"));
        assertFalse(ofuscado.contains("juan@email.com"));
    }

    @Test
    void restaurarObjeto() {
        Persona persona = new Persona("Juan García", "12345678Z", "612345678", "juan@email.com");
        String ofuscado = engine.ofuscarObjeto(persona);
        Persona restaurado = engine.restaurarObjeto(ofuscado, Persona.class);

        assertNotNull(restaurado);
        assertEquals("Juan García", restaurado.getNombre());
        assertEquals("12345678Z", restaurado.getDni());
        assertEquals("612345678", restaurado.getTelefono());
        assertEquals("juan@email.com", restaurado.getEmail());
    }

    @Test
    void ofuscarObjetoJson() {
        String json = "{\"nombre\":\"Juan\",\"dni\":\"12345678Z\",\"email\":\"juan@email.com\"}";
        String ofuscado = engine.ofuscarObjetoJson(json);

        assertNotNull(ofuscado);
        assertTrue(engine.containsTags(ofuscado));
        assertFalse(ofuscado.contains("12345678Z"));
        assertFalse(ofuscado.contains("juan@email.com"));
    }

    @Test
    void restaurarObjetoJson() {
        String json = "{\"nombre\":\"Juan\",\"dni\":\"12345678Z\",\"email\":\"juan@email.com\"}";
        String ofuscado = engine.ofuscarObjetoJson(json);
        String restaurado = engine.restaurarObjetoJson(ofuscado);

        assertNotNull(restaurado);
        assertTrue(restaurado.contains("12345678Z"));
        assertTrue(restaurado.contains("juan@email.com"));
    }

    @Test
    void ofuscarObjetoConArray() {
        String json = "{\"personas\":[{\"dni\":\"12345678Z\"},{\"dni\":\"87654321A\"}]}";
        String ofuscado = engine.ofuscarObjetoJson(json);

        assertNotNull(ofuscado);
        assertTrue(engine.containsTags(ofuscado));
        assertFalse(ofuscado.contains("12345678Z"));
        assertFalse(ofuscado.contains("87654321A"));
    }

    @Test
    void restaurarObjetoConArray() {
        String json = "{\"personas\":[{\"dni\":\"12345678Z\"},{\"dni\":\"87654321A\"}]}";
        String ofuscado = engine.ofuscarObjetoJson(json);
        String restaurado = engine.restaurarObjetoJson(ofuscado);

        assertNotNull(restaurado);
        assertTrue(restaurado.contains("12345678Z"));
        assertTrue(restaurado.contains("87654321A"));
    }

    @Test
    void prefijoConfigurable() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setRedactedPrefix("OBFUSCADO");
        ObfuscationEngine customEngine = new ObfuscationEngine(config, new InMemoryStorageService());

        String result = customEngine.ofuscar("DNI: 12345678Z");
        assertTrue(customEngine.containsTags(result));
        List<ObfuscationTag> tags = customEngine.extractTags(result);
        assertFalse(tags.isEmpty());
    }

    @Test
    void separadorConfigurable() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setTagSeparator("_");
        ObfuscationEngine customEngine = new ObfuscationEngine(config, new InMemoryStorageService());

        String result = customEngine.ofuscar("DNI: 12345678Z");
        assertTrue(customEngine.containsTags(result));
        String restored = customEngine.restaurar(result);
        assertEquals("DNI: 12345678Z", restored);
    }

    @Test
    void prefijoYSeparadorConfigurables() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setRedactedPrefix("OCULTO");
        config.setTagSeparator("_");
        ObfuscationEngine customEngine = new ObfuscationEngine(config, new InMemoryStorageService());

        String result = customEngine.ofuscar("DNI: 12345678Z");
        assertTrue(customEngine.containsTags(result));
        String restored = customEngine.restaurar(result);
        assertEquals("DNI: 12345678Z", restored);
    }

    public static class Persona {
        private String nombre;
        private String dni;
        private String telefono;
        private String email;

        public Persona() {}

        public Persona(String nombre, String dni, String telefono, String email) {
            this.nombre = nombre;
            this.dni = dni;
            this.telefono = telefono;
            this.email = email;
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getDni() { return dni; }
        public void setDni(String dni) { this.dni = dni; }
        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @Test
    void ofuscarOpenAIKey() {
        String result = engine.ofuscar("API key: sk-proj-abc123def456ghi789jkl012mno");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("sk-proj-abc123def456ghi789jkl012mno"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("OPENAI_API_KEY")));
    }

    @Test
    void ofuscarAnthropicKey() {
        String result = engine.ofuscar("Key: sk-ant-api03-abc123def456ghi789jkl012");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("sk-ant-api03-abc123def456ghi789jkl012"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("ANTHROPIC_API_KEY")));
    }

    @Test
    void ofuscarGoogleAIKey() {
        String result = engine.ofuscar("Google key: AIzaSyA1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("GOOGLE_AI_KEY")));
    }

    @Test
    void ofuscarHuggingFaceToken() {
        String result = engine.ofuscar("HF token: hf_abc123def456ghi789jkl012mno34pqrst");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("hf_abc123def456ghi789jkl012mno34"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("HUGGINGFACE_TOKEN")));
    }

    @Test
    void ofuscarAWSAccessKey() {
        String result = engine.ofuscar("AWS key: AKIAIOSFODNN7EXAMPLE");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("AKIAIOSFODNN7EXAMPLE"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("AWS_ACCESS_KEY")));
    }

    @Test
    void ofuscarAzureStorageKey() {
        String key = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCdEfGhIjKlMnOpQrStUvWxYz==";
        String result = engine.ofuscar("Azure key: " + key);
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("AZURE_STORAGE_KEY")));
    }

    @Test
    void ofuscarDigitalOceanToken() {
        String result = engine.ofuscar("DO token: dop_v1_abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("DIGITALOCEAN_TOKEN")));
    }

    @Test
    void ofuscarGitHubToken() {
        String result = engine.ofuscar("GH token: ghp_ABCDEFghijklMNOPqrstuvwxYZ0123456789");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("ghp_ABCDEFghijklMNOPqrstuvwxYZ0123456789"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("GITHUB_TOKEN")));
    }

    @Test
    void ofuscarGitLabToken() {
        String result = engine.ofuscar("GL token: glpat-abc123def456ghi789jkl");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("glpat-abc123def456ghi789"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("GITLAB_TOKEN")));
    }

    @Test
    void ofuscarNpmToken() {
        String result = engine.ofuscar("npm token: npm_ABCDEFghijklMNOPqrstuvwxYZ0123456789");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("NPM_TOKEN")));
    }

    @Test
    void ofuscarPypiToken() {
        String result = engine.ofuscar("PyPI token: pypi-AgEIcHlwaS5vcmcCJGY4NjMxMjctNjBhNi00NzEwLWE0ZTktMGFiNDE2NzI2MzA4Cmc6AQQ");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("PYPI_TOKEN")));
    }

    @Test
    void ofuscarSlackToken() {
        String result = engine.ofuscar("Slack token: xoxb-123456789012-1234567890123-abcDefGhiJklMnoPqrStu12");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("SLACK_TOKEN")));
    }

    @Test
    void ofuscarTwilioApiKey() {
        String result = engine.ofuscar("Twilio key: SKabcdef0123456789abcdef0123456789");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("SKabcdef0123456789abcdef0123456789"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("TWILIO_API_KEY")));
    }

    @Test
    void ofuscarSendGridKey() {
        String result = engine.ofuscar("SG key: SG.abcdef1234567890abcdef.XYZWVUtsrqponmlkjihgfedcba9876543210WVUTSRa");
        assertTrue(engine.containsTags(result));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("SENDGRID_KEY")));
    }

    @Test
    void ofuscarMailgunKey() {
        String result = engine.ofuscar("Mailgun key: key-abcdef0123456789abcdef0123456789");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("key-abcdef0123456789abcdef0123456789"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("MAILGUN_API_KEY")));
    }

    @Test
    void ofuscarStripeKey() {
        String result = engine.ofuscar("Stripe key: sk_live_abcdef0123456789abcdef0123456789");
        assertTrue(engine.containsTags(result));
        assertFalse(result.contains("sk_live_abcdef0123456789abcdef0123456789"));
        List<ObfuscationTag> tags = engine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("STRIPE_KEY")));
    }

    @Test
    void restaurarServiceKeys() {
        String text = "OpenAI: sk-proj-abc123def456ghi789jkl012mno, GitHub: ghp_ABCDEFghijklMNOPqrstuvwxYZ0123456789";
        String ofuscado = engine.ofuscar(text);
        String restaurado = engine.restaurar(ofuscado);
        assertTrue(restaurado.contains("sk-proj-abc123def456ghi789jkl012mno"));
        assertTrue(restaurado.contains("ghp_ABCDEFghijklMNOPqrstuvwxYZ0123456789"));
    }

    @Test
    void serviceKeysNoFalsosPositivos() {
        String normal = "Mi contraseña es miPassword123 y mi usuario es juan";
        String result = engine.ofuscar(normal);
        assertFalse(engine.containsTags(result), "Textos normales no deben ser ofuscados como service keys");
    }

    @Test
    void serviceKeysDisabled_noDetectServiceKeys() {
        ObfuscationConfig disabledConfig = new ObfuscationConfig();
        disabledConfig.setServiceKeysEnabled(false);
        ObfuscationEngine disabledEngine = new ObfuscationEngine(disabledConfig, new InMemoryStorageService());

        String result = disabledEngine.ofuscar("OpenAI: sk-proj-abc123def456ghi789jkl012mno");
        assertFalse(disabledEngine.containsTags(result), "Service keys no deben detectarse cuando serviceKeysEnabled=false");
        assertTrue(result.contains("sk-proj-abc123def456ghi789jkl012mno"), "El valor original debe permanecer intacto");
    }

    @Test
    void serviceKeysDisabled_stillDetectPII() {
        ObfuscationConfig disabledConfig = new ObfuscationConfig();
        disabledConfig.setServiceKeysEnabled(false);
        ObfuscationEngine disabledEngine = new ObfuscationEngine(disabledConfig, new InMemoryStorageService());

        String result = disabledEngine.ofuscar("DNI: 12345678Z, Email: test@example.com");
        assertTrue(disabledEngine.containsTags(result), "PII sigue detectándose con serviceKeysEnabled=false");
        List<ObfuscationTag> tags = disabledEngine.extractTags(result);
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("DNI")));
        assertTrue(tags.stream().anyMatch(t -> t.getType().equals("EMAIL")));
    }

    @Test
    void serviceKeysEnabled_detectAll() {
        assertTrue(engine.containsTags(engine.ofuscar("OpenAI: sk-proj-abc123def456ghi789jkl012mno")));
        assertTrue(engine.containsTags(engine.ofuscar("GitHub: ghp_ABCDEFghijklMNOPqrstuvwxYZ0123456789")));
    }

    @Test
    void conversationIsolation_differentConversationsCannotRestoreEachOthersData() {
        InMemoryStorageService sharedStorage = new InMemoryStorageService();

        ObfuscationConfig configA = new ObfuscationConfig();
        configA.setConversationId("conversation-A");
        ObfuscationEngine engineA = new ObfuscationEngine(configA, sharedStorage);

        ObfuscationConfig configB = new ObfuscationConfig();
        configB.setConversationId("conversation-B");
        ObfuscationEngine engineB = new ObfuscationEngine(configB, sharedStorage);

        String obfuscatedA = engineA.ofuscar("Email de Alice: alice@secret.com");
        String obfuscatedB = engineB.ofuscar("Email de Bob: bob@secret.com");

        String restoredA = engineA.restaurar(obfuscatedA);
        assertTrue(restoredA.contains("alice@secret.com"), "Engine A should restore its own data");

        String restoredB = engineB.restaurar(obfuscatedB);
        assertTrue(restoredB.contains("bob@secret.com"), "Engine B should restore its own data");

        String attemptedB = engineA.restaurar(obfuscatedB);
        assertTrue(engineA.containsTags(attemptedB),
                "Engine A should NOT restore Engine B's data - tags remain as-is");

        String attemptedA = engineB.restaurar(obfuscatedA);
        assertTrue(engineB.containsTags(attemptedA),
                "Engine B should NOT restore Engine A's data - tags remain as-is");
    }

    @Test
    void conversationIsolation_sameConversationSharesStorage() {
        InMemoryStorageService sharedStorage = new InMemoryStorageService();

        ObfuscationConfig configA = new ObfuscationConfig();
        configA.setConversationId("same-conversation");
        ObfuscationEngine engine1 = new ObfuscationEngine(configA, sharedStorage);

        ObfuscationConfig configB = new ObfuscationConfig();
        configB.setConversationId("same-conversation");
        ObfuscationEngine engine2 = new ObfuscationEngine(configB, sharedStorage);

        String obfuscated = engine1.ofuscar("Teléfono: 612345678");
        String restored = engine2.restaurar(obfuscated);

        assertTrue(restored.contains("612345678"),
                "Same conversation should be able to restore shared data");
    }
}
