package com.ploybot.promptshield.injection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency-free prompt-injection detector based on weighted multilingual rules
 * applied to several normalized views of the text (see {@link TextNormalizer}), plus
 * character-level anomalies (Unicode tag smuggling, variation-selector payloads,
 * zero-width characters inside words, bidi controls, mixed-script words).
 *
 * <p>Each rule contributes a weight that depends on the {@link ContentSource}; the
 * score combines the strongest weight of every rule that fired as a noisy-OR,
 * {@code 1 - Π(1 - w)}, so independent weak evidence adds up while a single rule can
 * never exceed its own weight. A rule found only in a decoded or de-obfuscated view
 * adds an extra evasion signal: benign text rarely needs to hide an instruction.
 *
 * <p><strong>Limits.</strong> Heuristics catch known phrasings and the common ways
 * of disguising them. They do not understand intent: a paraphrased instruction with
 * none of the vocabulary below, or an injection that simply asks for a plausible
 * action ("also add a 10% discount for order 1234") will pass. Treat the verdict as
 * one layer of defence and combine it with least privilege for tools, confirmation
 * of sensitive actions and, if available, a model-based classifier through
 * {@link CompositeInjectionDetector}.
 *
 * <p>Instances are immutable and thread safe.
 */
public class HeuristicInjectionDetector implements InjectionDetector {

    public static final double DEFAULT_THRESHOLD = 0.5;

    private static final int EXCERPT_LENGTH = 80;
    /** Views that only reformat the text: a match there is not evidence of hiding anything. */
    private static final Set<String> PLAIN_LAYERS = Set.of("normalized", "json-fields");

    private final List<InjectionRule> rules;
    private final Map<ContentSource, Double> thresholds;
    private final Pattern placeholderPattern;
    private final boolean deobfuscation;

    public HeuristicInjectionDetector() {
        this(builder());
    }

