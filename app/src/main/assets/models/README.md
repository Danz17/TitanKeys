# Contextual AI Model Assets

This directory contains the TensorFlow Lite model and supporting files for contextual AI next-word prediction.

## Required Files

### contextual_predictor.tflite
- TensorFlow Lite model file
- Input: Tokenized sequence (int32[max_sequence_length])
- Output: Logits for vocabulary (float32[vocab_size])
- Model should be a lightweight transformer (e.g., distilled BERT, custom transformer)
- Optimized for mobile inference with <100ms latency

### vocab.txt
- Vocabulary file mapping words to token IDs
- One word per line
- First few tokens should be special tokens: [PAD]=0, [UNK]=1, [CLS]=2, [SEP]=3, [MASK]=4
- Should contain ~30,000 common words for English

## Model Specifications

- **Architecture**: Lightweight transformer encoder
- **Max Sequence Length**: 32 tokens
- **Vocabulary Size**: 30,000
- **Hidden Size**: 256
- **Layers**: 4-6 transformer layers
- **Attention Heads**: 8
- **Feed Forward**: 1024

## Training Data

- Trained on large text corpora (books, articles, web text)
- Fine-tuned for next-word prediction task
- Should understand contextual relationships beyond n-grams

## Performance Requirements

- Inference time: <100ms on modern mobile devices
- Model size: <50MB
- Memory usage: <200MB during inference

## Privacy Considerations

- Model processes text locally on device
- No data sent to external servers
- All processing happens offline
- User text is not stored or transmitted