#!/bin/bash
# For each app language code, fetch one real recording and its transcript from Google FLEURS
# (CC BY 4.0). Only the first ~2.5 MB of each audio archive is downloaded.
cd "$(dirname "$0")"
PAIRS="en-us:en_us hi:hi_in ta:ta_in te:te_in mr:mr_in bn:bn_in kn:kn_in ml:ml_in gu:gu_in pa:pa_in ar:ar_eg ko:ko_kr fr:fr_fr es:es_419 de:de_de ru:ru_ru cn:cmn_hans_cn ja:ja_jp pt:pt_br tr:tr_tr vn:vi_vn it:it_it nl:nl_nl el:el_gr pl:pl_pl sv:sv_se uk:uk_ua fa:fa_ir"
BASE=https://huggingface.co/datasets/google/fleurs/resolve/main/data
for pair in $PAIRS; do
  code=${pair%%:*}; cfg=${pair##*:}
  d=fleurs/$code; rm -rf "$d"; mkdir -p "$d/x"
  curl -sL -o "$d/test.tsv" "$BASE/$cfg/test.tsv"
  curl -sL -r 0-2500000 -o "$d/part.tar.gz" "$BASE/$cfg/audio/test.tar.gz"
  (cd "$d/x" && tar -xzf ../part.tar.gz 2>/dev/null)
  # The last file in a truncated archive is incomplete, so leave it out and take the smallest remaining one.
  files=$(cd "$d/x" && tar -tzf ../part.tar.gz 2>/dev/null | grep '\.wav$' | head -n -1)
  best=""; bestsize=999999999
  for f in $files; do
    s=$(stat -c %s "$d/x/$f" 2>/dev/null || echo 0)
    if [ "$s" -gt 120000 ] && [ "$s" -lt "$bestsize" ]; then best=$f; bestsize=$s; fi
  done
  if [ -z "$best" ]; then echo "$code: NO AUDIO"; continue; fi
  cp "$d/x/$best" "$d/sample.wav"
  name=$(basename "$best")
  awk -F'\t' -v n="$name" '$2==n {print $3; exit}' "$d/test.tsv" > "$d/lyrics.txt"
  awk -F'\t' -v n="$name" '$2==n {print $4; exit}' "$d/test.tsv" > "$d/normalized.txt"
  rm -rf "$d/x" "$d/part.tar.gz"
  echo "$code: $(stat -c %s "$d/sample.wav") bytes  $(head -c 60 "$d/lyrics.txt")"
done
