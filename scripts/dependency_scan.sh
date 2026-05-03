#!/usr/bin/env bash
set -euo pipefail

cd /home/micu/clojure

OSV_SCANNER="${OSV_SCANNER:-/home/micu/go/bin/osv-scanner}"
REPORT_DIR="${REPORT_DIR:-logs/security}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
table_report="$REPORT_DIR/osv-$timestamp.log"
json_report="$REPORT_DIR/osv-$timestamp.json"
tree_report="$REPORT_DIR/deps-tree-$timestamp.log"
had_existing_pom=0

mkdir -p "$REPORT_DIR"

if [[ ! -x "$OSV_SCANNER" ]]; then
  echo "osv-scanner not found or not executable at $OSV_SCANNER" | tee "$table_report"
  exit 2
fi

if [[ -f pom.xml ]]; then
  had_existing_pom=1
  cp pom.xml "$REPORT_DIR/pom-$timestamp.xml"
fi

cleanup() {
  if [[ "$had_existing_pom" = "0" ]]; then
    rm -f pom.xml
  fi
}
trap cleanup EXIT

echo "Clojure EventPulse dependency scan" | tee "$table_report"
echo "Timestamp: $timestamp" | tee -a "$table_report"
echo "Scanner: $("$OSV_SCANNER" --version | head -n 1)" | tee -a "$table_report"
echo | tee -a "$table_report"

lein pom >/dev/null
lein deps :tree > "$tree_report"

scan_status=0
"$OSV_SCANNER" scan source --data-source native --lockfile pom.xml --format table >> "$table_report" 2>&1 || scan_status=$?
"$OSV_SCANNER" scan source --data-source native --lockfile pom.xml --format json --output-file "$json_report" >/dev/null 2>&1 || true

ln -sfn "$(basename "$table_report")" "$REPORT_DIR/latest-osv.log"
ln -sfn "$(basename "$json_report")" "$REPORT_DIR/latest-osv.json"
ln -sfn "$(basename "$tree_report")" "$REPORT_DIR/latest-deps-tree.log"

find "$REPORT_DIR" -type f -name 'osv-*.log' -mtime +180 -delete
find "$REPORT_DIR" -type f -name 'osv-*.json' -mtime +180 -delete
find "$REPORT_DIR" -type f -name 'deps-tree-*.log' -mtime +180 -delete

if [[ "$scan_status" -ne 0 ]]; then
  echo "Dependency scan completed with findings or errors. See $table_report" >&2
  exit "$scan_status"
fi

echo "Dependency scan completed without known vulnerabilities. See $table_report"
