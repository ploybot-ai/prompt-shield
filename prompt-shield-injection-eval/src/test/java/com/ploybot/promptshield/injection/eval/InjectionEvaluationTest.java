package com.ploybot.promptshield.injection.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full evaluation (report in target/injection-eval) and guards against
 * regressions. The floors are set just below the measured values so that a change
 * that loses detections or adds false positives fails the build; they are not a
 * statement that the detector is secure.
 */
class InjectionEvaluationTest {

    @Test
    void evaluationMeetsRegressionFloors() throws Exception {
        EvaluationMain.Result result = EvaluationMain.run(Path.of("target", "injection-eval"));
        Map<String, Evaluator.Metrics> byDataset = result.metrics().get("heuristic (default)");

        Evaluator.Metrics dev = byDataset.get(Corpus.DEV);
        Evaluator.Metrics mutated = byDataset.get(Corpus.DEV_MUTATED);
        System.out.println("dev " + dev + " mutated " + mutated);

        assertTrue(dev.recall() >= RegressionFloors.DEV_RECALL, "dev recall " + dev.recall());
        assertTrue(dev.falsePositiveRate() <= RegressionFloors.DEV_FPR, "dev FPR " + dev.falsePositiveRate());
        assertTrue(mutated.recall() >= RegressionFloors.MUTATED_RECALL, "mutated recall " + mutated.recall());
        assertTrue(mutated.falsePositiveRate() <= RegressionFloors.MUTATED_FPR, "mutated FPR " + mutated.falsePositiveRate());
        for (String external : Corpus.EXTERNAL) {
            Evaluator.Metrics m = byDataset.get(external);
            assertTrue(m.falsePositiveRate() <= RegressionFloors.EXTERNAL_FPR, external + " FPR " + m.falsePositiveRate());
        }
    }
}
