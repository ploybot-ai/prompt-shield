package com.ploybot.promptshield.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingRestorerTest {

    private ObfuscationEngine engine;
    private ObfuscationScope scope;
    private String tag;

    @BeforeEach
    void setUp() {
        engine = new ObfuscationEngine();
        scope = engine.forScope("stream-conv");
        String obfuscated = scope.obfuscate("juan@example.com");
        tag = obfuscated; // the whole text is the tag
    }

    private String streamAll(List<String> chunks) {
        StreamingRestorer restorer = scope.streamingRestorer();
        StringBuilder output = new StringBuilder();
        for (String chunk : chunks) {
            output.append(restorer.process(chunk));
        }
        output.append(restorer.flush());
        return output.toString();
    }

    @Test
    void completeTagInSingleChunkIsRestored() {
        assertEquals("Envia a juan@example.com ya",
                streamAll(List.of("Envia a " + tag + " ya")));
    }

    @Test
    void tagSplitAcrossTwoChunksIsRestored() {
        int mid = tag.length() / 2;
        assertEquals("Envia a juan@example.com ya",
                streamAll(List.of("Envia a " + tag.substring(0, mid), tag.substring(mid) + " ya")));
    }

    @Test
    void tagSplitCharacterByCharacterIsRestored() {
        List<String> chunks = ("Hola " + tag + " fin").chars()
                .mapToObj(c -> String.valueOf((char) c))
                .toList();
        assertEquals("Hola juan@example.com fin", streamAll(chunks));
    }

    @Test
    void loneDelimiterIsEmitted() {
        assertEquals("precio ~ 100 euros",
                streamAll(List.of("precio ~ 1", "00 euros")));
    }

    @Test
    void unfinishedTagAtEndOfStreamIsFlushedAsIs() {
        String truncated = tag.substring(0, tag.length() - 1);
        assertEquals("texto " + truncated,
                streamAll(List.of("texto " + truncated)));
    }

    @Test
    void multipleTagsAcrossChunksAreRestored() {
        String tag2 = scope.obfuscate("maria@test.com");
        int mid = tag2.length() / 3;
        assertEquals("De juan@example.com a maria@test.com.",
                streamAll(List.of("De " + tag + " a " + tag2.substring(0, mid),
                        tag2.substring(mid) + ".")));
    }

    @Test
    void unknownTagIsLeftUntouched() {
        String unknown = "~REDACTED:DNI#abcdef~";
        assertEquals("dato " + unknown + " fin",
                streamAll(List.of("dato " + unknown.substring(0, 10), unknown.substring(10) + " fin")));
    }
}
