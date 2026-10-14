package com.ploybot.promptshield.injection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The index only decides where a rule's regex is tried: it must never hide a match.
 * Texts are built from each rule's own vocabulary so that a good share of them match.
 */
class RuleIndexTest {

    private static final String[] GLUE = {" ", "  ", ", ", ". ", "\n", "-", "'", "\"", "(", ")", "[", "]", "<", ">",
            "|", "!", "?", ":", "/", "_", "*", "#", "@", "", "", " the ", " your ", " you ", " all ", " a "};
    private static final Pattern LITERALS = Pattern.compile("[\\p{L}\\p{N}' .,:<>|\\[\\]/!_-]+");

    private final List<InjectionRule> rules = new HeuristicInjectionDetector().getRules();
    private final RuleIndex index = new RuleIndex(rules);

    @Test
    void everyBuiltInRuleExceptRawOnesHasPrefixes() {
        for (int r = 0; r < rules.size(); r++) {
            assertEquals(!rules.get(r).raw(), index.hasPrefixes(r), rules.get(r).id());
        }
    }

    @Test
    void candidateSearchFindsWhatAFullSearchFinds() {
        Random random = new Random(7);
        int matched = 0;
        for (int r = 0; r < rules.size(); r++) {
            InjectionRule rule = rules.get(r);
            if (!index.hasPrefixes(r)) {
                continue;
            }
            List<String> vocabulary = vocabulary(rule, random);
            for (int i = 0; i < 400; i++) {
                String text = text(vocabulary, random);
                Matcher full = rule.pattern().matcher(text);
                String expected = full.find() ? full.start() + "-" + full.end() : "none";
                RuleIndex.Scan scan = index.scan(text);
                assertEquals(expected, candidateSearch(rule, text, scan, r), rule.id() + " on " + text);
                assertEquals(rule.mayMatch(text), scan.mayMatch(r), rule.id() + " anchors on " + text);
                matched += expected.equals("none") ? 0 : 1;
            }
        }
        assertTrue(matched > 4000, "too few matching texts to be meaningful: " + matched);
    }

    private static String candidateSearch(InjectionRule rule, String text, RuleIndex.Scan scan, int r) {
        Matcher matcher = rule.pattern().matcher(text).useTransparentBounds(true).useAnchoringBounds(false);
        int[] candidates = scan.candidates(r);
        for (int i = 0; i < scan.candidateCount(r); i++) {
            matcher.region(candidates[i], text.length());
            if (matcher.lookingAt()) {
                return matcher.start() + "-" + matcher.end();
            }
        }
        return "none";
    }

    private static List<String> vocabulary(InjectionRule rule, Random random) {
        String source = rule.pattern().pattern().replace("\\W+", " ").replace("\\s*", " ").replace("\\b", "");
        List<String> words = new ArrayList<>();
        Matcher matcher = LITERALS.matcher(source);
        while (matcher.find()) {
            String literal = matcher.group();
            words.add(literal);
            if (literal.length() > 3) {
                words.add(literal.substring(random.nextInt(literal.length() / 2)));
            }
        }
        return words;
    }

    private static String text(List<String> vocabulary, Random random) {
        StringBuilder text = new StringBuilder();
        int parts = 1 + random.nextInt(14);
        for (int k = 0; k < parts; k++) {
            text.append(vocabulary.get(random.nextInt(vocabulary.size()))).append(GLUE[random.nextInt(GLUE.length)]);
            if (random.nextInt(8) == 0) {
                text.append((char) ('a' + random.nextInt(26)));
            }
        }
        return random.nextBoolean() ? text.toString().toLowerCase(Locale.ROOT) : text.toString();
    }
}
