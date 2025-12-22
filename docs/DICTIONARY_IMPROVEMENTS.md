# Dictionary Improvements Guide

This guide explains how to improve TitanKeys dictionaries with better word lists and n-gram data for next-word prediction.

## Overview

TitanKeys dictionaries can be enhanced with:
- **Larger word lists** (50k+ words per language)
- **N-gram data** (bigrams and trigrams) for next-word prediction
- **Domain-specific terms** (technical, medical, etc.)
- **Common phrases** for phrase prediction

## Current Dictionary Structure

Dictionaries are stored in:
- **Source**: `app/src/main/assets/common/dictionaries/*_base.json`
- **Format**: `[{"w": "word", "f": frequency}, ...]`
- **Processed**: `app/src/main/assets/common/dictionaries_serialized/*_base.dict` (CBOR format)

## Improvement Pipeline

### Step 1: Download Text Corpora

Use the download script to get word frequency lists:

```bash
python scripts/download_corpora.py --language it --source all --convert
```

**Sources Available**:
- **OpenSubtitles**: Word frequency lists (conversational text)
- **Wikipedia**: Word frequency data (general knowledge)

**Output**: Files saved to `corpora/` directory

### Step 2: Extract N-grams

Extract bigrams and trigrams from text files:

```bash
python scripts/extract_ngrams.py input.txt output_bigrams.json output_trigrams.json --min-freq 2
```

**Parameters**:
- `--min-freq N`: Minimum frequency to include (filters rare n-grams)

**Output**: JSON files with n-gram frequencies

### Step 3: Merge Dictionaries

Merge multiple word lists into one optimized dictionary:

```bash
python scripts/merge_dictionaries.py dict1.json dict2.json --output merged.json --strategy weighted
```

**Merge Strategies**:
- `max`: Use maximum frequency (best for quality)
- `sum`: Sum all frequencies (best for coverage)
- `avg`: Average frequencies (balanced)
- `weighted`: Weighted average (first source has higher weight)

### Step 4: Complete Pipeline

Run the full pipeline script:

```bash
python scripts/build_complete_dictionary.py --language it --all
```

This script:
1. Downloads corpora (optional)
2. Extracts n-grams (optional)
3. Merges dictionaries (optional)
4. Preprocesses to .dict format (optional)

## Manual Process

### 1. Acquire Text Corpora

**OpenSubtitles**:
- Visit: https://github.com/hermitdave/FrequencyWords
- Download language-specific word frequency lists
- Format: `word frequency` (space-separated)

**Wikipedia**:
- Download word frequency CSV files
- Format: `word,count` (comma-separated)

**Project Gutenberg**:
- Download public domain books
- Extract text for n-gram generation

### 2. Process Text Files

Extract n-grams from large text files:

```bash
python scripts/extract_ngrams.py book.txt bigrams.json trigrams.json --min-freq 3
```

### 3. Enhance Base Dictionary

Merge new word lists with existing dictionary:

```bash
python scripts/merge_dictionaries.py \
    app/src/main/assets/common/dictionaries/it_base.json \
    corpora/it_opensubtitles.json \
    corpora/it_wikipedia.json \
    --output corpora/it_enhanced.json \
    --strategy weighted
```

### 4. Include N-grams in Preprocessing

Place n-gram JSON files in `corpora/` directory:
- `{language}_bigrams.json`
- `{language}_trigrams.json`

The preprocessing script will automatically include them if found.

### 5. Generate Final Dictionary

Run preprocessing:

```bash
kotlinc -script scripts/preprocess-dictionaries.main.kts
```

Or use the enhanced dictionary:

```bash
# Copy enhanced dictionary to dictionaries directory
cp corpora/it_enhanced.json app/src/main/assets/common/dictionaries/it_base.json

# Run preprocessing
kotlinc -script scripts/preprocess-dictionaries.main.kts
```

## N-gram File Format

### Bigrams JSON Format

```json
{
  "word1": {
    "word2": 150,
    "word3": 200
  },
  "word4": {
    "word5": 100
  }
}
```

### Trigrams JSON Format

```json
{
  "word1": {
    "word2": {
      "word3": 50,
      "word4": 30
    }
  }
}
```

## Quality Targets

### Word Lists
- **Minimum**: 50,000 words per language
- **Ideal**: 100,000+ words
- **Frequency range**: 1 to 10,000+ (normalized)

### N-grams
- **Bigrams**: 100,000+ per language
- **Trigrams**: 50,000+ per language
- **Minimum frequency**: 2-3 (filters noise)

### Performance
- **Loading time**: <500ms
- **Memory usage**: <50MB per language
- **Lookup speed**: <50ms for predictions

## Testing Improved Dictionaries

1. **Build and install** the app with new dictionaries
2. **Test next-word prediction** accuracy
3. **Monitor performance** (loading time, memory)
4. **Validate coverage** (test common words/phrases)

## Resources

### Word Frequency Lists
- OpenSubtitles: https://github.com/hermitdave/FrequencyWords
- Wikipedia: Various GitHub repositories
- Project Gutenberg: https://www.gutenberg.org/

### Text Corpora
- OpenSubtitles subtitle files (best for conversational text)
- Wikipedia article dumps
- News article corpora
- Book collections (Project Gutenberg)

### Tools
- Python 3.x (for processing scripts)
- Kotlin compiler (for preprocessing)
- Text processing tools (grep, sed, etc.)

## Tips

1. **Start with one language** to test the pipeline
2. **Use weighted merge** to preserve quality from base dictionary
3. **Filter low-frequency n-grams** to reduce file size
4. **Test loading performance** before committing large dictionaries
5. **Keep backups** of original dictionaries

## Troubleshooting

### Dictionary Too Large
- Filter by minimum frequency
- Remove very rare words
- Use compression (CBOR format helps)

### N-grams Not Loading
- Check file names match language code
- Verify JSON format is correct
- Check preprocessing script logs

### Poor Prediction Quality
- Increase n-gram coverage
- Use higher quality text sources
- Adjust frequency thresholds

---

*Made by Phenix with love and frustration*

