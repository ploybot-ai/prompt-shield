package com.ploybot.promptshield.injection.eval;

import com.ploybot.promptshield.injection.HeuristicInjectionDetector;
import com.ploybot.promptshield.injection.InjectionRule;
import com.ploybot.promptshield.injection.InjectionVerdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anchors are a pure optimization: a rule with a wrong anchor list would silently
 * stop matching. Every sample of the corpus must get exactly the same score with and
 * without them.
 */
class InjectionRuleAnchorsTest {

    @Test
    void anchorsNeverChangeAVerdict() {
        HeuristicInjectionDetector withAnchors = new HeuristicInjectionDetector();
        List<InjectionRule> unanchored = withAnchors.getRules().stream()
                .map(rule -> rule.withAnchors(List.of()))
                .toList();
        HeuristicInjectionDetector withoutAnchors = HeuristicInjectionDetector.builder().rules(unanchored).build();

        List<String> differences = new ArrayList<>();
        for (Sample sample : Corpus.all(EvaluationMain.SEED)) {
            InjectionVerdict fast = withAnchors.analyze(sample.text(), sample.source());
            InjectionVerdict slow = withoutAnchors.analyze(sample.text(), sample.source());
            if (Math.abs(fast.score() - slow.score()) > 1e-9) {
                differences.add(sample.id() + ": " + fast.summary() + " vs " + slow.summary());
            }
        }
        assertTrue(differences.isEmpty(), String.join("\n", differences));
    }

    @Test
    void everyBuiltInRuleExceptRawOnesHasAnchors() {
        for (InjectionRule rule : new HeuristicInjectionDetector().getRules()) {
            assertEquals(rule.raw(), rule.anchors().isEmpty(), rule.id());
        }
    }
}
