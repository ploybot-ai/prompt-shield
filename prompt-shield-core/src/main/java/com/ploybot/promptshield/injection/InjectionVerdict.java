package com.ploybot.promptshield.injection;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The outcome of scanning one text.
 *
 * <p>A verdict is evidence, not a guarantee: a text below the threshold may still be
 * an injection the rules do not know, and one above it may be a legitimate text that
 * talks about injections. Use it as one layer next to privilege separation and human
 * confirmation of sensitive tool calls.
 *
 * @param score     combined score between 0 and 1
 * @param threshold the threshold that applied to this source
 * @param source    where the text came from
 * @param signals   evidence, strongest first
 */
public record InjectionVerdict(double score, double threshold, ContentSource source, List<InjectionSignal> signals) {

    public InjectionVerdict {
        signals = List.copyOf(signals);
    }

    public static InjectionVerdict clean(ContentSource source, double threshold) {
        return new InjectionVerdict(0, threshold, source, List.of());
    }

    /** Whether the score reached the threshold. */
    public boolean isInjection() {
        return score >= threshold;
    }

    public Set<InjectionCategory> categories() {
        Set<InjectionCategory> categories = EnumSet.noneOf(InjectionCategory.class);
        signals.forEach(signal -> categories.add(signal.category()));
        return categories;
    }

    /** A one-line summary without the matched text, safe to log. */
    public String summary() {
        StringBuilder text = new StringBuilder()
                .append("score=").append(String.format(java.util.Locale.ROOT, "%.2f", score))
                .append(" threshold=").append(String.format(java.util.Locale.ROOT, "%.2f", threshold))
                .append(" source=").append(source)
                .append(" rules=[");
        for (int i = 0; i < signals.size(); i++) {
            InjectionSignal signal = signals.get(i);
            text.append(i == 0 ? "" : ", ").append(signal.ruleId()).append('@').append(signal.layer());
        }
        return text.append(']').toString();
    }
}
