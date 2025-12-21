#!/usr/bin/env python3
"""
Download text corpora for dictionary and n-gram generation.

This script downloads word frequency lists and text corpora from various sources
to improve Pastiera keyboard dictionaries.

Usage:
    python download_corpora.py [--language LANG] [--output-dir DIR] [--source SOURCE]

Sources:
    - opensubtitles: Word frequency lists from OpenSubtitles
    - wikipedia: Wikipedia word frequency data
    - gutenberg: Project Gutenberg books (public domain)
"""

import argparse
import os
import sys
import json
import urllib.request
import urllib.error
from pathlib import Path
from typing import Optional, Dict, List
import gzip
import re


# Language code mappings
LANGUAGE_CODES = {
    'it': 'italian',
    'en': 'english',
    'de': 'german',
    'es': 'spanish',
    'fr': 'french',
    'pl': 'polish',
    'pt': 'portuguese',
    'ru': 'russian',
    'lt': 'lithuanian'
}

# FrequencyWords word frequency URLs (from OpenSubtitles corpus)
# Uses language codes (en, it, de) not full names
FREQUENCYWORDS_BASE = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/{lang}/{lang}_50k.txt"

# Wikipedia word frequency (alternative source) - specific dated files
WIKIPEDIA_FILES = {
    'en': 'enwiki-2023-04-13.txt',
    'it': 'itwiki-2022-08-30.txt',
    'de': 'dewiki-2022-08-29.txt',
    'es': 'eswiki-2022-08-29.txt',
    'fr': 'frwiki-2022-08-29.txt',
    'pl': 'plwiki-2022-08-29.txt',
    'pt': 'ptwiki-2022-08-29.txt',
    'ru': 'ruwiki-2022-08-29.txt',
    # Lithuanian not available in this source
}
WIKIPEDIA_FREQ_BASE = "https://raw.githubusercontent.com/IlyaSemenov/wikipedia-word-frequency/master/results/{filename}"


def download_file(url: str, output_path: Path, description: str = "file") -> bool:
    """Download a file from URL to output path."""
    try:
        print(f"Downloading {description} from {url}...")
        urllib.request.urlretrieve(url, output_path)
        print(f"  [OK] Downloaded to {output_path}")
        return True
    except urllib.error.URLError as e:
        print(f"  [FAIL] Failed to download: {e}")
        return False
    except Exception as e:
        print(f"  [ERROR] Error: {e}")
        return False


def download_frequencywords(lang_code: str, output_dir: Path) -> Optional[Path]:
    """Download FrequencyWords word frequency list (from OpenSubtitles corpus)."""
    # Uses language code directly (en, it, de, etc.)
    url = FREQUENCYWORDS_BASE.format(lang=lang_code)

    output_file = output_dir / f"{lang_code}_frequencywords_50k.txt"

    if download_file(url, output_file, f"FrequencyWords list for {lang_code}"):
        return output_file
    return None


def download_wikipedia_frequency(lang_code: str, output_dir: Path) -> Optional[Path]:
    """Download Wikipedia word frequency data."""
    if lang_code not in WIKIPEDIA_FILES:
        print(f"  [SKIP] Wikipedia frequency not available for {lang_code}")
        return None

    filename = WIKIPEDIA_FILES[lang_code]
    url = WIKIPEDIA_FREQ_BASE.format(filename=filename)

    output_file = output_dir / f"{lang_code}_wikipedia_freq.txt"

    if download_file(url, output_file, f"Wikipedia frequency for {lang_code}"):
        return output_file
    return None


def convert_opensubtitles_to_json(txt_file: Path, json_file: Path) -> bool:
    """Convert OpenSubtitles frequency list to JSON format."""
    try:
        entries = []
        print(f"Converting {txt_file.name} to JSON...")
        
        with open(txt_file, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                
                # Format: word frequency (space-separated)
                parts = line.split()
                if len(parts) < 2:
                    continue
                
                word = ' '.join(parts[:-1])
                try:
                    frequency = int(parts[-1])
                    entries.append({"w": word, "f": frequency})
                except ValueError:
                    continue
        
        # Write JSON
        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(entries, f, ensure_ascii=False, indent=2)
        
        print(f"  [OK] Converted {len(entries)} entries to {json_file.name}")
        return True
    except Exception as e:
        print(f"  [FAIL] Conversion failed: {e}")
        return False


def convert_wikipedia_to_json(csv_file: Path, json_file: Path, limit: int = 50000) -> bool:
    """Convert Wikipedia frequency CSV to JSON format."""
    try:
        entries = []
        print(f"Converting {csv_file.name} to JSON...")
        
        with open(csv_file, 'r', encoding='utf-8') as f:
            # Skip header if present
            first_line = f.readline()
            if not first_line.strip().startswith('word'):
                f.seek(0)
            
            for line_num, line in enumerate(f, 1):
                if len(entries) >= limit:
                    break
                
                line = line.strip()
                if not line:
                    continue
                
                # CSV format: word,count or word count
                parts = line.split(',')
                if len(parts) < 2:
                    parts = line.split()
                
                if len(parts) < 2:
                    continue
                
                word = parts[0].strip().strip('"')
                try:
                    frequency = int(parts[1].strip().strip('"'))
                    entries.append({"w": word, "f": frequency})
                except (ValueError, IndexError):
                    continue
        
        # Write JSON
        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(entries, f, ensure_ascii=False, indent=2)
        
        print(f"  [OK] Converted {len(entries)} entries to {json_file.name}")
        return True
    except Exception as e:
        print(f"  [FAIL] Conversion failed: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description='Download text corpora for dictionary generation')
    parser.add_argument('--language', '-l', help='Language code (it, en, de, etc.)', default='all')
    parser.add_argument('--output-dir', '-o', help='Output directory', default='corpora')
    parser.add_argument('--source', '-s', choices=['opensubtitles', 'wikipedia', 'all'], 
                       default='all', help='Data source to download')
    parser.add_argument('--convert', '-c', action='store_true', 
                       help='Convert downloaded files to JSON format')
    
    args = parser.parse_args()
    
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    languages = [args.language] if args.language != 'all' else list(LANGUAGE_CODES.keys())
    
    print(f"Downloading corpora for languages: {', '.join(languages)}")
    print(f"Output directory: {output_dir.absolute()}")
    print()
    
    downloaded_files = []
    
    for lang_code in languages:
        print(f"\n=== Processing {lang_code} ===")
        
        if args.source in ['opensubtitles', 'all']:
            file = download_frequencywords(lang_code, output_dir)
            if file:
                downloaded_files.append(file)
                if args.convert:
                    json_file = output_dir / f"{lang_code}_frequencywords.json"
                    convert_opensubtitles_to_json(file, json_file)
        
        if args.source in ['wikipedia', 'all']:
            file = download_wikipedia_frequency(lang_code, output_dir)
            if file:
                downloaded_files.append(file)
                if args.convert:
                    json_file = output_dir / f"{lang_code}_wikipedia.json"
                    convert_wikipedia_to_json(file, json_file)
    
    print(f"\n=== Summary ===")
    print(f"Downloaded {len(downloaded_files)} files")
    print(f"Files saved to: {output_dir.absolute()}")
    
    if not downloaded_files:
        print("\n[WARN] No files were downloaded. Check your internet connection and URLs.")
        print("Note: Some sources may require manual download or have different URLs.")
        return 1
    
    return 0


if __name__ == '__main__':
    sys.exit(main())

