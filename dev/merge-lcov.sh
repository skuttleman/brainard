#!/usr/bin/env bash
set -euo pipefail

COVERAGE_DIR=${1:-target/coverage}
MERGED_DIR=${2:-target/coverage/merged}

files=$(find "$COVERAGE_DIR" -type f -name 'lcov.info' -not -path '*/merged/*' -print)
if [ -z "$files" ]; then
  echo "No coverage files found";
  exit 0;
fi

# Determine workspace root (git rev-parse --show-toplevel), fall back to cwd
workspace=$(git rev-parse --show-toplevel 2>/dev/null || echo "$PWD")

rm -rf "$MERGED_DIR" && mkdir -p "$MERGED_DIR"

for f in $files; do
  tmp=$(mktemp)
  current_lines=0
  while IFS= read -r line; do
    if [[ "$line" == SF:* ]]; then
      sf="${line#SF:}"
      # Strip workspace prefix if present
      if [[ "$sf" == "$workspace"/* ]]; then
        sf="${sf#$workspace/}"
      fi

      if [[ "$sf" == resources/public/js/cljs-runtime/* ]]; then
        rel="${sf#resources/public/js/cljs-runtime/}"
        resolved="$rel"
        for srcdir in src modules/api/src modules/infra/src; do
          if [ -f "$srcdir/$rel" ]; then
            resolved="$srcdir/$rel"
            break
          fi
        done
        echo "SF:$resolved" >> "$tmp"
        if [ -f "$resolved" ]; then current_lines=$(wc -l < "$resolved"); else current_lines=0; fi
      else
        echo "SF:$sf" >> "$tmp"
        if [ -f "$sf" ]; then current_lines=$(wc -l < "$sf"); else current_lines=0; fi
      fi
    elif [[ "$line" == DA:* ]]; then
      if [ "$current_lines" -gt 0 ]; then
        lineno=$(echo "$line" | cut -d: -f2 | cut -d, -f1)
        if [ "$lineno" -le "$current_lines" ]; then
          echo "$line" >> "$tmp"
        fi
      else
        echo "$line" >> "$tmp"
      fi
    else
      echo "$line" >> "$tmp"
    fi
  done < "$f"
  mv "$tmp" "$f"
done

first=$(echo "$files" | head -n1)
lcov -a "$first" -o "$MERGED_DIR/merged.info"
echo "$files" | tail -n +2 | while read ff; do lcov -a "$MERGED_DIR/merged.info" -a "$ff" -o "$MERGED_DIR/merged.info"; done

# Final normalization: make SF lines repo-relative when possible.
# This removes any leading path up to the repository root so genhtml won't recreate absolute filesystem trees.
awk -v repo="$workspace" 'BEGIN{rlen=length(repo)} \
  /^SF:/ { sf=substr($0,4); i=index(sf,repo); if(i) { sf=substr(sf,i+rlen+1) } sub("^/","",sf); print "SF:"sf; next } \
  { print }' "$MERGED_DIR/merged.info" > "$MERGED_DIR/merged.normalized.info" && mv "$MERGED_DIR/merged.normalized.info" "$MERGED_DIR/merged.info"

cp "$MERGED_DIR/merged.info" "merged.info"
genhtml -p $(git rev-parse --show-toplevel) --ignore-errors category -o "$MERGED_DIR" "$MERGED_DIR/merged.info"

echo "Merged coverage written to $MERGED_DIR"
