#!/usr/bin/env python3
"""
Complete dictionary building pipeline.

This script orchestrates the full process:
1. Download corpora (optional)
2. Extract n-grams from text files
3. Merge word lists
4. Generate final dictionary with n-grams

Usage:
    python build_complete_dictionary.py --language LANG [--download] [--extract-ngrams] [--merge] [--preprocess]
"""

import argparse
import subprocess
import sys
from pathlib import Path
from typing import Optional


def run_command(cmd: list, description: str) -> bool:
    """Run a shell command and return success status."""
    print(f"\n{'='*60}")
    print(f"Step: {description}")
    print(f"Command: {' '.join(cmd)}")
    print('='*60)
    
    try:
        result = subprocess.run(cmd, check=True, capture_output=True, text=True)
        if result.stdout:
            print(result.stdout)
        return True
    except subprocess.CalledProcessError as e:
        print(f"Error: {e}")
        if e.stderr:
            print(f"Stderr: {e.stderr}")
        return False
    except FileNotFoundError:
        print(f"Error: Command not found. Make sure the script is in your PATH.")
        return False


def build_dictionary(
    language: str,
    download: bool = False,
    extract_ngrams: bool = False,
    merge: bool = False,
    preprocess: bool = False,
    corpora_dir: Path = Path("tools/corpora"),
    output_dir: Path = Path("app/src/main/assets/common/dictionaries_serialized")
) -> bool:
    """Build complete dictionary with all steps."""
    
    print(f"\n{'='*60}")
    print(f"Building dictionary for language: {language}")
    print(f"{'='*60}\n")
    
    # Step 1: Download corpora
    if download:
        print("\n[1/4] Downloading corpora...")
        cmd = [
            sys.executable, "tools/dictionaries/download_corpora.py",
            "--language", language,
            "--output-dir", str(corpora_dir),
            "--convert"
        ]
        if not run_command(cmd, "Download corpora"):
            print("Warning: Download failed, continuing with existing files...")
    
    # Step 2: Extract n-grams (if text corpora available)
    if extract_ngrams:
        print("\n[2/4] Extracting n-grams...")
        # Look for text files in corpora directory
        text_files = list(corpora_dir.glob(f"{language}_*.txt"))
        if not text_files:
            print(f"Warning: No text files found for {language} in {corpora_dir}")
            print("Skipping n-gram extraction...")
        else:
            for text_file in text_files[:1]:  # Process first text file found
                bigrams_out = corpora_dir / f"{language}_bigrams.json"
                trigrams_out = corpora_dir / f"{language}_trigrams.json"
                
                cmd = [
                    sys.executable, "tools/dictionaries/extract_ngrams.py",
                    str(text_file),
                    str(bigrams_out),
                    str(trigrams_out),
                    "--min-freq", "2"
                ]
                if not run_command(cmd, f"Extract n-grams from {text_file.name}"):
                    print("Warning: N-gram extraction failed...")
    
    # Step 3: Merge dictionaries
    if merge:
        print("\n[3/4] Merging dictionaries...")
        base_dict = Path(f"app/src/main/assets/common/dictionaries/{language}_base.json")
        downloaded_dicts = list(corpora_dir.glob(f"{language}_*.json"))
        
        if not base_dict.exists():
            print(f"Error: Base dictionary not found: {base_dict}")
            return False
        
        input_files = [base_dict] + [d for d in downloaded_dicts if d.name.endswith('.json') and 'bigram' not in d.name and 'trigram' not in d.name]
        
        if len(input_files) < 2:
            print(f"Warning: Only {len(input_files)} dictionary file(s) found, skipping merge...")
        else:
            merged_output = corpora_dir / f"{language}_merged.json"
            cmd = [
                sys.executable, "tools/dictionaries/merge_dictionaries.py"
            ] + [str(f) for f in input_files] + [
                "--output", str(merged_output),
                "--strategy", "weighted"
            ]
            if not run_command(cmd, "Merge dictionaries"):
                print("Warning: Merge failed, using base dictionary...")
    
    # Step 4: Preprocess (generate .dict file)
    if preprocess:
        print("\n[4/4] Preprocessing dictionary...")
        # Use the merged dictionary if available, otherwise base
        dict_input = corpora_dir / f"{language}_merged.json"
        if not dict_input.exists():
            dict_input = Path(f"app/src/main/assets/common/dictionaries/{language}_base.json")
        
        if not dict_input.exists():
            print(f"Error: No dictionary file found for preprocessing")
            return False
        
        # Load n-gram data if available
        bigrams_file = corpora_dir / f"{language}_bigrams.json"
        trigrams_file = corpora_dir / f"{language}_trigrams.json"
        
        # Note: The preprocessing script needs to be updated to accept n-gram data
        # For now, we'll just run the standard preprocessing
        cmd = [
            "kotlinc", "-script", "tools/dictionaries/preprocess-dictionaries.main.kts",
            str(dict_input)
        ]
        
        print(f"Note: N-gram integration in preprocessing requires script updates")
        print(f"Running standard preprocessing...")
        
        if not run_command(cmd, "Preprocess dictionary"):
            print("Error: Preprocessing failed")
            return False
    
    print(f"\n{'='*60}")
    print("Dictionary building complete!")
    print(f"{'='*60}\n")
    return True


def main():
    parser = argparse.ArgumentParser(description='Build complete dictionary with n-grams')
    parser.add_argument('--language', '-l', required=True,
                       help='Language code (it, en, de, etc.)')
    parser.add_argument('--download', action='store_true',
                       help='Download corpora from online sources')
    parser.add_argument('--extract-ngrams', action='store_true',
                       help='Extract n-grams from text corpora')
    parser.add_argument('--merge', action='store_true',
                       help='Merge multiple dictionary sources')
    parser.add_argument('--preprocess', action='store_true',
                       help='Generate final .dict file')
    parser.add_argument('--all', action='store_true',
                       help='Run all steps (download, extract, merge, preprocess)')
    parser.add_argument('--corpora-dir', type=Path, default=Path("tools/corpora"),
                       help='Directory for downloaded corpora')
    
    args = parser.parse_args()
    
    if args.all:
        args.download = True
        args.extract_ngrams = True
        args.merge = True
        args.preprocess = True
    
    if not any([args.download, args.extract_ngrams, args.merge, args.preprocess]):
        print("Error: No steps specified. Use --all or specify individual steps.")
        return 1
    
    success = build_dictionary(
        args.language,
        download=args.download,
        extract_ngrams=args.extract_ngrams,
        merge=args.merge,
        preprocess=args.preprocess,
        corpora_dir=args.corpora_dir
    )
    
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())

