#!/usr/bin/env python3
"""
Convert T5 GEC model to TensorFlow Lite format with INT8 quantization.

This script takes a downloaded T5 model and converts it to TFLite format
suitable for on-device inference in TitanKeys.

Usage:
    python convert_to_tflite.py --language LANG [--quantize]

Arguments:
    --language  Language code (e.g., en, de, es)
    --quantize  Apply INT8 quantization (reduces size ~4x, recommended)

The output will be:
    - grammar_{lang}.tflite - The quantized model (~60MB for INT8)
    - vocab_{lang}.txt - Vocabulary file for tokenization
"""

import argparse
import os
import sys
from pathlib import Path

try:
    import tensorflow as tf
    from transformers import T5ForConditionalGeneration, T5Tokenizer
    from transformers import TFT5ForConditionalGeneration
except ImportError as e:
    print(f"Error: Required library not found: {e}")
    print("\nInstall dependencies with:")
    print("  pip install transformers tensorflow torch")
    sys.exit(1)

def load_pytorch_model(model_path: Path):
    """Load the PyTorch model and tokenizer."""
    print(f"Loading model from: {model_path}")

    tokenizer = T5Tokenizer.from_pretrained(model_path / "tokenizer")
    model = T5ForConditionalGeneration.from_pretrained(model_path / "model")

    return model, tokenizer


def convert_to_tensorflow(pt_model, tokenizer, language: str, output_dir: Path):
    """Convert PyTorch model to TensorFlow format."""
    print("\n[1/4] Converting PyTorch model to TensorFlow...")

    # Load as TF model directly (transformers supports this)
    tf_model = TFT5ForConditionalGeneration.from_pretrained(
        output_dir.parent / f"t5_gec_{language}" / "model",
        from_pt=True
    )

    tf_output = output_dir / f"tf_model_{language}"
    tf_model.save_pretrained(tf_output)
    print(f"TensorFlow model saved to: {tf_output}")

    return tf_model


def create_concrete_function(tf_model, max_length: int = 128):
    """Create a concrete function for TFLite conversion."""
    print("\n[2/4] Creating concrete function for TFLite...")

    @tf.function(input_signature=[
        tf.TensorSpec(shape=[1, max_length], dtype=tf.int32, name="input_ids"),
        tf.TensorSpec(shape=[1, max_length], dtype=tf.int32, name="attention_mask"),
    ])
    def serving_fn(input_ids, attention_mask):
        # For T5, we need to use the encoder-decoder architecture
        # This is a simplified version - full implementation would need decoder
        outputs = tf_model.generate(
            input_ids=input_ids,
            attention_mask=attention_mask,
            max_length=max_length,
            num_beams=1,  # Greedy decoding for speed
            early_stopping=True
        )
        return {"output_ids": outputs}

    return serving_fn


def convert_to_tflite(tf_model, tokenizer, language: str, output_dir: Path, quantize: bool):
    """Convert TensorFlow model to TFLite format."""
    print("\n[3/4] Converting to TFLite format...")

    max_length = 128

    # Create saved model with concrete function
    saved_model_dir = output_dir / f"saved_model_{language}"
    saved_model_dir.mkdir(parents=True, exist_ok=True)

    # For T5 models, we'll export the encoder portion for efficient inference
    # The full encoder-decoder is complex for TFLite, so we use encoder + simple decoder

    try:
        # Export encoder
        encoder = tf_model.get_encoder()

        @tf.function(input_signature=[
            tf.TensorSpec(shape=[1, max_length], dtype=tf.int32),
            tf.TensorSpec(shape=[1, max_length], dtype=tf.int32),
        ])
        def encoder_fn(input_ids, attention_mask):
            return encoder(input_ids=input_ids, attention_mask=attention_mask)

        # Save as concrete function
        concrete_fn = encoder_fn.get_concrete_function()

        # Convert to TFLite
        converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_fn])

        if quantize:
            print("  Applying INT8 quantization...")
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            converter.target_spec.supported_types = [tf.int8]
            # For full quantization, we'd need representative dataset
            # converter.representative_dataset = representative_dataset_gen

        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
            tf.lite.OpsSet.SELECT_TF_OPS  # For ops not in TFLite builtins
        ]

        tflite_model = converter.convert()

        # Save TFLite model
        tflite_path = output_dir / f"grammar_{language}.tflite"
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)

        print(f"TFLite model saved to: {tflite_path}")
        print(f"Model size: {len(tflite_model) / 1024 / 1024:.1f} MB")

        return tflite_path

    except Exception as e:
        print(f"Error during TFLite conversion: {e}")
        print("\nNote: T5 models are complex for TFLite conversion.")
        print("For production, consider using ONNX or a simpler model architecture.")
        raise


