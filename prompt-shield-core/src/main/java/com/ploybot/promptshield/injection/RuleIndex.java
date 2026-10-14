package com.ploybot.promptshield.injection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The literals of a rule set in one trie, walked once from every position of a view:
 * <ul>
 *   <li>the anchors of each rule ({@link InjectionRule#anchors()}), which say whether
 *       the rule can match the view at all, and</li>
 *   <li>the start prefixes of each rule ({@link RegexPrefixes}), which say where a
 *       match can start.</li>
 * </ul>
 * Before, each rule checked each of its anchors with {@link String#contains} and then
 * tried its regex at every position; now one pass finds both and the regex is only
 * tried at the candidate positions. Matching is unchanged: a rule without prefixes is
 * still searched everywhere.
 */
final class RuleIndex {

    private final Node root;
    /** Anchor ids of each rule; empty when the rule has no anchors. */
    private final int[][] ruleAnchors;
    private final boolean[] hasPrefixes;
    private final int anchorCount;

    RuleIndex(List<InjectionRule> rules) {
        Builder builder = new Builder();
        ruleAnchors = new int[rules.size()][];
        hasPrefixes = new boolean[rules.size()];
        Map<String, Integer> anchorIds = new HashMap<>();
        for (int r = 0; r < rules.size(); r++) {
            InjectionRule rule = rules.get(r);
            List<String> anchors = rule.anchors();
            ruleAnchors[r] = new int[anchors.size()];
            for (int a = 0; a < anchors.size(); a++) {
                String anchor = anchors.get(a);
                boolean added = !anchorIds.containsKey(anchor);
                int id = anchorIds.computeIfAbsent(anchor, key -> anchorIds.size());
                ruleAnchors[r][a] = id;
                if (added && !anchor.isEmpty()) {
                    builder.add(anchor).anchors.add(id);
                }
                if (anchor.isEmpty()) {
                    // contains("") is always true: such a rule is never filtered
                    ruleAnchors[r] = new int[0];
                    break;
                }
            }
            Set<String> prefixes = rule.raw() ? null : RegexPrefixes.of(rule.pattern());
            if (prefixes != null) {
                hasPrefixes[r] = true;
                for (String prefix : prefixes) {
                    builder.add(prefix).rules.add(r);
                }
            }
        }
        anchorCount = anchorIds.size();
        root = builder.root.freeze(0);
    }

    /** Whether the rule's regex is only tried at the candidate positions of a {@link Scan}. */
    boolean hasPrefixes(int rule) {
        return hasPrefixes[rule];
    }

    /** Walks the trie from every position of the text. */
    Scan scan(String text) {
        Scan scan = new Scan(anchorCount, hasPrefixes.length);
        int n = text.length();
        for (int p = 0; p < n; p++) {
            Node node = root.child(text.charAt(p));
            int depth = 1;
            while (node != null) {
                for (int anchor : node.anchors) {
                    scan.anchors[anchor] = true;
                }
                for (int rule : node.rules) {
                    scan.add(rule, p);
                }
                if (p + depth >= n) {
                    break;
                }
                node = node.child(text.charAt(p + depth++));
            }
        }
        return scan;
    }

    /** The anchors found in a view and, for each rule, the positions where a match can start. */
    final class Scan {

        private final boolean[] anchors;
        private final int[][] candidates;
        private final int[] sizes;

        private Scan(int anchorCount, int ruleCount) {
            this.anchors = new boolean[anchorCount];
            this.candidates = new int[ruleCount][];
            this.sizes = new int[ruleCount];
        }

        private void add(int rule, int position) {
            int size = sizes[rule];
            int[] list = candidates[rule];
            if (list == null) {
                list = candidates[rule] = new int[8];
            } else if (list[size - 1] == position) {
                // Several prefixes of the rule start here
                return;
            } else if (size == list.length) {
                list = candidates[rule] = Arrays.copyOf(list, size * 2);
            }
            list[size] = position;
            sizes[rule] = size + 1;
        }

        /** Same result as {@link InjectionRule#mayMatch} on the scanned text. */
        boolean mayMatch(int rule) {
            int[] ids = ruleAnchors[rule];
            if (ids.length == 0) {
                return true;
            }
            for (int id : ids) {
                if (anchors[id]) {
                    return true;
                }
            }
            return false;
        }

        /** Candidate start positions of the rule, in increasing order. */
        int[] candidates(int rule) {
            return candidates[rule] == null ? new int[0] : candidates[rule];
        }

        int candidateCount(int rule) {
            return sizes[rule];
        }
    }

    // ------------------------------------------------------------------ trie

    private static final int[] NONE = new int[0];

    private static final class Node {
        /** Children by ASCII character, in the first levels (visited at almost every position). */
        private Node[] ascii;
        private char[] keys = new char[0];
        private Node[] children = new Node[0];
        private int[] anchors = NONE;
        private int[] rules = NONE;

        Node child(char c) {
            if (ascii != null && c < 128) {
                return ascii[c];
            }
            int i = Arrays.binarySearch(keys, c);
            return i >= 0 ? children[i] : null;
        }
    }

    private static final class Builder {

        private final MutableNode root = new MutableNode();

        MutableNode add(String literal) {
            MutableNode node = root;
            for (int i = 0; i < literal.length(); i++) {
                node = node.children.computeIfAbsent(literal.charAt(i), c -> new MutableNode());
            }
            return node;
        }
    }

    private static final class MutableNode {
        final TreeMap<Character, MutableNode> children = new TreeMap<>();
        final List<Integer> anchors = new ArrayList<>();
        final List<Integer> rules = new ArrayList<>();

        /** Nodes of the first two levels, visited at almost every position, also get a table for ASCII. */
        Node freeze(int depth) {
            Node node = new Node();
            node.anchors = anchors.isEmpty() ? NONE : anchors.stream().mapToInt(Integer::intValue).toArray();
            node.rules = rules.isEmpty() ? NONE : rules.stream().mapToInt(Integer::intValue).distinct().toArray();
            node.keys = new char[children.size()];
            node.children = new Node[children.size()];
            int i = 0;
            for (Map.Entry<Character, MutableNode> child : children.entrySet()) {
                node.keys[i] = child.getKey();
                node.children[i++] = child.getValue().freeze(depth + 1);
            }
            if (depth <= 1) {
                node.ascii = new Node[128];
                for (int k = 0; k < node.keys.length; k++) {
                    if (node.keys[k] < 128) {
                        node.ascii[node.keys[k]] = node.children[k];
                    }
                }
            }
            return node;
        }
    }
}
