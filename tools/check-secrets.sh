#!/usr/bin/env bash
# Refuses anything that looks like a secret or a personal detail before it reaches git.
# Runs on the staged diff by default (pre-commit), or on the whole tracked tree with --all (CI).
# Exit 1 on a hit. Patterns, not a scanner: gitleaks in CI is the second line.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
if [ "${1:-}" = "--all" ]; then
  files=$(git ls-files)
  content() { cat -- $files 2>/dev/null; }
else
  files=$(git diff --cached --name-only --diff-filter=ACMR)
  content() { git diff --cached -U0 -- $files 2>/dev/null | grep -E '^\+' | grep -v -E '^\+\+\+' || true; }
fi
# the checker itself carries the patterns it looks for
files=$(printf "%s\n" $files | grep -v -x "tools/check-secrets.sh" || true)
[ -z "$files" ] && exit 0
status=0
# 1. file names that must never be committed
for f in $files; do
  case "$f" in
    *.jks|*.keystore|*keystore.properties|config.json|private/*|*.pem|*.p12|local.properties)
      echo "BLOCKED: $f must not be committed (see .gitignore and SECURITY.md)"; status=1;;
  esac
done
# 2. content patterns: iCal secret addresses, Google API keys, private keys, LAN addresses, Wi-Fi names, emails
patterns='calendar\.google\.com/calendar/ical/[^/]+/private-[0-9a-f]{6,}|AIza[0-9A-Za-z_-]{30,}|-----BEGIN [A-Z ]*PRIVATE KEY-----|\b192\.168\.[0-9]{1,3}\.[0-9]{1,3}\b|\b10\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\b|[A-Za-z0-9._%+-]+@(gmail|ymail|yahoo|outlook|hotmail|icloud)\.com|storePassword=|keyPassword='
if content | grep -n -E -i "$patterns" | grep -v -E 'REPLACE|example|placeholder' | head -10 | sed 's/^/HIT: /' | grep .; then
  echo "BLOCKED: the lines above look like secrets or personal details. Move them to config.json or a private file."; status=1
fi
exit $status
