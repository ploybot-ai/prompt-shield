package com.ploybot.promptshield.injection;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexPrefixesTest {

    private static Set<String> prefixes(String regex) {
        return RegexPrefixes.of(Pattern.compile(regex));
    }

    @Test
    void leadingAlternationOfWords() {
        assertEquals(Set.of("ign", "for", "dis"), prefixes("\\b(?:ignore|forget(?:s|ting)?|disregard)\\W+all"));
    }

    @Test
    void optionalPartsAddTheirAlternatives() {
        assertEquals(Set.of("the", "rul", "ins"), prefixes("\\b(?:the\\W+)?(?:rules|instructions)\\b"));
        assertEquals(Set.of("ab", "b"), prefixes("a?b"));
    }

    @Test
    void shortMatchesAndClasses() {
        assertEquals(Set.of("i", "we"), prefixes("\\b(?:i|we)\\b"));
        assertEquals(Set.of("se", "ze"), prefixes("[sz]e"));
        assertEquals(Set.of("aaa", "aab"), prefixes("a{2,}b"));
    }

    @Test
    void zeroWidthConstructsAreSkipped() {
        assertEquals(Set.of("abc"), prefixes("(?<!x)(?=a)^abc(?!d)"));
        assertEquals(Set.of("![", "![]"), prefixes("!\\[[^\\]]{0,200}\\]"));
    }

    @Test
    void whitespaceAndDigitsAreEnumeratedOnlyForAsciiClasses() {
        Set<String> ascii = prefixes("\\sab");
        assertTrue(ascii.containsAll(Set.of(" ab", "\tab", "\nab")) && ascii.size() == 6, ascii.toString());
        assertEquals(10, prefixes("\\d").size());
        assertNull(RegexPrefixes.of(Pattern.compile("\\sab", Pattern.UNICODE_CHARACTER_CLASS)));
    }

    @Test
    void givesUpWhenAMatchCanStartAnywhereOrTheSyntaxIsUnknown() {
        assertNull(prefixes("\\W+ignore"));
        assertNull(prefixes(".*ignore"));
        assertNull(prefixes("[^a]bc"));
        assertNull(prefixes("(?:x)?\\w+"));
        assertNull(prefixes("(?i)ignore"));
        assertNull(prefixes("(a)\\1"));
        assertNull(prefixes("\\Qabc\\E"));
        assertNull(RegexPrefixes.of(Pattern.compile("ignore", Pattern.CASE_INSENSITIVE)));
    }

    @Test
    void largeCombinationsFallBackToShorterPrefixes() {
        Set<String> prefixes = prefixes("[a-z0-9_]{1,40}x");
        assertTrue(prefixes != null && prefixes.stream().allMatch(p -> p.length() <= RegexPrefixes.MAX_LENGTH), String.valueOf(prefixes));
    }
}
