#!/usr/bin/env python3
"""
Download T5-Small GEC model from HuggingFace.

This script downloads the Unbabel/gec-t5_small model which is a
pre-trained T5 model fine-tuned for grammatical error correction.

Usage:
    python download_grammar_model.py [--language LANG]

Arguments:
    --language  Language code (default: en). Currently only English is
                available from HuggingFace. Other languages require fine-tuning.
"""

import argparse
import os
import sys
from pathlib import Path

try:
    from transformers import T5ForConditionalGeneration, T5Tokenizer
except ImportError:
    print("Error: transformers library not found.")
    print("Install with: pip install transformers torch")
    sys.exit(1)

# HuggingFace model identifiers
MODELS = {
    "en": "Unbabel/gec-t5_small",
    # Other languages would need fine-tuned models
    # These are placeholders for future fine-tuned models
    "de": None,  # Would need fine-tuning on Falko-MERLIN dataset
    "es": None,  # Would need fine-tuning on COWS-L2H dataset
    "fr": None,  # Would need fine-tuning on EFCAMDAT dataset
    "it": None,  # Would need fine-tuning on MERLIN dataset
    "pt": None,  # Would need fine-tuning
    "pl": None,  # Would need fine-tuning on PolEval dataset
    "ru": None,  # Would need fine-tuning on RULEC-GEC dataset
    "lt": None,  # Would need custom dataset
}

def download_model(language: str, output_dir: Path):
    """Download the T5 model for the specified language."""
    model_id = MODELS.get(language)

    if model_id is None:
        print(f"Error: No pre-trained model available for language '{language}'")
        print("Available languages with pre-trained models: en")
        print("\nFor other languages, you need to fine-tune the base T5 model.")
        print("See: tools/models/finetune_grammar_model.py (to be created)")
        return False

    output_path = output_dir / f"t5_gec_{language}"

    print(f"Downloading model: {model_id}")
    print(f"Output directory: {output_path}")

    try:
        # Download tokenizer
        print("\n[1/2] Downloading tokenizer...")
        tokenizer = T5Tokenizer.from_pretrained(model_id)
        tokenizer.save_pretrained(output_path / "tokenizer")
        print(f"Tokenizer saved to: {output_path / 'tokenizer'}")

        # Download model
        print("\n[2/2] Downloading model (this may take a while)...")
        model = T5ForConditionalGeneration.from_pretrained(model_id)
        model.save_pretrained(output_path / "model")
        print(f"Model saved to: {output_path / 'model'}")

        # Report sizes
        tokenizer_size = sum(f.stat().st_size for f in (output_path / "tokenizer").rglob("*") if f.is_file())
        model_size = sum(f.stat().st_size for f in (output_path / "model").rglob("*") if f.is_file())

        print(f"\n=== Download Complete ===")
        print(f"Tokenizer size: {tokenizer_size / 1024 / 1024:.1f} MB")
        print(f"Model size: {model_size / 1024 / 1024:.1f} MB")
        print(f"Total: {(tokenizer_size + model_size) / 1024 / 1024:.1f} MB")

        print(f"\nNext step: Convert to TFLite format using:")
        print(f"  python convert_to_tflite.py --language {language}")

        return True

    except Exception as e:
        print(f"Error downloading model: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Download T5-Small GEC model from HuggingFace"
    )
    parser.add_argument(
        "--language", "-l",
        default="en",
        choices=list(MODELS.keys()),
        help="Language code (default: en)"
    )
    parser.add_argument(
        "--output-dir", "-o",
        type=Path,
        default=Path(__file__).parent / "downloaded",
        help="Output directory for downloaded models"
    )

    args = parser.parse_args()

    # Create output directory
    args.output_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 50)
    print("TitanKeys Grammar Model Downloader")
    print("=" * 50)
    print(f"\nLanguage: {args.language}")
    print(f"Output: {args.output_dir}")
    print()

    success = download_model(args.language, args.output_dir)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
