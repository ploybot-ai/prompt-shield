package com.ploybot.promptshield.injection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs several detectors and keeps the most severe verdict: the text is flagged as
 * soon as one of them flags it. Signals of every detector are reported.
 */
public class CompositeInjectionDetector implements InjectionDetector {

    private final List<InjectionDetector> detectors;

    public CompositeInjectionDetector(List<InjectionDetector> detectors) {
        if (detectors == null || detectors.isEmpty()) {
            throw new IllegalArgumentException("at least one detector is required");
        }
        this.detectors = List.copyOf(detectors);
    }

    @Override
    public InjectionVerdict analyze(String text, ContentSource source) {
        InjectionVerdict worst = null;
        List<InjectionSignal> signals = new ArrayList<>();
        for (InjectionDetector detector : detectors) {
            InjectionVerdict verdict = detector.analyze(text, source);
            signals.addAll(verdict.signals());
            if (worst == null || margin(verdict) > margin(worst)) {
                worst = verdict;
            }
        }
        signals.sort(Comparator.comparingDouble(InjectionSignal::weight).reversed());
        return new InjectionVerdict(worst.score(), worst.threshold(), source, signals);
    }

    /** How far above (or below) its own threshold a verdict is. */
    private static double margin(InjectionVerdict verdict) {
        return verdict.score() - verdict.threshold();
    }
}
