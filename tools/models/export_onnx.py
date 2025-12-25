#!/usr/bin/env python3
"""
Export T5 GEC model to ONNX format.

ONNX is a more portable format that can be used on various platforms.
For Android, the ONNX model can be converted to TFLite using ai.onnxruntime.

Usage:
    python export_onnx.py --language en

Note: Requires Python 3.11 or 3.12 (ONNX Runtime not yet available for 3.14)
      pip install onnx onnxruntime optimum[exporters]
"""

import argparse
import os
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description="Export T5 model to ONNX format")
    parser.add_argument("--language", "-l", default="en", help="Language code")
    parser.add_argument(
        "--input-dir",
        "-i",
        type=Path,
        default=Path(__file__).parent / "downloaded",
        help="Directory containing downloaded model",
    )
    parser.add_argument(
        "--output-dir",
        "-o",
        type=Path,
        default=Path(__file__).parent / "output",
        help="Output directory for ONNX model",
    )

    args = parser.parse_args()

    model_path = args.input_dir / f"t5_gec_{args.language}" / "model"

    if not model_path.exists():
        print(f"Error: Model not found at {model_path}")
        print(f"First download the model using: python download_grammar_model.py --language {args.language}")
        sys.exit(1)

    args.output_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 50)
    print("T5 GEC Model ONNX Export")
    print("=" * 50)

    try:
        from optimum.onnxruntime import ORTModelForSeq2SeqLM
        from transformers import T5Tokenizer
    except ImportError:
        print("\nError: Required libraries not installed.")
        print("Install with: pip install onnx onnxruntime optimum[exporters]")
        print("\nNote: Requires Python 3.11 or 3.12 (not 3.14)")
        sys.exit(1)

    print(f"\nLoading model from: {model_path}")

    # Export to ONNX using optimum
    print("\nExporting to ONNX format...")
    onnx_model = ORTModelForSeq2SeqLM.from_pretrained(
        str(model_path),
        export=True,
    )

    # Save ONNX model
    onnx_path = args.output_dir / f"grammar_{args.language}_onnx"
    onnx_model.save_pretrained(onnx_path)
    print(f"ONNX model saved to: {onnx_path}")

    # Also save tokenizer
    tokenizer = T5Tokenizer.from_pretrained(str(model_path.parent / "tokenizer"))
    tokenizer.save_pretrained(onnx_path)
    print(f"Tokenizer saved to: {onnx_path}")

    # Get model size
    total_size = sum(f.stat().st_size for f in onnx_path.rglob("*") if f.is_file())
    print(f"\nTotal size: {total_size / 1024 / 1024:.1f} MB")

    print("\n" + "=" * 50)
    print("Export Complete!")
    print("=" * 50)
    print(f"\nTo use in Android, add ONNX Runtime dependency:")
    print('  implementation "com.microsoft.onnxruntime:onnxruntime-android:1.16.0"')


if __name__ == "__main__":
    main()
