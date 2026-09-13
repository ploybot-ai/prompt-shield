package com.ploybot.promptshield.engine;

import com.ploybot.promptshield.model.ObfuscationConfig;

/**
 * Builds the instructions that tell a model to keep obfuscation placeholders intact.
 */
public final class SystemPromptGenerator {

    private static final String EN_TEMPLATE = """
            IMPORTANT: This conversation may contain obfuscated sensitive data markers in the format %s. These are NOT errors or artifacts - they are intentional security placeholders protecting Personally Identifiable Information (PII).

            RULES:
            1. NEVER modify, reformat, remove, or attempt to "fix" these placeholders
            2. NEVER ask the user about them or explain what they might be
            3. Treat them as opaque identifiers - the original values will be restored after your response
            4. Reference them naturally as if they were the original data
            5. If you need to repeat the data in your response, use the EXACT placeholder as-is
            6. Do NOT try to decode, guess, or reconstruct the original values

            TOOL CALLS:
            When you invoke tools, use the placeholders exactly as they appear in the conversation.
            The system will automatically restore the original values before the tool is executed.

            Example of a tool call with obfuscated data:
            - User says: "Send email to %s"
            - You call tool: sendEmail(to="%s", subject="Hello")
            - The system restores it to: sendEmail(to="user@example.com", subject="Hello")

            Your task is to process the REQUEST, not the placeholders. The system will handle restoration of original values.
            """;

    private static final String ES_TEMPLATE = """
            IMPORTANTE: Esta conversación puede contener marcadores de datos sensibles ofuscados en el formato %s. NO son errores ni artefactos - son marcadores de seguridad intencionales que protegen Información Personal Identificable (PII).

            REGLAS:
            1. NUNCA modifiques, reformatees, elimines o intentes "corregir" estos marcadores
            2. NUNCA preguntes al usuario sobre ellos o expliques qué podrían ser
            3. Trátalos como identificadores opacos - los valores originales se restaurarán después de tu respuesta
            4. Refiérete a ellos naturalmente como si fueran los datos originales
            5. Si necesitas repetir los datos en tu respuesta, usa el marcador EXACTO tal cual
            6. NO intentes decodificar, adivinar o reconstruir los valores originales

            LLAMADAS A HERRAMIENTAS:
            Cuando invoques herramientas, usa los marcadores exactamente como aparecen en la conversación.
            El sistema restaurará automáticamente los valores originales antes de ejecutar la herramienta.

            Ejemplo de llamada a herramienta con datos ofuscados:
            - El usuario dice: "Envía email a %s"
            - Tú llamas a la herramienta: sendEmail(to="%s", subject="Hola")
            - El sistema lo restaura a: sendEmail(to="usuario@ejemplo.com", subject="Hola")

            Tu tarea es procesar la SOLICITUD, no los marcadores. El sistema se encargará de restaurar los valores originales.
            """;

    private SystemPromptGenerator() {
    }

    /**
     * @param language {@code "es"} for Spanish; anything else yields English
     */
    public static String generate(ObfuscationConfig config, String language) {
        String prefix = config.getRedactedPrefix();
        String separator = config.getTagSeparator();
        String tagOpen = config.getTagOpen();
        String tagClose = config.getTagClose();
        String tagFormat = tagOpen + prefix + ":TYPE" + separator + "HASH" + tagClose;
        String exampleEmailTag = tagOpen + prefix + ":EMAIL" + separator + "e5a3b2" + tagClose;
        String template = "es".equalsIgnoreCase(language) ? ES_TEMPLATE : EN_TEMPLATE;
        return String.format(template, tagFormat, exampleEmailTag, exampleEmailTag);
    }
}
