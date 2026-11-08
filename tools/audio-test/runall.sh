#!/bin/bash
cd "$(dirname "$0")"
for code in "$@"; do
  bash run_lang.sh "$code" 2>&1 | tail -1 >> results.txt
done
echo FINISHED >> results.txt
