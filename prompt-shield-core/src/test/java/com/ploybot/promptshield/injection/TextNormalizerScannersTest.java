package com.ploybot.promptshield.injection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The normalizer replaced several full-text regexes with linear scans, which were a
 * large share of its cost. Each scan must give exactly what the regex it replaced gave,
 * including the corner cases of java.util.regex (a lookbehind that does not see a
 * supplementary letter, backtracking over padding).
 */
class TextNormalizerScannersTest {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern MARKS = Pattern.compile("\\p{Mn}+");
    private static final Pattern MARKDOWN_EMPHASIS = Pattern.compile("[*`]+|~~");
    private static final Pattern IN_WORD_SEPARATORS = Pattern.compile("(?<=\\p{L})[*_~`^|'\\u00b7\\u2022-]{1,2}(?=\\p{L})");
    private static final Pattern BASE64_TOKEN = Pattern.compile("(?<![A-Za-z0-9+/=_-])[A-Za-z0-9+/_-]{16,}={0,2}");
    private static final Pattern BASE32_TOKEN = Pattern.compile("(?<![A-Za-z0-9])[A-Z2-7]{16,}={0,6}(?![A-Za-z0-9])");

    /** Pieces that exercise every branch: separators, padding, marks, surrogates, odd whitespace. */
    private static final String[] PIECES = {"a", "e", "o", "Z", "Q", "A", "M", "2", "3", "7", "8", "0", "1", "=", "==", "+",
            "/", "_", "-", "*", "`", "~", "^", "|", "'", "·", "•", " ", "  ", "\t", "\n", "\u000B", "\f", "\r",
            " ", " ", "\u0085", "　", "é", "é", "́", "̈", "а", "о", "α", "ς", "Σ", "ﬁ", "Ａ",
            "𝐀", "𝐞", "\uD800", "\uDC00", "한", "가", "ß", "İ", "ㄱ", "€", "!", ".", ",",
            "QUJD", "aGVsbG8gd29ybGQgaG93IGFyZQ", "JBSWY3DPEBLW64TMMQQQ", "​"};

    private static List<String> randomTexts(long seed, int count) {
        Random random = new Random(seed);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder text = new StringBuilder();
            int parts = 1 + random.nextInt(random.nextInt(10) == 0 ? 120 : 25);
            for (int k = 0; k < parts; k++) {
                if (random.nextInt(6) == 0) {
                    String alphabet = random.nextBoolean() ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" : "ABCdef012+/_-xyzQ";
                    int run = 10 + random.nextInt(20);
                    for (int r = 0; r < run; r++) {
                        text.append(alphabet.charAt(random.nextInt(alphabet.length())));
                    }
                } else {
                    text.append(PIECES[random.nextInt(PIECES.length)]);
                }
            }
            texts.add(text.toString());
        }
        return texts;
    }

    @Test
    void scansGiveWhatTheRegexesGave() {
        for (String text : randomTexts(20241014L, 20_000)) {
            assertEquals(WHITESPACE.matcher(text).replaceAll(" ").strip(), TextNormalizer.collapseWhitespace(text), text);
            assertEquals(MARKS.matcher(text).replaceAll(""), TextNormalizer.removeMarks(text), text);
            assertEquals(MARKDOWN_EMPHASIS.matcher(text).replaceAll(""), TextNormalizer.removeMarkdownEmphasis(text), text);
            assertEquals(IN_WORD_SEPARATORS.matcher(text).replaceAll(""), TextNormalizer.removeInWordSeparators(text), text);
            assertEquals(mixedScriptTokens(text), TextNormalizer.mixedScriptTokens(text), text);
            assertEquals(decoded(BASE64_TOKEN, text, false), TextNormalizer.decodeBase64(text), text);
            assertEquals(decoded(BASE32_TOKEN, text, true), TextNormalizer.decodeBase32(text), text);
        }
    }

    @Test
    void separatorAfterASupplementaryLetterIsKeptAsTheRegexDid() {
        // java.util.regex's (?<=\p{L}) only looked at the low surrogate
        assertEquals("𝐀~a", TextNormalizer.removeInWordSeparators("𝐀~a"));
        assertEquals("ab", TextNormalizer.removeInWordSeparators("a~b"));
        assertEquals("a𝐀", TextNormalizer.removeInWordSeparators("a-𝐀"));
    }

    @Test
    void base32PaddingFollowedByALetterBacksOffOneEqualsSign() {
        String run = "JBSWY3DPEBLW64TMMQQQ";
        assertEquals(decoded(BASE32_TOKEN, run + "=x", true), TextNormalizer.decodeBase32(run + "=x"));
        assertEquals(decoded(BASE32_TOKEN, run + "x", true), TextNormalizer.decodeBase32(run + "x"));
    }

    /** The regex version of the decoders, with the same filters as the scanners. */
    private static String decoded(Pattern token, String text, boolean base32) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = token.matcher(text);
        int segments = 0;
        while (matcher.find() && segments < 32) {
            String value = matcher.group();
            String decoded = base32 ? TextNormalizer.decodeBase32(value.replace("=", "")) : TextNormalizer.decodeBase64(value);
            if (!decoded.isEmpty()) {
                out.append(decoded);
                segments++;
            }
        }
        return out.toString();
    }

    private static int mixedScriptTokens(String text) {
        int count = 0;
        for (String token : WHITESPACE.split(text)) {
            boolean latin = false;
            boolean other = false;
            for (int k = 0; k < token.length(); k++) {
                char c = token.charAt(k);
                if (!Character.isLetter(c)) {
                    continue;
                }
                Character.UnicodeScript script = Character.UnicodeScript.of(c);
                latin |= script == Character.UnicodeScript.LATIN;
                other |= script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.GREEK;
            }
            if (latin && other) {
                count++;
            }
        }
        return count;
    }
}
