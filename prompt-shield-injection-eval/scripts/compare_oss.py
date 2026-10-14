#!/usr/bin/env python3
"""Runs open-source prompt-injection detectors over the materialized evaluation corpus.

Input: target/injection-eval/corpus.jsonl and predictions.jsonl, written by
EvaluationMain (run InjectionEvaluationTest first). Output: a markdown table on
stdout and oss-comparison.json next to the corpus.

Detectors (all run locally, no network, no model downloads):
  vigil-yara        deadbits/vigil-llm YARA rules (data/yara), injection rules only:
                    InstructionBypass, SystemInstructions, ContainsReAct, MarkdownExfiltration,
                    ContainsGuidance. Secret/IP/SSH-key rules are left out (not injection).
  rebuff-heuristic  protectai/rebuff detect_pi_heuristics (string similarity against generated
                    "ignore previous instructions" variants), flagged above Rebuff's default 0.75.
                    It takes ~2 s for 60 characters and ~100 s for 1 KB, so it runs on a seeded
                    sample of 150 texts of at most 400 characters; prompt-shield is reported on the
                    same sample for comparison.
  llm-guard-invisible  protectai/llm-guard InvisibleText logic (Cf/Co/Cn characters).
  prompt-shield-ai  PyPI prompt-shield-ai 0.8.1 (unrelated project with the same name), default
                    config, threshold 0.7, with the vault, feedback, canary, PII detector and model-based
                    detectors disabled because they need downloads (d021, d022) or are not about
                    injection (d023).

Model-based detectors (Llama Prompt Guard 2, protectai/deberta-v3-base-prompt-injection-v2, LLM
Guard's PromptInjection) could not be evaluated: huggingface.co is not reachable from the
environment this was written in. Their published numbers are quoted in the report instead.

Usage:
  python3 compare_oss.py <corpus-dir> <clones-dir> [--limit-long N]
"""
import importlib.util
import json
import os
import random
import statistics
import sys
import time
import unicodedata
from collections import defaultdict


