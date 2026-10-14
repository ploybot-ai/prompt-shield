package com.ploybot.promptshield.injection;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Computes literal strings that every match of a {@code java.util.regex} pattern starts
 * with, so that the pattern only needs to be tried where one of them occurs.
 *
 * <p>{@link java.util.regex.Matcher#find()} tries the pattern at every position of the
 * text. For the rules of this package, which start with a long alternation of words
 * ({@code \b(?:ignore|disregard|forget|...)}), that attempt at each position was most of
 * the cost: on a 10 KB document, 60 to 99% of the time of the costliest rules.
 *
 * <p>The analysis over-approximates: zero-width constructs (word boundaries, anchors,
 * lookarounds) are treated as matching the empty string and possessive or lazy
 * quantifiers as greedy ones, which can only add prefixes. Anything it does not
 * understand (backreferences, inline flags, {@code \Q...\E}, character class
 * intersections) makes it give up, and so does a pattern that may start with an
 * arbitrary character ({@code \W+}, {@code .}, a negated class): the rule is then
 * matched as before, at every position. The result is either {@code null} or a set that
 * every match starts with.
 */
final class RegexPrefixes {

    /** Longest prefix tracked. Longer prefixes filter better but multiply with each character class. */
    static final int MAX_LENGTH = 3;
    private static final int MAX_PREFIXES = 2048;
    private static final int MAX_CLASS_SIZE = 64;

    private RegexPrefixes() {
    }

    /**
     * Literal prefixes of every match, or {@code null} when the pattern may start
     * anywhere or could not be analyzed.
     */
    static Set<String> of(Pattern pattern) {
        if ((pattern.flags() & (Pattern.CASE_INSENSITIVE | Pattern.LITERAL | Pattern.COMMENTS | Pattern.CANON_EQ)) != 0) {
            return null;
        }
        for (int length = MAX_LENGTH; length >= 1; length--) {
            Parser parser = new Parser(pattern.pattern(), length,
                    (pattern.flags() & Pattern.UNICODE_CHARACTER_CLASS) != 0);
            Set<Item> items;
            try {
                items = parser.alternation();
            } catch (Unsupported e) {
                return null;
            }
            if (!parser.atEnd()) {
                return null;
            }
            Set<String> prefixes = new HashSet<>();
            for (Item item : items) {
                prefixes.add(item.text);
            }
            if (!prefixes.contains("")) {
                return Set.copyOf(prefixes);
            }
            if (!parser.overflowed) {
                // A match may start with any character
                return null;
            }
            // Too many combinations at this length: try shorter prefixes
        }
        return null;
    }

    /**
     * A prefix of a match of a sub-expression. {@code open} means more characters may
     * follow that were not tracked (the prefix was truncated or reached a character the
     * analysis cannot enumerate); otherwise the text is a complete match.
     */
    private record Item(String text, boolean open) {
        static final Item EMPTY = new Item("", false);
    }

    private static final Set<Item> ANY = Set.of(new Item("", true));

    private static final class Unsupported extends RuntimeException {
        Unsupported(String message) {
            super(message, null, false, false);
        }
    }

    /** Recursive descent over the subset of the java.util.regex syntax used by rules. */
    private static final class Parser {

        private final String regex;
        private final int maxLength;
        /** With UNICODE_CHARACTER_CLASS, \\s and \\d also match non-ASCII characters. */
        private final boolean unicodeClasses;
        private int pos;
        /** A set grew too large and was replaced by "any string". */
        boolean overflowed;

        Parser(String regex, int maxLength, boolean unicodeClasses) {
            this.regex = regex;
            this.maxLength = maxLength;
            this.unicodeClasses = unicodeClasses;
        }

        /** The members of {@code \\s} or {@code \\d}, or {@code null} for other predefined classes. */
        private Set<String> predefined(char c) {
            if (unicodeClasses) {
                return null;
            }
            if (c == 's') {
                return Set.of(" ", "\t", "\n", "\u000B", "\f", "\r");
            }
            if (c == 'd') {
                return Set.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
            }
            return null;
        }

        boolean atEnd() {
            return pos >= regex.length();
        }

        Set<Item> alternation() {
            Set<Item> result = new LinkedHashSet<>(sequence());
            while (pos < regex.length() && regex.charAt(pos) == '|') {
                pos++;
                result.addAll(sequence());
                result = bounded(result);
            }
            return result;
        }

        private Set<Item> sequence() {
            Set<Item> result = Set.of(Item.EMPTY);
            while (pos < regex.length() && regex.charAt(pos) != '|' && regex.charAt(pos) != ')') {
                Set<Item> atom = quantified(atom());
                result = concat(result, atom);
            }
            return result;
        }

        private Set<Item> quantified(Set<Item> atom) {
            if (pos >= regex.length()) {
                return atom;
            }
            int min;
            int max;
            char c = regex.charAt(pos);
            if (c == '?') {
                min = 0;
                max = 1;
                pos++;
            } else if (c == '*') {
                min = 0;
                max = -1;
                pos++;
            } else if (c == '+') {
                min = 1;
                max = -1;
                pos++;
            } else if (c == '{') {
                int close = regex.indexOf('}', pos);
                if (close < 0) {
                    throw new Unsupported("unclosed repetition");
                }
                String[] bounds = regex.substring(pos + 1, close).split(",", -1);
                try {
                    min = Integer.parseInt(bounds[0].trim());
                    max = bounds.length == 1 ? min : bounds[1].isBlank() ? -1 : Integer.parseInt(bounds[1].trim());
                } catch (NumberFormatException e) {
                    throw new Unsupported("repetition " + regex.substring(pos, close + 1));
                }
                pos = close + 1;
            } else {
                return atom;
            }
            // Lazy and possessive variants match a subset of the same strings
            if (pos < regex.length() && (regex.charAt(pos) == '?' || regex.charAt(pos) == '+')) {
                pos++;
            }
            return repeat(atom, min, max);
        }

        /** Repetitions beyond the prefix length cannot change prefixes of that length. */
        private Set<Item> repeat(Set<Item> atom, int min, int max) {
            int cap = maxLength + 1;
            Set<Item> result = Set.of(Item.EMPTY);
            for (int i = 0; i < Math.min(min, cap); i++) {
                result = concat(result, atom);
            }
            int optional = max < 0 ? cap : Math.min(max - min, cap);
            Set<Item> current = result;
            Set<Item> all = new LinkedHashSet<>(result);
            for (int i = 0; i < optional; i++) {
                current = concat(current, atom);
                if (!all.addAll(current)) {
                    break;
                }
                if (bounded(all) != all) {
                    return ANY;
                }
            }
            return all;
        }

        private Set<Item> atom() {
            char c = regex.charAt(pos);
            switch (c) {
                case '(':
                    return group();
                case '[':
                    return chars(characterClass());
                case '\\':
                    return escape();
                case '.':
                    pos++;
                    return Set.of(new Item("", true));
                case '^':
                case '$':
                    pos++;
                    return Set.of(Item.EMPTY);
                case '*': case '+': case '?': case '{':
                    throw new Unsupported("dangling quantifier");
                default:
                    int cp = regex.codePointAt(pos);
                    pos += Character.charCount(cp);
                    return Set.of(new Item(new String(Character.toChars(cp)), false));
            }
        }

        private Set<Item> group() {
            pos++;
            boolean zeroWidth = false;
            if (regex.startsWith("?:", pos) || regex.startsWith("?>", pos)) {
                pos += 2;
            } else if (regex.startsWith("?=", pos) || regex.startsWith("?!", pos)) {
                pos += 2;
                zeroWidth = true;
            } else if (regex.startsWith("?<=", pos) || regex.startsWith("?<!", pos)) {
                pos += 3;
                zeroWidth = true;
            } else if (regex.startsWith("?<", pos)) {
                int close = regex.indexOf('>', pos);
                if (close < 0) {
                    throw new Unsupported("named group");
                }
                pos = close + 1;
            } else if (pos < regex.length() && regex.charAt(pos) == '?') {
                throw new Unsupported("inline flags");
            }
            if (zeroWidth) {
                skipToGroupEnd();
                return Set.of(Item.EMPTY);
            }
            Set<Item> inner = alternation();
            expect(')');
            return inner;
        }

        /** Lookarounds do not consume characters: their content is skipped, not analyzed. */
        private void skipToGroupEnd() {
            int depth = 1;
            while (pos < regex.length()) {
                char c = regex.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                    continue;
                }
                if (c == '[') {
                    characterClass();
                    continue;
                }
                pos++;
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    return;
                }
            }
            throw new Unsupported("unclosed group");
        }

        private void expect(char c) {
            if (pos >= regex.length() || regex.charAt(pos) != c) {
                throw new Unsupported("expected " + c + " at " + pos);
            }
            pos++;
        }

        private Set<Item> escape() {
            pos++;
            if (pos >= regex.length()) {
                throw new Unsupported("trailing backslash");
            }
            char c = regex.charAt(pos);
            switch (c) {
                case 'b': case 'B': case 'A': case 'z': case 'Z': case 'G':
                    pos++;
                    return Set.of(Item.EMPTY);
                case 's': case 'd':
                    pos++;
                    return chars(predefined(c));
                case 'w': case 'W': case 'S': case 'D': case 'h': case 'H': case 'v': case 'V':
                case 'R': case 'X': case 'N':
                    pos++;
                    return Set.of(new Item("", true));
                case 'p': case 'P':
                    skipProperty();
                    return Set.of(new Item("", true));
                default:
                    return chars(Set.of(escapedChar()));
            }
        }

        /** The character of an escape that stands for one character ({@code \.}, {@code \n}, {@code ·}). */
        private String escapedChar() {
            char c = regex.charAt(pos);
            switch (c) {
                case 'n': pos++; return "\n";
                case 't': pos++; return "\t";
                case 'r': pos++; return "\r";
                case 'f': pos++; return "\f";
                case 'a': pos++; return "\u0007";
                case 'e': pos++; return "\u001b";
                case 'u': {
                    String hex = regex.substring(pos + 1, Math.min(regex.length(), pos + 5));
                    pos += 5;
                    return String.valueOf((char) parseHex(hex));
                }
                case 'x': {
                    if (regex.startsWith("x{", pos)) {
                        int close = regex.indexOf('}', pos);
                        int cp = parseHex(regex.substring(pos + 2, close));
                        pos = close + 1;
                        return new String(Character.toChars(cp));
                    }
                    String hex = regex.substring(pos + 1, Math.min(regex.length(), pos + 3));
                    pos += 3;
                    return String.valueOf((char) parseHex(hex));
                }
                default:
                    if (Character.isLetterOrDigit(c)) {
                        // Backreferences, \Q, \k<name>, \c, octal escapes...
                        throw new Unsupported("escape \\" + c);
                    }
                    pos++;
                    return String.valueOf(c);
            }
        }

        private static int parseHex(String hex) {
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new Unsupported("hex escape " + hex);
            }
        }

        private void skipProperty() {
            pos++;
            if (pos < regex.length() && regex.charAt(pos) == '{') {
                int close = regex.indexOf('}', pos);
                if (close < 0) {
                    throw new Unsupported("property");
                }
                pos = close + 1;
            } else {
                pos++;
            }
        }

        /**
         * The characters of a class, or {@code null} for a class that is negated, uses a
         * predefined class or property, or is too large to enumerate.
         */
        private Set<String> characterClass() {
            pos++;
            boolean negated = pos < regex.length() && regex.charAt(pos) == '^';
            if (negated) {
                pos++;
            }
            Set<String> members = new LinkedHashSet<>();
            boolean enumerable = !negated;
            boolean first = true;
            while (true) {
                if (pos >= regex.length()) {
                    throw new Unsupported("unclosed class");
                }
                char c = regex.charAt(pos);
                if (c == ']' && !first) {
                    pos++;
                    break;
                }
                first = false;
                if (c == '[' || regex.startsWith("&&", pos)) {
                    throw new Unsupported("nested class");
                }
                String from;
                if (c == '\\') {
                    pos++;
                    char e = regex.charAt(pos);
                    if ("wWsSdDhHvV".indexOf(e) >= 0) {
                        pos++;
                        Set<String> predefined = predefined(e);
                        if (predefined == null) {
                            enumerable = false;
                        } else {
                            members.addAll(predefined);
                        }
                        continue;
                    }
                    if (e == 'p' || e == 'P') {
                        skipProperty();
                        enumerable = false;
                        continue;
                    }
                    from = escapedChar();
                } else {
                    int cp = regex.codePointAt(pos);
                    pos += Character.charCount(cp);
                    from = new String(Character.toChars(cp));
                }
                if (pos + 1 < regex.length() && regex.charAt(pos) == '-' && regex.charAt(pos + 1) != ']') {
                    pos++;
                    String to;
                    if (regex.charAt(pos) == '\\') {
                        pos++;
                        to = escapedChar();
                    } else {
                        int cp = regex.codePointAt(pos);
                        pos += Character.charCount(cp);
                        to = new String(Character.toChars(cp));
                    }
                    int start = from.codePointAt(0);
                    int end = to.codePointAt(0);
                    if (end - start >= MAX_CLASS_SIZE) {
                        enumerable = false;
                        continue;
                    }
                    for (int cp = start; cp <= end; cp++) {
                        members.add(new String(Character.toChars(cp)));
                    }
                } else {
                    members.add(from);
                }
            }
            return enumerable && members.size() <= MAX_CLASS_SIZE ? members : null;
        }

        private static Set<Item> chars(Set<String> members) {
            if (members == null) {
                return Set.of(new Item("", true));
            }
            Set<Item> items = new LinkedHashSet<>();
            for (String member : members) {
                items.add(new Item(member, false));
            }
            return items;
        }

        private Set<Item> concat(Set<Item> left, Set<Item> right) {
            Set<Item> result = new LinkedHashSet<>();
            for (Item a : left) {
                if (a.open) {
                    result.add(a);
                    continue;
                }
                for (Item b : right) {
                    String text = a.text + b.text;
                    if (text.length() >= maxLength) {
                        result.add(new Item(text.substring(0, maxLength), true));
                    } else {
                        result.add(new Item(text, b.open));
                    }
                }
                if (bounded(result) != result) {
                    return ANY;
                }
            }
            return result;
        }

        /**
         * The set itself, or "any string" when it is too large to be useful. Always
         * sound: "any string" includes every prefix the set had.
         */
        private Set<Item> bounded(Set<Item> items) {
            if (items.size() > MAX_PREFIXES) {
                overflowed = true;
                return ANY;
            }
            return items;
        }
    }
}