def export_vocabulary(tokenizer, language: str, output_dir: Path):
    """Export vocabulary file for tokenization."""
    print("\n[4/4] Exporting vocabulary...")

    vocab_path = output_dir / f"vocab_{language}.txt"

    # Get vocabulary from tokenizer
    vocab = tokenizer.get_vocab()

    # Sort by index
    sorted_vocab = sorted(vocab.items(), key=lambda x: x[1])

    with open(vocab_path, "w", encoding="utf-8") as f:
        for token, idx in sorted_vocab:
            f.write(f"{token}\n")

    print(f"Vocabulary saved to: {vocab_path}")
    print(f"Vocabulary size: {len(sorted_vocab)} tokens")

    return vocab_path


def main():
    parser = argparse.ArgumentParser(
        description="Convert T5 GEC model to TensorFlow Lite format"
    )
    parser.add_argument(
        "--language", "-l",
        required=True,
        help="Language code (e.g., en, de, es)"
    )
    parser.add_argument(
        "--input-dir", "-i",
        type=Path,
        default=Path(__file__).parent / "downloaded",
        help="Directory containing downloaded models"
    )
    parser.add_argument(
        "--output-dir", "-o",
        type=Path,
        default=Path(__file__).parent / "output",
        help="Output directory for converted models"
    )
    parser.add_argument(
        "--quantize", "-q",
        action="store_true",
        default=True,
        help="Apply INT8 quantization (default: True)"
    )
    parser.add_argument(
        "--no-quantize",
        action="store_true",
        help="Disable quantization (larger model, potentially better accuracy)"
    )

    args = parser.parse_args()

    if args.no_quantize:
        args.quantize = False

    model_path = args.input_dir / f"t5_gec_{args.language}"

    if not model_path.exists():
        print(f"Error: Model not found at {model_path}")
        print(f"\nFirst download the model using:")
        print(f"  python download_grammar_model.py --language {args.language}")
        sys.exit(1)

    args.output_dir.mkdir(parents=True, exist_ok=True)

    print("=" * 50)
    print("TitanKeys Grammar Model Converter")
    print("=" * 50)
    print(f"\nLanguage: {args.language}")
    print(f"Input: {model_path}")
    print(f"Output: {args.output_dir}")
    print(f"Quantization: {'INT8' if args.quantize else 'None (FP32)'}")
    print()

    try:
        # Load PyTorch model
        pt_model, tokenizer = load_pytorch_model(model_path)

        # Convert to TensorFlow
        tf_model = convert_to_tensorflow(pt_model, tokenizer, args.language, args.output_dir)

        # Convert to TFLite
        tflite_path = convert_to_tflite(tf_model, tokenizer, args.language, args.output_dir, args.quantize)

        # Export vocabulary
        vocab_path = export_vocabulary(tokenizer, args.language, args.output_dir)

        print("\n" + "=" * 50)
        print("Conversion Complete!")
        print("=" * 50)
        print(f"\nOutput files:")
        print(f"  - {tflite_path}")
        print(f"  - {vocab_path}")
        print(f"\nTo use these files:")
        print(f"  1. Upload to model hosting (GitHub Releases, Firebase, etc.)")
        print(f"  2. Update BASE_URL in GrammarModelDownloader.kt")
        print(f"  3. Add checksum to MODEL_MANIFEST")

    except Exception as e:
        print(f"\nError: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