def load_jsonl(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def vigil_detector(clones):
    import yara
    rules_dir = os.path.join(clones, "vigil-llm", "data", "yara")
    files = {name: os.path.join(rules_dir, name) for name in
             ["instruction_bypass.yar", "system_instructions.yar", "react.yar", "mdexfil.yar", "guidance.yar"]}
    rules = yara.compile(filepaths=files)
    return lambda text, source: bool(rules.match(data=text.encode("utf-8")))


def rebuff_detector(clones):
    path = os.path.join(clones, "rebuff", "python-sdk", "rebuff", "detect_pi_heuristics.py")
    spec = importlib.util.spec_from_file_location("rebuff_heuristics", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return lambda text, source: module.detect_prompt_injection_using_heuristic_on_input(text) > 0.75


def invisible_detector(clones):
    banned = {"Cf", "Co", "Cn"}

    def detect(text, source):
        if all(ord(c) < 128 for c in text):
            return False
        return any(unicodedata.category(c) in banned for c in text)
    return detect


def psai_detector(clones):
    import logging
    logging.disable(logging.CRITICAL)
    from prompt_shield.engine import PromptShieldEngine
    import tempfile
    disabled = ["d021_vault_similarity", "d022_semantic_classifier", "d023_pii_detection"]
    config = {"prompt_shield": {
        "mode": "block", "threshold": 0.7, "parallel": False,
        "vault": {"enabled": False}, "feedback": {"enabled": False}, "canary": {"enabled": False},
        "detectors": {d: {"enabled": False} for d in disabled},
    }}
    engine = PromptShieldEngine(config_dict=config, data_dir=tempfile.mkdtemp())

    def detect(text, source):
        report = engine.scan(text)
        return str(report.action.value if hasattr(report.action, "value") else report.action).lower() in ("block", "flag")
    return detect


DETECTORS = {
    "vigil-yara": vigil_detector,
    "rebuff-heuristic": rebuff_detector,
    "llm-guard-invisible": invisible_detector,
    "prompt-shield-ai": psai_detector,
}


def metrics(rows):
    tp = sum(1 for r in rows if r["label"] and r["flag"])
    fn = sum(1 for r in rows if r["label"] and not r["flag"])
    fp = sum(1 for r in rows if not r["label"] and r["flag"])
    tn = sum(1 for r in rows if not r["label"] and not r["flag"])
    p = tp / (tp + fp) if tp + fp else float("nan")
    rc = tp / (tp + fn) if tp + fn else float("nan")
    f1 = 2 * p * rc / (p + rc) if tp and p + rc else float("nan")
    fpr = fp / (fp + tn) if fp + tn else float("nan")
    return dict(n=len(rows), tp=tp, fp=fp, tn=tn, fn=fn, precision=p, recall=rc, f1=f1, fpr=fpr)


def pct(x):
    return "–" if x != x else "%.1f%%" % (100 * x)


def main():
    corpus_dir, clones = sys.argv[1], sys.argv[2]
    corpus = load_jsonl(os.path.join(corpus_dir, "corpus.jsonl"))
    java = {p["id"]: p for p in load_jsonl(os.path.join(corpus_dir, "predictions.jsonl"))}
    results = {"prompt-shield (this PR, default)": [
        dict(dataset=s["dataset"], label=s["label"], flag=java[s["id"]]["flagged"], ms=java[s["id"]]["micros"] / 1000)
        for s in corpus]}
    short = [s for s in corpus if len(s["text"]) <= 400]
    sample = random.Random(20241014).sample(short, min(150, len(short)))
    results["prompt-shield (this PR, default) on rebuff sample"] = [
        dict(dataset="rebuff-sample", label=s["label"], flag=java[s["id"]]["flagged"], ms=java[s["id"]]["micros"] / 1000)
        for s in sample]
    for name, factory in DETECTORS.items():
        try:
            detect = factory(clones)
        except Exception as e:  # missing clone or dependency
            print("skipping %s: %s" % (name, e), file=sys.stderr)
            continue
        rows = []
        for s in (sample if name == "rebuff-heuristic" else corpus):
            start = time.perf_counter()
            try:
                flag = bool(detect(s["text"], s["source"]))
            except Exception as e:
                print("%s failed on %s: %s" % (name, s["id"], e), file=sys.stderr)
                flag = False
            dataset = "rebuff-sample" if name == "rebuff-heuristic" else s["dataset"]
            rows.append(dict(dataset=dataset, label=s["label"], flag=flag, ms=(time.perf_counter() - start) * 1000))
        results[name] = rows
        print("done", name, file=sys.stderr)

    datasets = sorted({s["dataset"] for s in corpus} | {"rebuff-sample"})
    out = {}
    print("| Detector | Dataset | n | Precision | Recall | F1 | FPR |")
    print("|---|---|---|---|---|---|---|")
    for name, rows in results.items():
        out[name] = {}
        by = defaultdict(list)
        for r in rows:
            by[r["dataset"]].append(r)
        for d in datasets:
            if not by[d]:
                continue
            m = metrics(by[d])
            out[name][d] = m
            print("| %s | %s | %d | %s | %s | %s | %s |" % (name, d, m["n"], pct(m["precision"]), pct(m["recall"]),
                                                          pct(m["f1"]), pct(m["fpr"])))
    print()
    print("| Detector | median ms | p95 ms | max ms |")
    print("|---|---|---|---|")
    for name, rows in results.items():
        ms = sorted(r["ms"] for r in rows)
        out[name]["_latency_ms"] = dict(median=statistics.median(ms), p95=ms[int(0.95 * len(ms)) - 1], max=ms[-1])
        print("| %s | %.3f | %.3f | %.1f |" % (name, statistics.median(ms), ms[int(0.95 * len(ms)) - 1], ms[-1]))
    with open(os.path.join(corpus_dir, "oss-comparison.json"), "w") as f:
        json.dump(out, f, indent=2, default=str)


if __name__ == "__main__":
    main()
