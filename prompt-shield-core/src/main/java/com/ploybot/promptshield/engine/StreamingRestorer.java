package com.ploybot.promptshield.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Restores obfuscation tags in a streamed response where a tag may arrive split
 * across chunks. Complete tags are restored and emitted immediately; a trailing
 * fragment that could still grow into a tag is withheld until the next chunk (or
 * {@link #flush()}) decides it.
 *
 * <p>One instance per stream; stateful and not thread-safe.
 */
public final class StreamingRestorer {

    private final ObfuscationScope scope;
    private final Pattern tagPattern;
    private final String tagOpen;
    private final StringBuilder carry = new StringBuilder();

    StreamingRestorer(ObfuscationScope scope) {
        this.scope = scope;
        this.tagPattern = scope.getEngine().getTagPattern();
        this.tagOpen = scope.getEngine().getConfig().getTagOpen();
    }

    /**
     * Processes the next streamed text fragment and returns the text that is safe to
     * emit now, with any complete tags restored. May return an empty string when the
     * whole fragment is withheld as a potential partial tag.
     */
    public String process(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        carry.append(chunk);
        String buffered = carry.toString();
        int hold = holdIndex(buffered);
        String emit = buffered.substring(0, hold);
        carry.setLength(0);
        carry.append(buffered, hold, buffered.length());
        return scope.restore(emit);
    }

    /**
     * Returns the remaining withheld text at end of stream, restored best-effort.
     */
    public String flush() {
        String rest = carry.toString();
        carry.setLength(0);
        return scope.restore(rest);
    }

    public boolean hasPendingText() {
        return carry.length() > 0;
    }

    /**
     * Index from which the text must be withheld because it may be the beginning of a
     * tag that is not complete yet, or the text length if everything can be emitted.
     */
    private int holdIndex(String text) {
        Matcher matcher = tagPattern.matcher(text);
        int lastCompleteEnd = 0;
        while (matcher.find()) {
            lastCompleteEnd = matcher.end();
        }

        int idx = text.indexOf(tagOpen, lastCompleteEnd);
        while (idx >= 0) {
            Matcher partial = tagPattern.matcher(text.substring(idx));
            // hitEnd: the match failed because input ran out, so more chunks could complete it
            if (!partial.matches() && partial.hitEnd()) {
                return idx;
            }
            idx = text.indexOf(tagOpen, idx + 1);
        }

        // The text may end mid-way through a multi-character tagOpen delimiter
        for (int k = tagOpen.length() - 1; k >= 1; k--) {
            if (text.length() >= k && lastCompleteEnd <= text.length() - k
                    && text.endsWith(tagOpen.substring(0, k))) {
                return text.length() - k;
            }
        }

        return text.length();
    }
}
