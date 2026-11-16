"""Trains the category model from dataset.csv and writes it into core:ml's assets (plan 4.2).

The model is multinomial Naive Bayes over whole words plus 3-letter pieces of words. The pieces let it
cope with spellings it never saw ("swigy", "petrool"). Why not a TFLite network: the LiteRT runtime alone
adds more to the download than this whole model, and on a few thousand short notes Naive Bayes is as
accurate. The model file is plain text (word counts per category); CategoryClassifier.kt reads it.

The text clean-up and the features below MUST match CategoryClassifier.kt exactly. Change both together,
then bump MODEL_VERSION.

Run:  python ml/training/train.py      (no packages needed)
"""

import csv
import math
import random
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

MODEL_VERSION = 1
ROOT = Path(__file__).resolve().parents[2]
DATA = Path(__file__).with_name("dataset.csv")
OUT = ROOT / "core/ml/src/main/assets/category_model.txt"
ALPHA = 0.5  # smoothing
MIN_COUNT = 2  # pieces seen fewer times are dropped (keeps the file small)
NGRAM = 3
MIN_WORD = 2

NUKTA, CHANDRABINDU, ANUSVARA = "़", "ँ", "ं"


def norm(text):
    t = unicodedata.normalize("NFD", text.lower())
    t = t.replace(NUKTA, "").replace(CHANDRABINDU, ANUSVARA)
    return unicodedata.normalize("NFC", t)


def words(text):
    out, cur = [], []
    for ch in norm(text):
        if unicodedata.category(ch)[0] in "LM":
            cur.append(ch)
        else:
            if cur:
                out.append("".join(cur))
            cur = []
    if cur:
        out.append("".join(cur))
    return [w for w in out if len(w) >= MIN_WORD]


def features(text):
    feats = []
    for w in words(text):
        feats.append("w:" + w)
        padded = "<" + w + ">"
        for i in range(len(padded) - NGRAM + 1):
            feats.append("c:" + padded[i : i + NGRAM])
    return feats


def train(rows):
    labels = sorted({label for _, label in rows})
    docs = Counter(label for _, label in rows)
    counts = defaultdict(Counter)  # feature -> label -> count
    for text, label in rows:
        for f in features(text):
            counts[f][label] += 1
    counts = {f: c for f, c in counts.items() if sum(c.values()) >= MIN_COUNT or f.startswith("w:")}
    return labels, docs, counts


class Model:
    def __init__(self, labels, docs, counts):
        self.labels = labels
        n = sum(docs.values())
        self.prior = {l: math.log(docs[l] / n) for l in labels}
        totals = Counter()
        for c in counts.values():
            for l, k in c.items():
                totals[l] += k
        v = len(counts)
        self.denom = {l: totals[l] + ALPHA * v for l in labels}
        self.counts = counts

    def scores(self, text):
        s = dict(self.prior)
        for f in features(text):
            c = self.counts.get(f)
            if c is None:
                continue
            for l in self.labels:
                s[l] += math.log((c.get(l, 0) + ALPHA) / self.denom[l])
        top = max(s.values())
        z = sum(math.exp(v - top) for v in s.values())
        return {l: math.exp(v - top) / z for l, v in s.items()}

    def predict(self, text):
        p = self.scores(text)
        best = max(p, key=p.get)
        return best, p[best]


def write(labels, docs, counts):
    index = {l: i for i, l in enumerate(labels)}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", encoding="utf-8", newline="\n") as f:
        f.write(f"# Kinly category model v{MODEL_VERSION}. Made by ml/training/train.py; do not edit by hand.\n")
        f.write(f"v\t{MODEL_VERSION}\talpha\t{ALPHA}\tngram\t{NGRAM}\tminword\t{MIN_WORD}\n")
        f.write("labels\t" + "\t".join(labels) + "\n")
        f.write("docs\t" + "\t".join(str(docs[l]) for l in labels) + "\n")
        for feat in sorted(counts):
            pairs = " ".join(f"{index[l]}:{k}" for l, k in sorted(counts[feat].items(), key=lambda x: index[x[0]]))
            f.write(f"{feat}\t{pairs}\n")


def main():
    with DATA.open(encoding="utf-8") as f:
        rows = [(r["text"], r["category"]) for r in csv.DictReader(f)]
    rng = random.Random(7)
    shuffled = rows[:]
    rng.shuffle(shuffled)
    cut = len(shuffled) * 8 // 10
    model = Model(*train(shuffled[:cut]))
    test = shuffled[cut:]
    right = sum(model.predict(t)[0] == l for t, l in test)
    print(f"held-out accuracy: {right}/{len(test)} = {right / len(test):.1%}  (synthetic data, so optimistic)")

    labels, docs, counts = train(rows)
    write(labels, docs, counts)
    full = Model(labels, docs, counts)
    for probe in ["swigy dinner", "dominos", "jio recharge", "amul", "petrool", "ramesh", "something odd",
                  "netflix", "lenskart", "urban company", "दूध", "bescom"]:
        label, p = full.predict(probe)
        print(f"  {probe!r:20} -> {label} ({p:.2f})")
    print(f"{len(counts)} features, {OUT.stat().st_size / 1024:.0f} KB -> {OUT}")


if __name__ == "__main__":
    main()
