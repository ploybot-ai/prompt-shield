package com.ploybot.promptshield.injection.eval;

import com.ploybot.promptshield.injection.InjectionDetector;
import com.ploybot.promptshield.injection.InjectionVerdict;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Runs a detector over labelled samples and computes quality and cost metrics.
 *
 * <p>Latency is measured per call with {@link System#nanoTime()} after a warm-up
 * pass, single-threaded; allocation per call uses the HotSpot per-thread allocation
 * counter when available. Numbers depend on the machine: compare runs made on the
 * same host.
 */
public final class Evaluator {

    /** The outcome of one sample. */
    public record Prediction(Sample sample, boolean flagged, double score, long nanos, long allocatedBytes,
                             InjectionVerdict verdict) {
        public boolean correct() {
            return flagged == sample.attack();
        }
    }

    /** Confusion matrix and derived metrics. */
    public record Metrics(int tp, int fp, int tn, int fn) {

        public int total() {
            return tp + fp + tn + fn;
        }

        public double precision() {
            return tp + fp == 0 ? Double.NaN : (double) tp / (tp + fp);
        }

        public double recall() {
            return tp + fn == 0 ? Double.NaN : (double) tp / (tp + fn);
        }

        public double f1() {
            double p = precision();
            double r = recall();
            return Double.isNaN(p) || Double.isNaN(r) || p + r == 0 ? Double.NaN : 2 * p * r / (p + r);
        }

        public double falsePositiveRate() {
            return fp + tn == 0 ? Double.NaN : (double) fp / (fp + tn);
        }

        static Metrics of(List<Prediction> predictions) {
            int tp = 0;
            int fp = 0;
            int tn = 0;
            int fn = 0;
            for (Prediction p : predictions) {
                if (p.sample().attack()) {
                    if (p.flagged()) tp++; else fn++;
                } else {
                    if (p.flagged()) fp++; else tn++;
                }
            }
            return new Metrics(tp, fp, tn, fn);
        }
    }

    /** Latency percentiles in microseconds and mean allocation in bytes. */
    public record Cost(double p50Micros, double p95Micros, double p99Micros, double maxMicros, double meanMicros,
                       double meanAllocatedBytes, double throughputCharsPerMs) {

        static Cost of(List<Prediction> predictions) {
            if (predictions.isEmpty()) {
                return new Cost(0, 0, 0, 0, 0, 0, 0);
            }
            long[] nanos = predictions.stream().mapToLong(Prediction::nanos).sorted().toArray();
            double mean = Arrays.stream(nanos).average().orElse(0) / 1000.0;
            double alloc = predictions.stream().mapToLong(Prediction::allocatedBytes).average().orElse(0);
            long chars = predictions.stream().mapToLong(p -> p.sample().text().length()).sum();
            long total = Arrays.stream(nanos).sum();
            return new Cost(percentile(nanos, 0.50), percentile(nanos, 0.95), percentile(nanos, 0.99),
                    nanos[nanos.length - 1] / 1000.0, mean, alloc, total == 0 ? 0 : chars / (total / 1e6));
        }

        private static double percentile(long[] sorted, double q) {
            int index = (int) Math.ceil(q * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))] / 1000.0;
        }
    }

    private final InjectionDetector detector;

    public Evaluator(InjectionDetector detector) {
        this.detector = detector;
    }

    public List<Prediction> run(List<Sample> samples, int warmupRounds) {
        for (int round = 0; round < warmupRounds; round++) {
            for (Sample sample : samples) {
                detector.analyze(sample.text(), sample.source());
            }
        }
        AllocationCounter allocation = AllocationCounter.create();
        List<Prediction> predictions = new ArrayList<>(samples.size());
        for (Sample sample : samples) {
            long allocatedBefore = allocation.current();
            long start = System.nanoTime();
            InjectionVerdict verdict = detector.analyze(sample.text(), sample.source());
            long nanos = System.nanoTime() - start;
            long allocated = allocation.current() - allocatedBefore;
            predictions.add(new Prediction(sample, verdict.isInjection(), verdict.score(), nanos, allocated, verdict));
        }
        return predictions;
    }

    public static Metrics metrics(List<Prediction> predictions) {
        return Metrics.of(predictions);
    }

    public static Cost cost(List<Prediction> predictions) {
        return Cost.of(predictions);
    }

    public static <K extends Comparable<K>> Map<K, Metrics> metricsBy(List<Prediction> predictions,
                                                                       Function<Prediction, K> key) {
        Map<K, List<Prediction>> groups = new TreeMap<>();
        for (Prediction p : predictions) {
            groups.computeIfAbsent(key.apply(p), k -> new ArrayList<>()).add(p);
        }
        Map<K, Metrics> out = new LinkedHashMap<>();
        groups.forEach((k, v) -> out.put(k, Metrics.of(v)));
        return out;
    }

    /** Per-thread allocated bytes (HotSpot); returns 0 elsewhere. */
    interface AllocationCounter {
        long current();

        static AllocationCounter create() {
            var bean = ManagementFactory.getThreadMXBean();
            if (bean instanceof com.sun.management.ThreadMXBean hotspot && hotspot.isThreadAllocatedMemorySupported()) {
                hotspot.setThreadAllocatedMemoryEnabled(true);
                long thread = Thread.currentThread().getId();
                return () -> hotspot.getThreadAllocatedBytes(thread);
            }
            return () -> 0;
        }
    }
}
