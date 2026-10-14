package com.ploybot.promptshield.injection.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.injection.ContentSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loads the evaluation corpus from the classpath.
 *
 * <ul>
 *   <li>{@code dev}: hand-written seeds (scripts/build_dev_corpus.py). Written together
 *       with the rules, so results on them are optimistic.</li>
 *   <li>{@code dev-mutated}: the seeds after the deterministic {@link Mutators}:
 *       Unicode tricks, encodings, documents and MCP tool results.</li>
 *   <li>{@code bipia-train}: BIPIA's train split, used to tune the indirect rules.</li>
 *   <li>{@code review-r1-tune} / {@code review-r1-holdout}: adaptive cases by the
 *       independent reviewer (see {@link #REVIEW}).</li>
 *   <li>External, held-out sets (scripts/import_external.py), never used to tune
 *       rules: {@code bipia}, {@code llm-guard-tests}, {@code prompt-shield-ai-sample},
 *       {@code pint-example}.</li>
 * </ul>
 */
public final class Corpus {

    public static final String DEV = "dev";
    public static final String DEV_MUTATED = "dev-mutated";
    public static final List<String> EXTERNAL = List.of("bipia", "llm-guard-tests", "prompt-shield-ai-sample", "pint-example");
    /** BIPIA's train split: third-party, but used to tune the indirect-injection rules (not held out). */
    public static final String BIPIA_TRAIN = "bipia-train";
    /**
     * Written by an independent reviewer who knew the v1 rules (adaptive attacks and
     * hard negatives); every attack bypassed v1. The tune half was read to write v2; the
     * holdout half was only measured.
     */
    public static final List<String> REVIEW = List.of("review-r1-tune", "review-r1-holdout");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ROOT = "/injection-corpus/";

    private Corpus() {
    }

    public static List<Sample> devSeeds() {
        List<Sample> samples = new ArrayList<>();
        samples.addAll(load(ROOT + "dev/attacks-direct.jsonl", DEV));
        samples.addAll(load(ROOT + "dev/attacks-indirect-payloads.jsonl", DEV));
        samples.addAll(load(ROOT + "dev/benign.jsonl", DEV));
        return samples;
    }

    public static List<Sample> external(String name) {
        return load(ROOT + "external/" + name + ".jsonl", name);
    }

    /** Seeds, their mutations and every external set. */
    public static List<Sample> all(long seed) {
        List<Sample> seeds = devSeeds();
        List<Sample> samples = new ArrayList<>(seeds);
        samples.addAll(new Mutators(seed).mutate(seeds));
        samples.addAll(load(ROOT + "external/" + BIPIA_TRAIN + ".jsonl", BIPIA_TRAIN));
        for (String name : REVIEW) {
            samples.addAll(load(ROOT + "review/" + name + ".jsonl", name));
        }
        for (String name : EXTERNAL) {
            samples.addAll(external(name));
        }
        return samples;
    }

    static List<Sample> load(String resource, String dataset) {
        List<Sample> samples = new ArrayList<>();
        try (InputStream in = Corpus.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing corpus resource " + resource);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = MAPPER.readTree(line);
                samples.add(new Sample(
                        node.get("id").asText(),
                        node.get("label").asInt() == 1,
                        ContentSource.valueOf(node.get("source").asText().toUpperCase(Locale.ROOT)),
                        dataset,
                        node.path("category").asText("unknown"),
                        node.path("technique").asText("plain"),
                        node.path("lang").asText("en"),
                        node.get("text").asText()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return samples;
    }
}
