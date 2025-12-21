#!/usr/bin/env python3
"""
Extract n-grams (bigrams and trigrams) from text corpora for next-word prediction.

Usage:
    python extract_ngrams.py input.txt output_bigrams.json output_trigrams.json [--min-freq N]

This script processes text files and extracts word sequences to build language models
for next-word prediction in the Pastiera keyboard.
"""

import json
import re
import sys
from collections import defaultdict
from typing import Dict, Tuple
import argparse


def normalize_word(word: str) -> str:
    """Normalize a word: lowercase, remove accents, keep only letters."""
    # Remove common punctuation and convert to lowercase
    word = word.lower().strip()
    # Remove accents (simplified - for full support use unicodedata)
    word = word.replace('à', 'a').replace('è', 'e').replace('é', 'e').replace('ì', 'i')
    word = word.replace('ò', 'o').replace('ó', 'o').replace('ù', 'u')
    word = word.replace('À', 'a').replace('È', 'e').replace('É', 'e').replace('Ì', 'i')
    word = word.replace('Ò', 'o').replace('Ó', 'o').replace('Ù', 'u')
    # Keep only letters (remove apostrophes for n-gram matching)
    word = re.sub(r'[^a-z]', '', word)
    return word


def extract_words(text: str) -> list[str]:
    """Extract normalized words from text."""
    # Split on whitespace and punctuation
    words = re.findall(r'\b\w+\b', text)
    normalized = [normalize_word(w) for w in words if w]
    # Filter out empty words and very short words (likely artifacts)
    return [w for w in normalized if len(w) > 1]


def extract_ngrams(input_file: str, min_freq: int = 1) -> Tuple[Dict[str, Dict[str, int]], Dict[str, Dict[str, Dict[str, int]]]]:
    """
    Extract bigrams and trigrams from a text file.
    
    Returns:
        Tuple of (bigrams, trigrams) where:
        - bigrams: word1 -> word2 -> frequency
        - trigrams: word1 -> word2 -> word3 -> frequency
    """
    bigrams: Dict[str, Dict[str, int]] = defaultdict(lambda: defaultdict(int))
    trigrams: Dict[str, Dict[str, Dict[str, int]]] = defaultdict(lambda: defaultdict(lambda: defaultdict(int)))
    
    print(f"Processing {input_file}...")
    
    try:
        with open(input_file, 'r', encoding='utf-8', errors='ignore') as f:
            line_count = 0
            for line in f:
                line_count += 1
                if line_count % 10000 == 0:
                    print(f"  Processed {line_count} lines...", end='\r')
                
                words = extract_words(line)
                
                # Extract bigrams
                for i in range(len(words) - 1):
                    word1 = words[i]
                    word2 = words[i + 1]
                    if word1 and word2:
                        bigrams[word1][word2] += 1
                
                # Extract trigrams
                for i in range(len(words) - 2):
                    word1 = words[i]
                    word2 = words[i + 1]
                    word3 = words[i + 2]
                    if word1 and word2 and word3:
                        trigrams[word1][word2][word3] += 1
            
            print(f"\n  Processed {line_count} lines total")
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error reading file: {e}", file=sys.stderr)
        sys.exit(1)
    
    # Filter by minimum frequency
    if min_freq > 1:
        print(f"Filtering n-grams with frequency < {min_freq}...")
        bigrams_filtered = {}
        for word1, word2_map in bigrams.items():
            filtered_map = {word2: freq for word2, freq in word2_map.items() if freq >= min_freq}
            if filtered_map:
                bigrams_filtered[word1] = filtered_map
        
        trigrams_filtered = {}
        for word1, word2_map in trigrams.items():
            word2_filtered = {}
            for word2, word3_map in word2_map.items():
                filtered_map = {word3: freq for word3, freq in word3_map.items() if freq >= min_freq}
                if filtered_map:
                    word2_filtered[word2] = filtered_map
            if word2_filtered:
                trigrams_filtered[word1] = word2_filtered
        
        bigrams = bigrams_filtered
        trigrams = trigrams_filtered
    
    print(f"Extracted {sum(len(m) for m in bigrams.values())} bigrams")
    print(f"Extracted {sum(sum(len(m) for m in w2.values()) for w2 in trigrams.values())} trigrams")
    
    return dict(bigrams), dict(trigrams)


def save_json(data: dict, output_file: str):
    """Save data to JSON file."""
    print(f"Saving to {output_file}...")
    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"  Saved successfully")
    except Exception as e:
        print(f"Error saving file: {e}", file=sys.stderr)
        sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description='Extract n-grams from text corpora')
    parser.add_argument('input_file', help='Input text file')
    parser.add_argument('output_bigrams', help='Output JSON file for bigrams')
    parser.add_argument('output_trigrams', help='Output JSON file for trigrams')
    parser.add_argument('--min-freq', type=int, default=1, help='Minimum frequency to include (default: 1)')
    
    args = parser.parse_args()
    
    bigrams, trigrams = extract_ngrams(args.input_file, args.min_freq)
    
    save_json(bigrams, args.output_bigrams)
    save_json(trigrams, args.output_trigrams)
    
    print("\nDone!")


if __name__ == '__main__':
    main()

