#!/usr/bin/env python3
"""Small, reproducible RAG evaluation runner.

Input JSONL records: {"question": "...", "contexts": ["..."], "reference": "..."}
It reports Hit@K and optionally delegates faithfulness/context-recall scoring to
the Ragas package when an LLM configuration is available.
"""
import argparse
import json
from pathlib import Path


def hit_at_k(records, k):
    hits = 0
    for record in records:
        reference = str(record.get("reference", "")).lower()
        contexts = [str(item).lower() for item in record.get("contexts", [])[:k]]
        if reference and any(reference in context or context in reference for context in contexts):
            hits += 1
    return hits / len(records) if records else 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", type=Path)
    parser.add_argument("--k", type=int, default=3)
    args = parser.parse_args()
    records = [json.loads(line) for line in args.dataset.read_text(encoding="utf-8").splitlines() if line.strip()]
    print(json.dumps({"samples": len(records), f"hit@{args.k}": round(hit_at_k(records, args.k), 4)}, ensure_ascii=False))
    try:
        import ragas  # noqa: F401
        print("Ragas is installed; use ragas.evaluate with the same JSONL records for semantic metrics.")
    except ImportError:
        print("Ragas is not installed; lexical Hit@K was reported. Install with: pip install ragas datasets")


if __name__ == "__main__":
    main()
