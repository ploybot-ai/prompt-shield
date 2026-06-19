package com.obfuscador.engine;

import com.obfuscador.model.ObfuscationConfig;
import com.obfuscador.model.ObfuscationTag;
import com.obfuscador.storage.InMemoryStorageService;
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
        assertTrue(result.contains("<DNI_"));
        assertTrue(result.contains("12345678Z") == false);
    }

    @Test
    void ofuscarNIE() {
        String result = engine.ofuscar("Mi NIE es X1234567A");
        assertNotNull(result);
        assertTrue(result.contains("<NIE_"));
    }

    @Test
    void ofuscarEmail() {
        String result = engine.ofuscar("Mi email es user@email.com");
        assertNotNull(result);
        assertTrue(result.contains("<EMAIL_"));
    }

    @Test
    void ofuscarTelefono() {
        String result = engine.ofuscar("Mi teléfono es 612345678");
        assertNotNull(result);
        assertTrue(result.contains("<TELEFONO_"));
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
        assertTrue(result.contains("<DNI_"));
        assertFalse(result.contains("<TELEFONO_"));
    }

    @Test
    void ofuscarMultiplesTipos() {
        String result = engine.ofuscar("DNI: 12345678Z, Email: test@example.com");
        assertTrue(result.contains("<DNI_"));
        assertTrue(result.contains("<EMAIL_"));
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
        assertTrue(ofuscado.contains("<DNI_"));
        assertTrue(ofuscado.contains("<TELEFONO_"));
        assertTrue(ofuscado.contains("<EMAIL_"));
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
        assertTrue(ofuscado.contains("<DNI_"));
        assertTrue(ofuscado.contains("<EMAIL_"));
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
        assertTrue(ofuscado.contains("<DNI_"));
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
}
