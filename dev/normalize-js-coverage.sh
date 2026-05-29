#!/usr/bin/env bash
set -euo pipefail

# Normalize JS coverage lcov paths produced by nyc/karma so they point
# back to the original source files in the repo for merging with cloverage.

echo "Normalizing JS coverage source paths..."
if [ -f target/coverage/js/lcov.info ]; then
  src_dirs=("src" "modules/api/src" "modules/infra/src")
  cljs_prefix="resources/public/js/cljs-runtime/"
  current_file=""
  current_lines=0

  while IFS= read -r line; do
    if [[ "$line" == SF:* ]]; then
      rel="${line#SF:}"
      rel="${rel#${cljs_prefix}}"
      resolved="${rel}"
      for srcdir in "${src_dirs[@]}"; do
        if [ -f "$srcdir/$rel" ]; then
          resolved="$srcdir/$rel"
          break
        fi
      done
      current_file="$resolved"
      current_lines=0
      if [ -f "$resolved" ]; then current_lines=$(wc -l < "$resolved"); fi
      echo "SF:$resolved"
    elif [[ "$line" == DA:* && $current_lines -gt 0 ]]; then
      lineno="$(echo "$line" | cut -d: -f2 | cut -d, -f1)"
      if [ "$lineno" -le "$current_lines" ]; then echo "$line"; fi
    else
      echo "$line"
    fi
  done < target/coverage/js/lcov.info > target/coverage/js/lcov.normalized.info

  mv target/coverage/js/lcov.normalized.info target/coverage/js/lcov.info
  echo "JS coverage paths normalized"
else
  echo "No JS coverage report generated (skipping)"
fi
