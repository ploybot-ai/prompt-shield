package com.ploybot.promptshield.injection;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A weighted pattern. Patterns are matched against the lower-case, accent-free views
 * built by the normalizer unless {@code raw} is set, in which case they are matched
 * against the original text.
 *
 * @param id             stable identifier, reported in signals and logs
 * @param category       what the rule detects
 * @param pattern        compiled pattern
 * @param directWeight   weight for {@link ContentSource#USER} and {@link ContentSource#SYSTEM}
 * @param indirectWeight weight for documents, tool results and tool definitions
 * @param raw            match the original text instead of the normalized views
 * @param anchors        optional pre-filter: lower-case substrings of which every match
 *                       contains at least one. When none occurs in the text the regex is
 *                       not run. An anchor list that misses a possible match silently
 *                       disables the rule for it, so keep them conservative; empty means
 *                       always run.
 */
public record InjectionRule(String id, InjectionCategory category, Pattern pattern,
                            double directWeight, double indirectWeight, boolean raw, List<String> anchors) {

    public InjectionRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (category == null || pattern == null) {
            throw new IllegalArgumentException("category and pattern are required");
        }
        checkWeight(directWeight);
        checkWeight(indirectWeight);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
    }

    /**
     * A rule over the normalized views. Write the regex in lower case and without
     * accents ("instruccion", not "Instrucción"). {@code \w} and {@code \b} are ASCII,
     * which is enough for Latin-script languages once accents are stripped and much
     * faster; use {@link #unicode} for other scripts.
     */
    public static InjectionRule of(String id, InjectionCategory category, String regex,
                                   double directWeight, double indirectWeight) {
        return new InjectionRule(id, category, Pattern.compile(regex), directWeight, indirectWeight, false, List.of());
    }

    /** Like {@link #of}, with Unicode-aware {@code \w} and {@code \b} (Cyrillic, CJK...). */
    public static InjectionRule unicode(String id, InjectionCategory category, String regex,
                                        double directWeight, double indirectWeight) {
        return new InjectionRule(id, category, Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS),
                directWeight, indirectWeight, false, List.of());
    }

    /** A rule over the original, case-sensitive text. */
    public static InjectionRule raw(String id, InjectionCategory category, String regex,
                                    double directWeight, double indirectWeight) {
        return new InjectionRule(id, category, Pattern.compile(regex), directWeight, indirectWeight, true, List.of());
    }

    /** A copy of this rule with the given pre-filter anchors (see the record description). */
    public InjectionRule withAnchors(List<String> anchors) {
        return new InjectionRule(id, category, pattern, directWeight, indirectWeight, raw, anchors);
    }

    /** Whether the regex can match the text according to the anchors. */
    public boolean mayMatch(String text) {
        if (anchors.isEmpty()) {
            return true;
        }
        for (String anchor : anchors) {
            if (text.contains(anchor)) {
                return true;
            }
        }
        return false;
    }

    public double weight(ContentSource source) {
        return source.isIndirect() ? indirectWeight : directWeight;
    }

    private static void checkWeight(double weight) {
        if (weight < 0 || weight > 1 || Double.isNaN(weight)) {
            throw new IllegalArgumentException("weights must be between 0 and 1");
        }
    }
}
