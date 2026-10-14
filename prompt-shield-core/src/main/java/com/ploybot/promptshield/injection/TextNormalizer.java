package com.ploybot.promptshield.injection;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the views of a text that the rules are matched against. Attackers hide
 * instructions from filters while keeping them readable for the model; each view
 * undoes one family of tricks:
 *
 * <ul>
 *   <li>{@code normalized}: invisible and format characters removed, NFKC (fullwidth,
 *       mathematical alphanumerics, ligatures), diacritics stripped, lower case.</li>
 *   <li>{@code skeleton}: the normalized text with Cyrillic/Greek lookalikes mapped to
 *       Latin, leetspeak folded, separators inside words removed and spaced-out words
 *       ("i g n o r e") joined.</li>
 *   <li>{@code hidden}: ASCII smuggled through Unicode tag characters (U+E0000 block)
 *       or variation-selector sequences.</li>
 *   <li>{@code base64}, {@code hex}, {@code url}, {@code html-entities}: decoded
 *       segments that look like encoded text.</li>
 *   <li>{@code rot13}, {@code reversed}: the whole text rotated or reversed.</li>
 * </ul>
 */
final class TextNormalizer {

    /** A view of the text, already lower case and NFKC-normalized. */
    record Layer(String name, String text) {
    }

    /** Character-level anomalies found while building the views. */
    record Stats(int tagCharacters, int variationSelectorPayload, int zeroWidthInWords, int bidiControls,
                 int mixedScriptTokens) {
    }

    record Result(String normalized, List<Layer> layers, Stats stats) {
    }

    private static final int MAX_DECODED_SEGMENTS = 32;
    private static final int MIN_DECODED_LENGTH = 8;

