package com.ploybot.promptshield.injection.eval;

/** Minimum quality accepted by {@link InjectionEvaluationTest}; see its description. */
final class RegressionFloors {

    static final double DEV_RECALL = 0.98;
    static final double DEV_FPR = 0.05;
    static final double MUTATED_RECALL = 0.97;
    static final double MUTATED_FPR = 0.07;
    static final double EXTERNAL_FPR = 0.02;

    private RegressionFloors() {
    }
}
