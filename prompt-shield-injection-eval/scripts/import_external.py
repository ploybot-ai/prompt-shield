#!/usr/bin/env python3
"""Imports public, third-party datasets as the held-out evaluation sets.

None of these texts were written by the authors of the rules, which makes them a
fairer measure than the development seeds. Clone the sources first (shallow clones
are enough):

    git clone --depth 1 https://github.com/microsoft/BIPIA            (MIT)
    git clone --depth 1 https://github.com/protectai/llm-guard        (MIT)
    git clone --depth 1 https://github.com/lakeraai/pint-benchmark    (MIT)
    pip download prompt-shield-ai==0.8.1 --no-deps                    (Apache-2.0)

Usage:
    python3 import_external.py <dir-with-clones> <prompt_shield_ai wheel>

Output: ../src/main/resources/injection-corpus/external/*.jsonl

BIPIA: indirect injections. Each attack instruction of the text and code test splits
is inserted at the start, middle (sentence boundary) or end of a real e-mail, table
or Stack Overflow answer, as BIPIA's own insert_* functions do. The clean contexts
are the benign set. A seeded RNG makes the output reproducible.
"""
import ast
import json
import os
import random
import re
import sys
import zipfile

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "injection-corpus", "external")
SEED = 20240613


def write(name, rows):
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=True) + "\n")
    print(name, len(rows), "attacks:", sum(r["label"] for r in rows))


def sentence_starts(text):
    """Offsets where a sentence starts (a light stand-in for nltk's Punkt)."""
    starts = [0]
    for m in re.finditer(r"[.!?]\s+(?=[A-Z0-9\"'(\[])|\n+", text):
        if m.end() < len(text):
            starts.append(m.end())
    return starts


def insert(context, attack, position, rng):
    if position == "start":
        return attack + "\n" + context
    if position == "end":
        return context + "\n" + attack
    start = rng.choice(sentence_starts(context))
    return context[:start] + "\n" + attack + "\n" + context[start:]


def bipia(root, split="test", name="bipia.jsonl", max_contexts=100):
    """split="train" gives the tuning set (bipia-train); "test" is held out."""
    rng = random.Random(SEED)
    bench = os.path.join(root, "BIPIA", "benchmark")
    text_attacks = [(cat, a) for cat, items in json.load(open(os.path.join(bench, "text_attack_%s.json" % split))).items() for a in items]
    code_attacks = [(cat, a) for cat, items in json.load(open(os.path.join(bench, "code_attack_%s.json" % split))).items() for a in items]

    def contexts(task):
        rows = [json.loads(line) for line in open(os.path.join(bench, task, "%s.jsonl" % split))][:max_contexts]
        out = []
        for r in rows:
            c = r["context"]
            out.append("\n".join(c) if isinstance(c, list) else c)
        return out

    email, table, code = contexts("email"), contexts("table"), contexts("code")
    rows = []
    positions = ["start", "middle", "end"]
    n = 0
    for task, ctxs, attacks in (("email", email, text_attacks), ("table", table, text_attacks), ("code", code, code_attacks)):
        for i, (cat, attack) in enumerate(attacks):
            ctx = ctxs[i % len(ctxs)]
            pos = positions[i % 3]
            n += 1
            rows.append({"id": "%s-a-%04d" % (name[:-6], n), "label": 1, "source": "document", "category": "bipia-" + task,
                         "technique": cat.lower().replace(" ", "-").replace("&", "and"), "lang": "en",
                         "position": pos, "text": insert(ctx, attack, pos, rng)})
        for j, ctx in enumerate(ctxs):
            n += 1
            rows.append({"id": "%s-b-%04d" % (name[:-6], n), "label": 0, "source": "document", "category": "bipia-" + task,
                         "technique": "clean-context", "lang": "en", "text": ctx})
    write(name, rows)


def llm_guard(root):
    path = os.path.join(root, "llm-guard", "tests", "input_scanners", "test_prompt_injection.py")
    tree = ast.parse(open(path).read())
    rows = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Tuple) and len(node.elts) == 4 and isinstance(node.elts[2], ast.Constant) \
                and isinstance(node.elts[2].value, bool):
            try:
                prompt = ast.literal_eval(node.elts[1])
            except ValueError:
                continue
            valid = node.elts[2].value
            rows.append({"id": "llmguard-%03d" % (len(rows) + 1), "label": 0 if valid else 1, "source": "user",
                         "category": "llm-guard-tests", "technique": "plain", "lang": "en", "text": prompt})
    write("llm-guard-tests.jsonl", rows)


def prompt_shield_ai(wheel):
    data = json.loads(zipfile.ZipFile(wheel).read("prompt_shield/benchmarks/sample_data.json"))
    rows = [{"id": "psai-%03d" % i, "label": 1 if s["is_injection"] else 0, "source": "user",
             "category": "prompt-shield-ai-sample", "technique": "plain", "lang": "en", "text": s["text"]}
            for i, s in enumerate(data["samples"], 1)]
    write("prompt-shield-ai-sample.jsonl", rows)


def pint_example(root):
    import yaml  # pyyaml
    items = yaml.safe_load(open(os.path.join(root, "pint-benchmark", "benchmark", "data", "example-dataset.yaml")))
    rows = [{"id": "pint-%03d" % i, "label": 1 if it["label"] else 0, "source": "user", "category": "pint-example",
             "technique": it.get("category", "plain"), "lang": "en", "text": it["text"]}
            for i, it in enumerate(items, 1)]
    write("pint-example.jsonl", rows)


if __name__ == "__main__":
    clones, wheel = sys.argv[1], sys.argv[2]
    bipia(clones)
    bipia(clones, split="train", name="bipia-train.jsonl")
    llm_guard(clones)
    prompt_shield_ai(wheel)
    pint_example(clones)
