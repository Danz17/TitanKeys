#!/usr/bin/env python3
"""
Merge multiple dictionary word lists into a single optimized dictionary.

This script combines word lists from different sources, deduplicates entries,
and intelligently merges frequencies.

Usage:
    python merge_dictionaries.py input1.json input2.json ... --output merged.json [--strategy STRATEGY]

Merge Strategies:
    - max: Use maximum frequency when duplicates found
    - sum: Sum frequencies for duplicates
    - avg: Average frequencies for duplicates
    - weighted: Weighted average (first source has higher weight)
"""

import argparse
import json
import sys
from pathlib import Path
from typing import Dict, List, Tuple
from collections import defaultdict


def load_dictionary(json_file: Path) -> List[Dict]:
    """Load dictionary from JSON file."""
    try:
        with open(json_file, 'r', encoding='utf-8') as f:
            data = json.load(f)
            if isinstance(data, list):
                return data
            else:
                print(f"Warning: {json_file} is not a list format, skipping")
                return []
    except Exception as e:
        print(f"Error loading {json_file}: {e}")
        return []


def normalize_word(word: str) -> str:
    """Normalize word for comparison (lowercase, no accents)."""
    # Simple normalization - for full support use unicodedata
    word = word.lower().strip()
    # Remove common accents (simplified)
    word = word.replace('à', 'a').replace('è', 'e').replace('é', 'e').replace('ì', 'i')
    word = word.replace('ò', 'o').replace('ó', 'o').replace('ù', 'u')
    return word


def merge_max_frequency(entries: List[Dict]) -> Dict:
    """Merge strategy: Use maximum frequency."""
    if not entries:
        return None
    
    # Find entry with maximum frequency, preserving original case
    max_entry = max(entries, key=lambda e: e.get('f', 0))
    return {
        'w': max_entry['w'],  # Preserve original case from highest frequency entry
        'f': max_entry['f']
    }


def merge_sum_frequency(entries: List[Dict]) -> Dict:
    """Merge strategy: Sum all frequencies."""
    if not entries:
        return None
    
    total_freq = sum(e.get('f', 0) for e in entries)
    # Use the first entry's word (preserve case from first source)
    return {
        'w': entries[0]['w'],
        'f': total_freq
    }


def merge_avg_frequency(entries: List[Dict]) -> Dict:
    """Merge strategy: Average frequencies."""
    if not entries:
        return None
    
    avg_freq = sum(e.get('f', 0) for e in entries) // len(entries)
    return {
        'w': entries[0]['w'],
        'f': avg_freq
    }


def merge_weighted_frequency(entries: List[Dict], weights: List[float] = None) -> Dict:
    """Merge strategy: Weighted average (first source has higher weight)."""
    if not entries:
        return None
    
    if weights is None:
        # Default: first entry gets 0.6, others share 0.4
        weights = [0.6] + [0.4 / (len(entries) - 1)] * (len(entries) - 1) if len(entries) > 1 else [1.0]
    
    weighted_sum = sum(e.get('f', 0) * w for e, w in zip(entries, weights))
    return {
        'w': entries[0]['w'],
        'f': int(weighted_sum)
    }


def merge_dictionaries(
    input_files: List[Path],
    output_file: Path,
    strategy: str = 'max',
    min_frequency: int = 1
) -> bool:
    """Merge multiple dictionary files into one."""
    
    print(f"Merging {len(input_files)} dictionary files...")
    print(f"Strategy: {strategy}")
    print(f"Minimum frequency: {min_frequency}")
    print()
    
    # Load all dictionaries
    all_entries = []
    for input_file in input_files:
        entries = load_dictionary(input_file)
        print(f"Loaded {len(entries)} entries from {input_file.name}")
        all_entries.extend(entries)
    
    if not all_entries:
        print("Error: No entries loaded from input files")
        return False
    
    # Group by normalized word
    word_groups: Dict[str, List[Dict]] = defaultdict(list)
    for entry in all_entries:
        word = entry.get('w', '')
        if not word:
            continue
        normalized = normalize_word(word)
        word_groups[normalized].append(entry)
    
    print(f"\nFound {len(word_groups)} unique words (after normalization)")
    print(f"Total entries before merge: {len(all_entries)}")
    
    # Merge entries based on strategy
    merge_strategies = {
        'max': merge_max_frequency,
        'sum': merge_sum_frequency,
        'avg': merge_avg_frequency,
        'weighted': merge_weighted_frequency
    }
    
    merge_func = merge_strategies.get(strategy, merge_max_frequency)
    
    merged_entries = []
    duplicates_count = 0
    
    for normalized, entries in word_groups.items():
        if len(entries) > 1:
            duplicates_count += len(entries) - 1
            merged = merge_func(entries)
        else:
            merged = entries[0]
        
        if merged and merged.get('f', 0) >= min_frequency:
            merged_entries.append(merged)
    
    # Sort by frequency (descending)
    merged_entries.sort(key=lambda e: e.get('f', 0), reverse=True)
    
    print(f"Merged {duplicates_count} duplicate entries")
    print(f"Final dictionary: {len(merged_entries)} entries")
    
    # Write output
    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(merged_entries, f, ensure_ascii=False, indent=2)
        print(f"\n[OK] Saved merged dictionary to {output_file}")
        print(f"  Top 10 words by frequency:")
        for i, entry in enumerate(merged_entries[:10], 1):
            print(f"    {i}. {entry['w']} (freq: {entry['f']})")
        return True
    except Exception as e:
        print(f"\n[ERROR] Error saving output: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description='Merge multiple dictionary files')
    parser.add_argument('inputs', nargs='+', type=Path, help='Input JSON dictionary files')
    parser.add_argument('--output', '-o', type=Path, required=True, help='Output merged dictionary file')
    parser.add_argument('--strategy', '-s', choices=['max', 'sum', 'avg', 'weighted'],
                       default='max', help='Merge strategy for duplicate words')
    parser.add_argument('--min-freq', '-m', type=int, default=1,
                       help='Minimum frequency to include in output')
    
    args = parser.parse_args()
    
    # Validate input files
    for input_file in args.inputs:
        if not input_file.exists():
            print(f"Error: Input file not found: {input_file}")
            return 1
    
    # Create output directory if needed
    args.output.parent.mkdir(parents=True, exist_ok=True)
    
    success = merge_dictionaries(args.inputs, args.output, args.strategy, args.min_freq)
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())

