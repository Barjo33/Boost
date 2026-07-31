#!/bin/bash
# Side-by-side cadence analysis. Pass a CSV, or omit to run against the test fixture.
set -e
cd "$(dirname "$0")"
DATA="${1:-fixture_sidebyside.csv}"
for s in 01_qc_and_null 02_variogram_paired 03_prediction_paired; do
  echo "=== $s ==="
  python3 $s.py "$DATA"
done