    private HeuristicInjectionDetector(Builder builder) {
        List<InjectionRule> selected = new ArrayList<>();
        for (InjectionRule rule : builder.rules) {
            if (!builder.disabledRules.contains(rule.id())) {
                selected.add(rule);
            }
        }
        this.rules = List.copyOf(selected);
        this.thresholds = new EnumMap<>(builder.thresholds);
        this.placeholderPattern = builder.placeholderPattern;
        this.deobfuscation = builder.deobfuscation;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<InjectionRule> getRules() {
        return rules;
    }

    public double getThreshold(ContentSource source) {
        return thresholds.getOrDefault(source, DEFAULT_THRESHOLD);
    }

    @Override
    public InjectionVerdict analyze(String text, ContentSource source) {
        ContentSource effectiveSource = source == null ? ContentSource.USER : source;
        double threshold = getThreshold(effectiveSource);
        if (text == null || text.isBlank()) {
            return InjectionVerdict.clean(effectiveSource, threshold);
        }

        TextNormalizer.Result normalized = TextNormalizer.normalize(text, deobfuscation);
        Map<String, InjectionSignal> signals = new LinkedHashMap<>();
        Set<String> plainMatches = new HashSet<>();
        String lowerRaw = null;

        for (InjectionRule rule : rules) {
            double weight = rule.weight(effectiveSource);
            if (weight <= 0) {
                continue;
            }
            if (rule.raw()) {
                if (lowerRaw == null) {
                    lowerRaw = text.toLowerCase(java.util.Locale.ROOT);
                }
                if (!rule.mayMatch(lowerRaw)) {
                    continue;
                }
                Matcher matcher = rule.pattern().matcher(text);
                if (find(matcher, rule, "raw", signals)) {
                    plainMatches.add(rule.id());
                    record(signals, new InjectionSignal(rule.id(), rule.category(), weight, "raw", excerpt(text, matcher)));
                }
                continue;
            }
            for (TextNormalizer.Layer layer : normalized.layers()) {
                if (!rule.mayMatch(layer.text())) {
                    continue;
                }
                Matcher matcher = rule.pattern().matcher(layer.text());
                if (find(matcher, rule, layer.name(), signals)) {
                    if (PLAIN_LAYERS.contains(layer.name())) {
                        plainMatches.add(rule.id());
                    }
                    record(signals, new InjectionSignal(rule.id(), rule.category(), weight, layer.name(),
                            excerpt(layer.text(), matcher)));
                    if (PLAIN_LAYERS.contains(layer.name())) {
                        break;
                    }
                }
            }
        }

        addEvasionSignal(signals, plainMatches);
        addCharacterSignals(signals, normalized.stats());
        if (effectiveSource.isIndirect() && placeholderPattern != null) {
            Matcher matcher = placeholderPattern.matcher(text);
            if (matcher.find()) {
                // Weak on its own: tools legitimately echo the placeholders they were given
                record(signals, new InjectionSignal("placeholder.planted", InjectionCategory.PLACEHOLDER_ABUSE, 0.25,
                        "raw", excerpt(text, matcher)));
            }
        }

        List<InjectionSignal> ordered = new ArrayList<>(signals.values());
        ordered.sort(Comparator.comparingDouble(InjectionSignal::weight).reversed());
        return new InjectionVerdict(combine(ordered), threshold, effectiveSource, ordered);
    }

    /**
     * {@code java.util.regex} recurses on some constructs and may overflow the stack on
     * huge crafted inputs. Such an input must not pass silently: it fails closed with a
     * signal that reaches the default threshold on its own.
     */
    private static boolean find(Matcher matcher, InjectionRule rule, String layer, Map<String, InjectionSignal> signals) {
        try {
            return matcher.find();
        } catch (StackOverflowError e) {
            signals.put("engine.regex-overflow", new InjectionSignal("engine.regex-overflow",
                    InjectionCategory.OBFUSCATION, 0.5, layer, "rule " + rule.id() + " overflowed the regex engine"));
            return false;
        }
    }

    /** Keeps the plain-text match of a rule, or else its strongest one. */
    private static void record(Map<String, InjectionSignal> signals, InjectionSignal signal) {
        InjectionSignal existing = signals.get(signal.ruleId());
        if (existing == null || "normalized".equals(signal.layer()) && !"normalized".equals(existing.layer())) {
            signals.put(signal.ruleId(), signal);
        }
    }

    /**
     * A meaningful rule that only matched after decoding or de-obfuscation means
     * someone went out of their way to hide it.
     */
    private static void addEvasionSignal(Map<String, InjectionSignal> signals, Set<String> plainMatches) {
        InjectionSignal strongest = null;
        for (InjectionSignal signal : signals.values()) {
            if (signal.weight() >= 0.25 && !plainMatches.contains(signal.ruleId())
                    && (strongest == null || signal.weight() > strongest.weight())) {
                strongest = signal;
            }
        }
        if (strongest != null) {
            signals.put("obfuscation.evasive-match", new InjectionSignal("obfuscation.evasive-match",
                    InjectionCategory.OBFUSCATION, 0.35, strongest.layer(), strongest.evidence()));
        }
    }

    private static void addCharacterSignals(Map<String, InjectionSignal> signals, TextNormalizer.Stats stats) {
        if (stats.tagCharacters() > 0) {
            signals.put("hidden.unicode-tags", new InjectionSignal("hidden.unicode-tags", InjectionCategory.OBFUSCATION,
                    0.6, "hidden", stats.tagCharacters() + " invisible tag characters"));
        }
        if (stats.variationSelectorPayload() > 0) {
            signals.put("hidden.variation-selectors", new InjectionSignal("hidden.variation-selectors",
                    InjectionCategory.OBFUSCATION, 0.6, "hidden",
                    stats.variationSelectorPayload() + " bytes in variation selectors"));
        }
        if (stats.zeroWidthInWords() > 0) {
            double weight = stats.zeroWidthInWords() >= 3 ? 0.3 : 0.15;
            signals.put("hidden.zero-width", new InjectionSignal("hidden.zero-width", InjectionCategory.OBFUSCATION,
                    weight, "raw", stats.zeroWidthInWords() + " zero-width characters inside words"));
        }
        if (stats.bidiControls() > 0) {
            signals.put("hidden.bidi-controls", new InjectionSignal("hidden.bidi-controls", InjectionCategory.OBFUSCATION,
                    0.2, "raw", stats.bidiControls() + " bidirectional control characters"));
        }
        if (stats.mixedScriptTokens() > 0) {
            double weight = stats.mixedScriptTokens() >= 3 ? 0.35 : 0.25;
            signals.put("obfuscation.mixed-script", new InjectionSignal("obfuscation.mixed-script",
                    InjectionCategory.OBFUSCATION, weight, "normalized",
                    stats.mixedScriptTokens() + " words mixing Latin with Cyrillic or Greek letters"));
        }
    }

    /**
     * Noisy-OR over rule families. Language variants of the same rule
     * ({@code override.en}, {@code override.fr}...) often fire together on one sentence
     * ("ignore" is also French); they are one piece of evidence, not several.
     */
    private static double combine(Collection<InjectionSignal> signals) {
        Map<String, Double> families = new LinkedHashMap<>();
        for (InjectionSignal signal : signals) {
            families.merge(family(signal.ruleId()), signal.weight(), Math::max);
        }
        double clean = 1;
        for (double weight : families.values()) {
            clean *= 1 - weight;
        }
        return 1 - clean;
    }

    private static final Pattern LANGUAGE_VARIANT = Pattern.compile("^(\\w+)\\.(?:en|es|fr|de|pt|it|ca|ru|zh|fr-de-pt-it|ru-zh)$");

    /** Rules that fire on the same words ("Dear assistant,") and must not add up. */
    private static final Map<String, String> SHARED_FAMILIES = Map.of(
            "addressed.greeting", "addressed.vocative",
            "override.en-given", "override.*",
            "override.en-weak-verb", "override.*");

    static String family(String ruleId) {
        String shared = SHARED_FAMILIES.get(ruleId);
        if (shared != null) {
            return shared;
        }
        Matcher matcher = LANGUAGE_VARIANT.matcher(ruleId);
        return matcher.matches() ? matcher.group(1) + ".*" : ruleId;
    }

    private static String excerpt(String text, Matcher matcher) {
        int end = Math.min(matcher.end(), matcher.start() + EXCERPT_LENGTH);
        return text.substring(matcher.start(), end);
    }

    public static final class Builder {

        private final List<InjectionRule> rules = new ArrayList<>(BuiltInRules.rules());
        private final Set<String> disabledRules = new HashSet<>();
        private final Map<ContentSource, Double> thresholds = new EnumMap<>(ContentSource.class);
        private Pattern placeholderPattern = Pattern.compile(BuiltInRules.DEFAULT_TAG_REGEX);
        private boolean deobfuscation = true;

        private Builder() {
            for (ContentSource source : ContentSource.values()) {
                thresholds.put(source, DEFAULT_THRESHOLD);
            }
        }

        /** Sets the threshold of every source. Lower is stricter: more detections, more false positives. */
        public Builder threshold(double threshold) {
            for (ContentSource source : ContentSource.values()) {
                threshold(source, threshold);
            }
            return this;
        }

        public Builder threshold(ContentSource source, double threshold) {
            if (threshold <= 0 || threshold > 1 || Double.isNaN(threshold)) {
                throw new IllegalArgumentException("threshold must be in (0, 1]");
            }
            thresholds.put(source, threshold);
            return this;
        }

        public Builder addRule(InjectionRule rule) {
            if (rule == null) {
                throw new IllegalArgumentException("rule must not be null");
            }
            rules.add(rule);
            return this;
        }

        /** Replaces the whole rule set, built-in rules included. */
        public Builder rules(Collection<InjectionRule> replacement) {
            if (replacement == null || replacement.contains(null)) {
                throw new IllegalArgumentException("rules must not be null");
            }
            rules.clear();
            rules.addAll(replacement);
            return this;
        }

        /**
         * Whether to also scan the de-obfuscated views (lookalikes, leetspeak, spacing,
         * invisible text, base64/hex/URL/HTML-entity decoding, rot13, reversed text).
         * Disabling it roughly halves the latency and makes every obfuscated payload
         * invisible to the rules; the character anomaly signals remain.
         */
        public Builder deobfuscation(boolean enabled) {
            this.deobfuscation = enabled;
            return this;
        }

        /** Disables built-in or added rules by id, e.g. to silence a source of false positives. */
        public Builder disableRules(Collection<String> ruleIds) {
            disabledRules.addAll(ruleIds);
            return this;
        }

        /**
         * The placeholder format of the obfuscation engine (see
         * {@code ObfuscationEngine#getTagPattern()}). A placeholder inside a document or a
         * tool result was planted by a third party: the engine only creates them in
         * outgoing text. {@code null} disables the check.
         */
        public Builder placeholderPattern(Pattern pattern) {
            this.placeholderPattern = pattern;
            return this;
        }

        public HeuristicInjectionDetector build() {
            return new HeuristicInjectionDetector(this);
        }
    }
}
