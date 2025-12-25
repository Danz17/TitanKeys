# TitanKeys Grammar AI Model Tools

This directory contains scripts for downloading, converting, and managing grammar AI models for TitanKeys.

## Overview

TitanKeys uses T5-Small based models for grammar error correction. The English model (Unbabel/gec-t5_small) has been tested and works excellently.

## Model Architecture

- **Base Model**: T5-Small (60M parameters)
- **Task**: Grammatical Error Correction (GEC)
- **Input**: Text with potential errors (prefix: "gec: ")
- **Output**: Corrected text
- **PyTorch Size**: ~230MB (FP32)
- **Target Size**: ~60MB (INT8 quantized for mobile)

## Quick Start

### Prerequisites

```bash
pip install transformers torch sentencepiece hf_xet
```

### Step 1: Download Model

```bash
# Download English GEC model from HuggingFace
python download_grammar_model.py --language en
```

Or use the direct download script:
```bash
python -c "
from transformers import T5ForConditionalGeneration, T5Tokenizer
import os

model = T5ForConditionalGeneration.from_pretrained('Unbabel/gec-t5_small')
tokenizer = T5Tokenizer.from_pretrained('Unbabel/gec-t5_small')

os.makedirs('downloaded/t5_gec_en/model', exist_ok=True)
os.makedirs('downloaded/t5_gec_en/tokenizer', exist_ok=True)
model.save_pretrained('downloaded/t5_gec_en/model')
tokenizer.save_pretrained('downloaded/t5_gec_en/tokenizer')
print('Model downloaded successfully!')
"
```

### Step 2: Test Model (Optional)

```bash
python -c "
from transformers import T5ForConditionalGeneration, T5Tokenizer
import torch

model = T5ForConditionalGeneration.from_pretrained('downloaded/t5_gec_en/model')
tokenizer = T5Tokenizer.from_pretrained('downloaded/t5_gec_en/tokenizer')

sentence = 'I goed to the store yesterday.'
inputs = tokenizer(f'gec: {sentence}', return_tensors='pt')
outputs = model.generate(inputs['input_ids'], max_length=128, num_beams=4)
print(f'Original: {sentence}')
print(f'Corrected: {tokenizer.decode(outputs[0], skip_special_tokens=True)}')
"
```

### Step 3: Convert to TFLite

**Note**: TFLite conversion requires TensorFlow, which needs Python 3.11 or 3.12.

```bash
# With Python 3.11/3.12:
pip install tensorflow
python convert_to_tflite.py --language en
```

**Alternative**: Export to ONNX format (also requires Python 3.11/3.12):
```bash
pip install onnx onnxruntime optimum[exporters]
python export_onnx.py --language en
```

### Step 4: Upload and Configure

1. Upload `grammar_en.tflite` and `vocab_en.txt` to hosting (GitHub Releases recommended)
2. Update `GrammarModelDownloader.kt` with the correct URLs
3. Add SHA-256 checksum to `MODEL_MANIFEST`

## Available Languages

| Language | Code | Pre-trained | Status |
|----------|------|-------------|--------|
| English | en | ✅ Unbabel/gec-t5_small | Ready |
| German | de | ❌ Fine-tune needed | Planned |
| Spanish | es | ❌ Fine-tune needed | Planned |
| French | fr | ❌ Fine-tune needed | Planned |
| Italian | it | ❌ Fine-tune needed | Planned |
| Portuguese | pt | ❌ Fine-tune needed | Planned |
| Polish | pl | ❌ Fine-tune needed | Planned |
| Russian | ru | ❌ Fine-tune needed | Planned |
| Lithuanian | lt | ❌ Custom dataset needed | Future |

## Fine-tuning Guide (for non-English languages)

For languages without pre-trained GEC models, you'll need to fine-tune T5-Small on language-specific GEC datasets:

### Datasets

- **German**: Falko-MERLIN corpus
- **Spanish**: COWS-L2H corpus
- **French**: EFCAMDAT
- **Italian**: MERLIN
- **Polish**: PolEval 2022 GEC task
- **Russian**: RULEC-GEC

### Fine-tuning Script (TODO)

```bash
python finetune_grammar_model.py \
  --base-model t5-small \
  --dataset path/to/gec_dataset.tsv \
  --language de \
  --output-dir models/de
```

## Model Hosting

Recommended hosting options:

1. **GitHub Releases** (free, versioned)
   - Create a release with model files as assets
   - URL format: `https://github.com/user/repo/releases/download/v1.0/grammar_en.tflite`

2. **Firebase Storage** (scalable, CDN)
   - Good for larger user base
   - Requires Firebase project setup

3. **AWS S3 / CloudFront** (enterprise)
   - For production at scale

## File Structure

```
tools/models/
├── README.md                    # This file
├── download_grammar_model.py    # Download from HuggingFace
├── convert_to_tflite.py         # Convert to TFLite
├── downloaded/                  # Downloaded PyTorch models
│   └── t5_gec_en/
│       ├── model/
│       └── tokenizer/
└── output/                      # Converted TFLite models
    ├── grammar_en.tflite
    └── vocab_en.txt
```

## Integration with App

Models are loaded by `GrammarModelManager.kt` from:
- `context.filesDir/models/grammar/grammar_{lang}.tflite`

Downloaded via `GrammarModelDownloader.kt` with progress tracking.

## Performance Targets

| Metric | Target |
|--------|--------|
| Model Size | ≤60MB per language |
| Load Time | <2s |
| Inference Time | <100ms per sentence |
| Memory Usage | <100MB during inference |

## Troubleshooting

### TFLite Conversion Fails

T5 models use ops not fully supported in TFLite. Options:
1. Use `SELECT_TF_OPS` (increases APK size by ~10MB)
2. Consider ONNX as alternative format
3. Use a simpler model architecture (BERT-based, smaller T5)

### Model Too Large

- Ensure INT8 quantization is enabled
- Consider model pruning
- Use dynamic range quantization as fallback

### Poor Accuracy

- Check tokenization is working correctly
- Verify vocabulary file matches model
- Test with known error patterns first