    private static final Pattern MARKS = Pattern.compile("\\p{Mn}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BASE64_TOKEN = Pattern.compile("(?<![A-Za-z0-9+/=_-])[A-Za-z0-9+/_-]{16,}={0,2}");
    private static final Pattern BASE32_TOKEN = Pattern.compile("(?<![A-Za-z0-9])[A-Z2-7]{16,}={0,6}(?![A-Za-z0-9])");
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final Pattern PERCENT = Pattern.compile("%[0-9a-fA-F]{2}");
    private static final Pattern HTML_ENTITY = Pattern.compile("&#(?:(\\d{2,7})|[xX]([0-9a-fA-F]{2,6}));");
    private static final Pattern MARKDOWN_EMPHASIS = Pattern.compile("[*`]+|~~");
    private static final Pattern IN_WORD_SEPARATORS = Pattern.compile("(?<=\\p{L})[*_~`^|'\\u00b7\\u2022-]{1,2}(?=\\p{L})");

    private TextNormalizer() {
    }

    static Result normalize(String raw) {
        return normalize(raw, true);
    }

    /**
     * @param deobfuscate build the skeleton, hidden and decoded views; when false only
     *                    the normalized view is produced (faster, blind to obfuscation)
     */
    static Result normalize(String raw, boolean deobfuscate) {
        Invisible invisible = stripInvisible(unescape(raw));
        String visible = invisible.visible.toString();
        String normalized = fold(visible);
        List<Layer> layers = new ArrayList<>(8);
        layers.add(new Layer("normalized", normalized));
        Stats stats = new Stats(invisible.tagCharacters, invisible.variationPayload, invisible.zeroWidthInWords,
                invisible.bidiControls, mixedScriptTokens(normalized));
        if (!deobfuscate) {
            return new Result(normalized, layers, stats);
        }

        String skeleton = skeleton(foldKeepingSpaces(visible));
        if (!skeleton.equals(normalized)) {
            layers.add(new Layer("skeleton", skeleton));
        }
        if (!invisible.hidden.isEmpty()) {
            String hidden = fold(invisible.hidden.toString());
            layers.add(new Layer("hidden", hidden));
            String hiddenSkeleton = skeleton(foldKeepingSpaces(invisible.hidden.toString()));
            if (!hiddenSkeleton.equals(hidden)) {
                layers.add(new Layer("hidden", hiddenSkeleton));
            }
        }
        addDecoded(layers, "json-fields", jsonFields(raw));
        String source = invisible.visible.toString() + (invisible.hidden.isEmpty() ? "" : "\n" + invisible.hidden);
        addDecoded(layers, "base64", decodeBase64(source));
        addDecoded(layers, "hex", decodeHex(source));
        addDecoded(layers, "base32", decodeBase32(source));
        addDecoded(layers, "url", decodePercent(source));
        addDecoded(layers, "html-entities", decodeEntities(source));
        if (invisible.bidiControls > 0 || mayBeEncoded(normalized)) {
            // Rotate before accents are stripped: an unrotated "ç" must not become a rotated "c"
            String lower = Normalizer.normalize(visible, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
            String rotated = fold(rot13(lower));
            layers.add(new Layer("rot13", rotated));
            layers.add(new Layer("reversed", new StringBuilder(normalized).reverse().toString()));
            if (!skeleton.equals(normalized)) {
                layers.add(new Layer("rot13", skeleton(rot13(lower))));
                layers.add(new Layer("reversed", new StringBuilder(skeleton).reverse().toString()));
            }
        }
        return new Result(normalized, layers, stats);
    }

    private static void addDecoded(List<Layer> layers, String name, String decoded) {
        if (decoded != null && !decoded.isBlank()) {
            String folded = fold(decoded);
            layers.add(new Layer(name, folded));
            String skeleton = skeleton(foldKeepingSpaces(decoded));
            if (!skeleton.equals(folded)) {
                layers.add(new Layer(name, skeleton));
            }
        }
    }

    /** NFKC, diacritics removed, lower case, whitespace collapsed. */
    static String fold(String text) {
        return WHITESPACE.matcher(foldKeepingSpaces(text)).replaceAll(" ").strip();
    }

    /** Like {@link #fold} but keeps the original spacing, which tells words apart in "i g n o r e  a l l". */
    private static String foldKeepingSpaces(String text) {
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        String decomposed = Normalizer.normalize(nfkc, Normalizer.Form.NFD);
        // Recompose what is left: Hangul syllables would otherwise stay as separate jamo
        String withoutMarks = Normalizer.normalize(MARKS.matcher(decomposed).replaceAll(""), Normalizer.Form.NFC);
        return withoutMarks.toLowerCase(Locale.ROOT);
    }

    private static final java.util.Set<String> FUNCTION_WORDS = java.util.Set.of(
            "the", "a", "an", "and", "or", "to", "of", "is", "are", "in", "on", "you", "for", "it", "that", "this",
            "with", "be", "i", "we", "not", "your", "my", "de", "la", "el", "que", "y", "en", "los", "las", "un",
            "una", "es", "por", "con", "para", "no", "se", "le", "et", "les", "des", "du", "der", "die", "und",
            "das", "ist", "il", "di", "che", "e", "o", "do", "da", "um", "uma");
    private static final Pattern ENCODING_HINT = Pattern.compile(
            "rot-?\\s?13|caesar|cesar|cipher|cifra|decod|descodif|decrypt|descifr|revers|backwards|al reves|"
                    + "invert|mirror|espejo|right to left|derecha a izquierda");

    /**
     * Whether the rot13 and reversed views are worth scanning: the text mentions a
     * cipher, or few of its words are common function words (rotated or reversed
     * prose loses them). Normal prose skips two full passes of the rules.
     */
    static boolean mayBeEncoded(String normalized) {
        if (ENCODING_HINT.matcher(normalized).find()) {
            return true;
        }
        String[] tokens = normalized.split("[^\\p{L}]+");
        int words = 0;
        int common = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            words++;
            common += FUNCTION_WORDS.contains(token) ? 1 : 0;
        }
        return words < 8 || common < words * 0.12;
    }

    /**
     * Resolves backslash escapes as found in JSON strings and source code ({@code \\n},
     * {@code \\t}, {@code \\"}, {@code \\uXXXX}, {@code \\u{XXXXX}}). Tool results usually arrive
     * as JSON: without this, "\\nIgnore" reads as the word "nignore" and escaped invisible
     * characters ({@code \\u200b}, {@code \\udb40\\udc41}) would stay hidden.
     */
    static String unescape(String text) {
        if (text.indexOf('\\') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= n) {
                out.append(c);
                continue;
            }
            char next = text.charAt(i + 1);
            switch (next) {
                case 'n': case 'r': case 't': case 'f': case 'b':
                    out.append(' ');
                    i++;
                    break;
                case '"': case '\'': case '/': case '\\':
                    out.append(next);
                    i++;
                    break;
                case 'u':
                    int end = i + 2;
                    boolean braces = end < n && text.charAt(end) == '{';
                    int start = braces ? end + 1 : end;
                    int stop = start;
                    while (stop < n && stop - start < (braces ? 6 : 4) && isHex(text.charAt(stop))) {
                        stop++;
                    }
                    boolean complete = braces ? stop < n && text.charAt(stop) == '}' && stop > start : stop - start == 4;
                    if (complete) {
                        int cp = Integer.parseInt(text.substring(start, stop), 16);
                        if (Character.isValidCodePoint(cp)) {
                            out.appendCodePoint(cp);
                            i = braces ? stop : stop - 1;
                            break;
                        }
                    }
                    out.append(c);
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- invisible text

    private static final class Invisible {
        final StringBuilder visible = new StringBuilder();
        final StringBuilder hidden = new StringBuilder();
        int tagCharacters;
        int variationPayload;
        int zeroWidthInWords;
        int bidiControls;
    }

    private static Invisible stripInvisible(String raw) {
        Invisible out = new Invisible();
        ByteArrayOutputStream variationBytes = new ByteArrayOutputStream();
        int previousVisible = -1;
        boolean inFlagSequence = false;
        int i = 0;
        while (i < raw.length()) {
            int cp = raw.codePointAt(i);
            int next = i + Character.charCount(cp) < raw.length() ? raw.codePointAt(i + Character.charCount(cp)) : -1;
            i += Character.charCount(cp);

            if (cp >= 0xE0000 && cp <= 0xE007F) {
                // Subdivision flags (England, Scotland, Wales) legitimately use tag characters
                if (previousVisible == 0x1F3F4 || inFlagSequence) {
                    inFlagSequence = cp != 0xE007F;
                    continue;
                }
                out.tagCharacters++;
                if (cp >= 0xE0020 && cp <= 0xE007E) {
                    out.hidden.append((char) (cp - 0xE0000));
                }
                continue;
            }
            inFlagSequence = false;

            int variation = variationSelectorValue(cp);
            if (variation >= 0) {
                variationBytes.write(variation);
                if (next < 0 || variationSelectorValue(next) < 0) {
                    flushVariationBytes(variationBytes, out);
                }
                continue;
            }

            if (isBidiControl(cp)) {
                out.bidiControls++;
                continue;
            }
            if (isInvisible(cp)) {
                if (cp != 0x200C && cp != 0x200D && isLatinLetter(previousVisible) && isLatinLetter(next)) {
                    out.zeroWidthInWords++;
                }
                continue;
            }
            out.visible.appendCodePoint(cp);
            previousVisible = cp;
        }
        return out;
    }

    /** A single selector after an emoji is normal; a run of them can carry bytes. */
    private static void flushVariationBytes(ByteArrayOutputStream bytes, Invisible out) {
        if (bytes.size() >= 4) {
            out.variationPayload += bytes.size();
            String decoded = decodeUtf8(bytes.toByteArray());
            if (decoded != null) {
                out.hidden.append(out.hidden.isEmpty() ? "" : " ").append(decoded);
            }
        }
        bytes.reset();
    }

    /** Byte value carried by a variation selector, or -1 (FE00-FE0F: 0-15, E0100-E01EF: 16-255). */
    private static int variationSelectorValue(int cp) {
        if (cp >= 0xFE00 && cp <= 0xFE0F) {
            return cp - 0xFE00;
        }
        if (cp >= 0xE0100 && cp <= 0xE01EF) {
            return cp - 0xE0100 + 16;
        }
        return -1;
    }

    private static boolean isBidiControl(int cp) {
        return cp >= 0x202A && cp <= 0x202E || cp >= 0x2066 && cp <= 0x2069;
    }

    private static boolean isInvisible(int cp) {
        return Character.getType(cp) == Character.FORMAT
                || cp == 0x034F || cp == 0x115F || cp == 0x1160 || cp == 0x3164 || cp == 0xFFA0 || cp == 0x2800;
    }

    private static boolean isLatinLetter(int cp) {
        return cp >= 0 && Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
    }

    // ---------------------------------------------------------------- skeleton

    /** Lookalike letters mapped to Latin. Applied after lower-casing. */
    private static char confusable(char c) {
        switch (c) {
            // Cyrillic
            case 'а': case 'ӑ': case 'ӓ': return 'a';
            case 'в': return 'b';
            case 'с': case 'ҫ': return 'c';
            case 'ԁ': return 'd';
            case 'е': case 'ё': case 'є': case 'ҽ': return 'e';
            case 'һ': case 'н': return 'h';
            case 'і': case 'ї': case 'ӏ': return 'i';
            case 'ј': return 'j';
            case 'к': return 'k';
            case 'м': return 'm';
            case 'о': case 'ӧ': return 'o';
            case 'р': return 'p';
            case 'ԛ': return 'q';
            case 'ѕ': return 's';
            case 'т': return 't';
            case 'у': case 'ү': return 'y';
            case 'ԝ': return 'w';
            case 'х': case 'ҳ': return 'x';
            // Greek
            case 'α': return 'a';
            case 'β': return 'b';
            case 'ϲ': return 'c';
            case 'ε': return 'e';
            case 'η': return 'n';
            case 'ι': return 'i';
            case 'κ': return 'k';
            case 'ν': return 'v';
            case 'ο': case 'σ': return 'o';
            case 'ρ': return 'p';
            case 'τ': return 't';
            case 'υ': return 'u';
            case 'χ': return 'x';
            case 'γ': return 'y';
            // Latin extensions and IPA
            case 'ɑ': return 'a';
            case 'ɡ': return 'g';
            case 'ı': return 'i';
            case 'ɩ': return 'i';
            case 'ʟ': return 'l';
            case 'ɴ': return 'n';
            case 'ʀ': return 'r';
            default: return c;
        }
    }

    private static char leet(char c) {
        switch (c) {
            case '0': return 'o';
            case '1': case '!': return 'i';
            case '3': return 'e';
            case '4': case '@': return 'a';
            case '5': case '$': return 's';
            case '7': case '+': return 't';
            case '8': return 'b';
            case '9': return 'g';
            case '|': return 'l';
            case '€': return 'e';
            default: return c;
        }
    }

    /** Expects lower-case text with its original spacing; returns it with whitespace collapsed. */
    static String skeleton(String lowerCase) {
        String text = MARKDOWN_EMPHASIS.matcher(mapLetters(lowerCase)).replaceAll("");
        text = IN_WORD_SEPARATORS.matcher(text).replaceAll("");
        text = joinSpacedLetters(text);
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /** Maps lookalikes everywhere and leetspeak inside tokens that also contain letters. */
    private static String mapLetters(String text) {
        char[] chars = text.toCharArray();
        int start = 0;
        while (start < chars.length) {
            int end = start;
            while (end < chars.length && !Character.isWhitespace(chars[end])) {
                end++;
            }
            boolean hasLetter = false;
            for (int k = start; k < end; k++) {
                chars[k] = confusable(chars[k]);
                hasLetter |= Character.isLetter(chars[k]);
            }
            if (!hasLetter && end - start <= 6 && isLeetNumber(chars, start, end)) {
                // Digit-only words of leetspeak: "4" for "a", "537" for "set"
                for (int k = start; k < end; k++) {
                    chars[k] = leet(chars[k]);
                }
            } else if (hasLetter) {
                for (int k = start; k < end; k++) {
                    // Leading/trailing "!" and "$" are punctuation, not letters
                    boolean edge = k == start || k == end - 1;
                    if (!(edge && (chars[k] == '!' || chars[k] == '$' || chars[k] == '+' || chars[k] == '|'))) {
                        chars[k] = leet(chars[k]);
                    }
                }
            }
            start = end + 1;
        }
        return new String(chars);
    }

    private static boolean isLeetNumber(char[] chars, int start, int end) {
        for (int k = start; k < end; k++) {
            if ("013457".indexOf(chars[k]) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * "i g n o r e  a l l" becomes "ignore all": runs of four or more single letters
     * separated by up to four spaces or punctuation marks are joined, and a gap of two
     * or more spaces (or a slash) inside the run is kept as a word break. A linear scan:
     * the equivalent regex needs an unbounded group repetition, which overflows the
     * stack of java.util.regex on long inputs.
     */
    static String joinSpacedLetters(String text) {
        StringBuilder out = null;
        int copied = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (!isSingleLetter(text, i)) {
                i++;
                continue;
            }
            List<Integer> letters = new ArrayList<>();
            letters.add(i);
            int j = i;
            while (true) {
                int k = j + 1;
                int separators = 0;
                while (k < n && separators < 4 && isSpacingSeparator(text.charAt(k))) {
                    k++;
                    separators++;
                }
                if (separators == 0 || k >= n || !isSingleLetter(text, k)) {
                    break;
                }
                letters.add(k);
                j = k;
            }
            if (letters.size() >= 4) {
                if (out == null) {
                    out = new StringBuilder(n);
                }
                out.append(text, copied, i);
                for (int m = 0; m < letters.size(); m++) {
                    if (m > 0 && isWordGap(text, letters.get(m - 1) + 1, letters.get(m))) {
                        out.append(' ');
                    }
                    out.append(text.charAt(letters.get(m)));
                }
                copied = j + 1;
            }
            i = j + 1;
        }
        return out == null ? text : out.append(text, copied, n).toString();
    }

    /** A letter with no letter or digit on either side. */
    private static boolean isSingleLetter(String text, int index) {
        return Character.isLetter(text.charAt(index))
                && (index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1)))
                && (index + 1 >= text.length() || !Character.isLetterOrDigit(text.charAt(index + 1)));
    }

    private static boolean isSpacingSeparator(char c) {
        return Character.isWhitespace(c) || ".-_/|,*+\u00b7\u2022".indexOf(c) >= 0;
    }

    private static boolean isWordGap(String text, int from, int to) {
        int spaces = 0;
        for (int k = from; k < to; k++) {
            char c = text.charAt(k);
            if (c == '/' || c == '|') {
                return true;
            }
            if (Character.isWhitespace(c)) {
                spaces++;
            }
        }
        return spaces >= 2;
    }

    private static int mixedScriptTokens(String normalized) {
        int count = 0;
        for (String token : WHITESPACE.split(normalized)) {
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

    // ---------------------------------------------------------------- decoders

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final int MAX_JSON_LENGTH = 1_000_000;

    /**
     * For a JSON document, the string values of each key joined in document order. A
     * payload split across fields ("Please disregard" in one search snippet, "the
     * earlier guidance" in the next) is read as one sentence by the model.
     */
    static String jsonFields(String raw) {
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_JSON_LENGTH || !(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }
        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = JSON.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
        java.util.Map<String, StringBuilder> byKey = new java.util.LinkedHashMap<>();
        collect(root, "", byKey, 0);
        StringBuilder out = new StringBuilder();
        for (StringBuilder joined : byKey.values()) {
            if (joined.indexOf("\u0000") >= 0) {
                out.append(joined.toString().replace('\u0000', ' ')).append(" .\n");
            }
        }
        return out.isEmpty() ? null : out.toString();
    }

    /** Joins with a NUL marker so that keys with a single value can be skipped. */
    private static void collect(com.fasterxml.jackson.databind.JsonNode node, String key,
                                java.util.Map<String, StringBuilder> byKey, int depth) {
        if (depth > 64) {
            return;
        }
        if (node.isTextual()) {
            StringBuilder joined = byKey.get(key);
            if (joined == null) {
                byKey.put(key, new StringBuilder(node.asText()));
            } else {
                joined.append('\u0000').append(node.asText());
            }
        } else if (node.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode item : node) {
                collect(item, key, byKey, depth + 1);
            }
        } else if (node.isObject()) {
            for (java.util.Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field : node.properties()) {
                collect(field.getValue(), field.getKey(), byKey, depth + 1);
            }
        }
    }

    private static String decodeBase64(String text) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = BASE64_TOKEN.matcher(text);
        int segments = 0;
        while (matcher.find() && segments < MAX_DECODED_SEGMENTS) {
            String token = matcher.group();
            if (!hasMixedCaseOrDigits(token)) {
                continue;
            }
            String decoded = tryBase64(token);
            if (decoded != null) {
                out.append(decoded).append('\n');
                segments++;
            }
        }
        return out.toString();
    }

    /** Plain long words are not base64; real payloads mix cases and digits. */
    private static boolean hasMixedCaseOrDigits(String token) {
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        for (int k = 0; k < token.length(); k++) {
            char c = token.charAt(k);
            upper |= Character.isUpperCase(c);
            lower |= Character.isLowerCase(c);
            digit |= Character.isDigit(c);
        }
        return upper && lower || (upper || lower) && digit;
    }

    private static String tryBase64(String token) {
        String padded = token.replace('-', '+').replace('_', '/');
        padded = padded.replace("=", "");
        if (padded.length() % 4 == 1) {
            return null;
        }
        while (padded.length() % 4 != 0) {
            padded += "=";
        }
        try {
            return printable(decodeUtf8(Base64.getDecoder().decode(padded)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Runs of ten or more hex byte pairs, each optionally prefixed by {@code \\x} or
     * {@code 0x} and followed by one separator. A linear scan, for the same reason as
     * {@link #joinSpacedLetters}.
     */
    private static String decodeHex(String text) {
        StringBuilder out = new StringBuilder();
        int segments = 0;
        int i = 0;
        int n = text.length();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (i < n && segments < MAX_DECODED_SEGMENTS) {
            bytes.reset();
            int j = i;
            while (true) {
                int k = j;
                if (k + 1 < n && (text.charAt(k) == '\\' || text.charAt(k) == '0')
                        && (text.charAt(k + 1) == 'x' || text.charAt(k + 1) == 'X')
                        && k + 3 < n && isHex(text.charAt(k + 2)) && isHex(text.charAt(k + 3))) {
                    k += 2;
                }
                if (k + 1 >= n || !isHex(text.charAt(k)) || !isHex(text.charAt(k + 1))) {
                    break;
                }
                bytes.write(Character.digit(text.charAt(k), 16) * 16 + Character.digit(text.charAt(k + 1), 16));
                k += 2;
                if (k < n && (Character.isWhitespace(text.charAt(k)) || text.charAt(k) == ':' || text.charAt(k) == ',')) {
                    k++;
                }
                j = k;
            }
            if (bytes.size() >= 10) {
                String decoded = printable(decodeUtf8(bytes.toByteArray()));
                if (decoded != null) {
                    out.append(decoded).append('\n');
                    segments++;
                }
            }
            i = j > i ? j : i + 1;
        }
        return out.toString();
    }

    /** RFC 4648 base32 runs (upper case, as produced by common encoders). */
    private static String decodeBase32(String text) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = BASE32_TOKEN.matcher(text);
        int segments = 0;
        while (matcher.find() && segments < MAX_DECODED_SEGMENTS) {
            String token = matcher.group().replace("=", "");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(token.length() * 5 / 8);
            int buffer = 0;
            int bits = 0;
            for (int k = 0; k < token.length(); k++) {
                buffer = (buffer << 5) | BASE32_ALPHABET.indexOf(token.charAt(k));
                bits += 5;
                if (bits >= 8) {
                    bytes.write((buffer >> (bits - 8)) & 0xFF);
                    bits -= 8;
                }
            }
            String decoded = printable(decodeUtf8(bytes.toByteArray()));
            if (decoded != null) {
                out.append(decoded).append('\n');
                segments++;
            }
        }
        return out.toString();
    }

    private static String decodePercent(String text) {
        Matcher matcher = PERCENT.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (++count >= 4) {
                break;
            }
        }
        if (count < 4) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(text.length());
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '%' && k + 2 < text.length() && isHex(text.charAt(k + 1)) && isHex(text.charAt(k + 2))) {
                bytes.write(Integer.parseInt(text.substring(k + 1, k + 3), 16));
                k += 2;
            } else if (c == '+') {
                bytes.write(' ');
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        String decoded = decodeUtf8(bytes.toByteArray());
        return decoded == null || decoded.equals(text) ? null : decoded;
    }

    private static String decodeEntities(String text) {
        Matcher matcher = HTML_ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            int cp = matcher.group(1) != null ? parseOrMinus(matcher.group(1), 10) : parseOrMinus(matcher.group(2), 16);
            String replacement = Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            count++;
        }
        matcher.appendTail(out);
        return count >= 4 ? out.toString() : null;
    }

    private static int parseOrMinus(String digits, int radix) {
        try {
            return Integer.parseInt(digits, radix);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** Keeps decoded text only if it reads like text: mostly printable, with letters and spaces. */
    private static String printable(String decoded) {
        if (decoded == null || decoded.length() < MIN_DECODED_LENGTH) {
            return null;
        }
        int printable = 0;
        int letters = 0;
        int nonLatin = 0;
        boolean space = false;
        for (int k = 0; k < decoded.length(); k++) {
            char c = decoded.charAt(k);
            if (!Character.isISOControl(c) || c == '\n' || c == '\t' || c == '\r') {
                printable++;
            }
            letters += Character.isLetter(c) ? 1 : 0;
            nonLatin += Character.isLetter(c) && c > 0x24F ? 1 : 0;
            space |= c == ' ';
        }
        // Chinese or Japanese text has no spaces
        boolean words = space || nonLatin >= letters * 0.5;
        boolean readable = printable >= decoded.length() * 0.95 && letters >= decoded.length() * 0.5 && words;
        return readable ? decoded : null;
    }

    static String rot13(String text) {
        char[] chars = text.toCharArray();
        for (int k = 0; k < chars.length; k++) {
            char c = chars[k];
            if (c >= 'a' && c <= 'z') {
                chars[k] = (char) ('a' + (c - 'a' + 13) % 26);
            }
        }
        return new String(chars);
    }
}
