#!/bin/bash
# Usage: fetch_fleurs_more.sh [megabytes per language, default 40] <language codes...>
# Downloads the first part of each language's FLEURS test audio archive (Google FLEURS, CC BY 4.0) and
# unpacks the complete clips into fleursbig/<code>/wav, with the transcript list as fleursbig/<code>/test.tsv.
# The archives are in file-name order, so a prefix is a random-looking sample of the recordings.
# Downloaded audio is ignored by git. See docs/09-evaluation-harness.md.
cd "$(dirname "$0")"
MB=40
case "$1" in ''|*[!0-9]*) ;; *) MB=$1; shift ;; esac
[ $# -eq 0 ] && { echo "give language codes, for example: en-us hi es de fr"; exit 2; }
declare -A CFG=([en-us]=en_us [hi]=hi_in [es]=es_419 [de]=de_de [fr]=fr_fr [ta]=ta_in [te]=te_in [ar]=ar_eg [ko]=ko_kr [ru]=ru_ru [pt]=pt_br [it]=it_it [ja]=ja_jp [cn]=cmn_hans_cn)
BASE=https://huggingface.co/datasets/google/fleurs/resolve/main/data
for code in "$@"; do
  cfg=${CFG[$code]}
  [ -z "$cfg" ] && { echo "$code: unknown language"; continue; }
  d=fleursbig/$code; rm -rf "$d"; mkdir -p "$d/x" "$d/wav"
  curl -sL -o "$d/test.tsv" "$BASE/$cfg/test.tsv"
  curl -sL -r 0-$((MB * 1000000)) -o "$d/part.tar.gz" "$BASE/$cfg/audio/test.tar.gz"
  # The last file of a truncated archive is incomplete, so it is left out.
  files=$(tar -tzf "$d/part.tar.gz" 2>/dev/null | grep '\.wav$' | head -n -1)
  (cd "$d/x" && tar -xzf ../part.tar.gz 2>/dev/null)
  for f in $files; do mv "$d/x/$f" "$d/wav/$(basename "$f")" 2>/dev/null; done
  rm -rf "$d/x" "$d/part.tar.gz"
  echo "$code: $(ls "$d/wav" | wc -l) clips, $(du -sh "$d/wav" | cut -f1)"
done
