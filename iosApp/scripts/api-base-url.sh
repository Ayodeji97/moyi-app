#!/bin/sh
# The API base URL of an iOS build (CLAUDE.md, Configuration; README, "Which backend the
# app talks to"). Run by the "API Base URL" build phase of iosApp, and by CI.
#
#   Debug   the LOCAL backend: http://localhost:8080/api/v1, or `moyi.apiBaseUrl` from the
#           root local.properties (the same override Android reads). A production host
#           fails the build. Allows local networking (ATS NSAllowsLocalNetworking) and
#           carries the Local Network prompt's text, which plain-HTTP local servers need.
#   Release production, always: https://api.getmoyi.app/api/v1. The override is ignored,
#           and the build has no ATS exception: HTTPS only.
#
# Usage:
#   api-base-url.sh            Xcode build phase: writes the URL (key MoyiApiBaseUrl) and the
#                              Debug-only network keys into the built app's Info.plist.
#   api-base-url.sh resolve    prints the URL for $CONFIGURATION and checks it; writes
#                              nothing. CI runs this (iosApp/scripts/test-api-base-url.sh).
#
# Environment: CONFIGURATION (Debug or Release). For the build phase also TARGET_BUILD_DIR
# and INFOPLIST_PATH, which Xcode sets. MOYI_LOCAL_PROPERTIES replaces the path of
# local.properties (tests only).
set -eu

PRODUCTION_API_BASE_URL="https://api.getmoyi.app/api/v1" # the same as build-logic ApiBaseUrl.kt
DEBUG_DEFAULT_API_BASE_URL="http://localhost:8080/api/v1"
# The same list as build-logic ProductionHosts.kt: change both together.
PRODUCTION_IPS="91.98.71.136"
LOCAL_NETWORK_USAGE="Moyi connects to a development server on your local network."

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
LOCAL_PROPERTIES=${MOYI_LOCAL_PROPERTIES:-"$ROOT/local.properties"}

fail() {
  # "error:" is what Xcode shows as a build error.
  echo "error: $1" >&2
  exit 1
}

# moyi.apiBaseUrl from local.properties, or nothing. The last line wins, as in Java's Properties.
override_url() {
  [ -f "$LOCAL_PROPERTIES" ] || return 0
  grep -E '^[[:space:]]*moyi\.apiBaseUrl[[:space:]]*[=:]' "$LOCAL_PROPERTIES" | tail -n 1 |
    sed -E 's/^[[:space:]]*moyi\.apiBaseUrl[[:space:]]*[=:][[:space:]]*//; s/[[:space:]]+$//'
}

# The host of an http(s) URL, lower case, without a port, user or trailing dot.
host_of() {
  printf '%s' "$1" | sed -E 's#^[A-Za-z][A-Za-z0-9+.-]*://##; s#[/?\#].*$##; s#^.*@##; s#:[0-9]*$##; s#\.$##' |
    tr '[:upper:]' '[:lower:]'
}

is_production_host() {
  case "$1" in
    getmoyi.app | *.getmoyi.app) return 0 ;;
  esac
  for ip in $PRODUCTION_IPS; do
    [ "$1" = "$ip" ] && return 0
  done
  return 1
}

check_debug_url() {
  url=$1
  source=$2
  case "$url" in
    http://* | https://*) ;;
    *) fail "moyi.apiBaseUrl from $source is not an http(s) URL with a host: '$url'" ;;
  esac
  host=$(host_of "$url")
  [ -n "$host" ] || fail "moyi.apiBaseUrl from $source is not an http(s) URL with a host: '$url'"
  if is_production_host "$host"; then
    fail "moyi.apiBaseUrl from $source points at production ($host). Development builds never talk to getmoyi.app: real people's data lives there (CLAUDE.md, Configuration). Point it at a local backend, e.g. http://localhost:8080/api/v1, or remove the override. Only a Release build talks to production."
  fi
  case "${url%/}" in
    */api/v1) ;;
    *) fail "moyi.apiBaseUrl from $source must end in /api/v1, the same form as Bruno and doc 06: '$url'" ;;
  esac
}

resolve() {
  case "${CONFIGURATION:-}" in
    Debug)
      override=$(override_url)
      if [ -n "$override" ]; then
        check_debug_url "$override" "local.properties"
        echo "$override"
      else
        check_debug_url "$DEBUG_DEFAULT_API_BASE_URL" "the iOS default"
        echo "$DEBUG_DEFAULT_API_BASE_URL"
      fi
      ;;
    Release)
      echo "$PRODUCTION_API_BASE_URL"
      ;;
    *)
      fail "Unknown build configuration '${CONFIGURATION:-}': only Debug and Release have an API base URL (iosApp/scripts/api-base-url.sh)."
      ;;
  esac
}

URL=$(resolve)

if [ "${1:-}" = "resolve" ]; then
  echo "$URL"
  exit 0
fi

PLIST="${TARGET_BUILD_DIR:?}/${INFOPLIST_PATH:?}"
[ -f "$PLIST" ] || fail "No built Info.plist at $PLIST: the API Base URL phase ran before Xcode processed it."
BUDDY=/usr/libexec/PlistBuddy

# Delete, then Add: the same result whether or not an earlier build left the key.
"$BUDDY" -c "Delete :MoyiApiBaseUrl" "$PLIST" >/dev/null 2>&1 || true
"$BUDDY" -c "Add :MoyiApiBaseUrl string $URL" "$PLIST"
"$BUDDY" -c "Delete :NSAppTransportSecurity" "$PLIST" >/dev/null 2>&1 || true
"$BUDDY" -c "Delete :NSLocalNetworkUsageDescription" "$PLIST" >/dev/null 2>&1 || true
if [ "$CONFIGURATION" = "Debug" ]; then
  "$BUDDY" -c "Add :NSAppTransportSecurity dict" "$PLIST"
  "$BUDDY" -c "Add :NSAppTransportSecurity:NSAllowsLocalNetworking bool true" "$PLIST"
  "$BUDDY" -c "Add :NSLocalNetworkUsageDescription string $LOCAL_NETWORK_USAGE" "$PLIST"
fi

# Read it back: what ships is what was checked.
WRITTEN=$("$BUDDY" -c "Print :MoyiApiBaseUrl" "$PLIST")
[ "$WRITTEN" = "$URL" ] || fail "Info.plist holds MoyiApiBaseUrl '$WRITTEN', expected '$URL'."
if [ "$CONFIGURATION" = "Release" ] && "$BUDDY" -c "Print :NSAppTransportSecurity" "$PLIST" >/dev/null 2>&1; then
  fail "The Release Info.plist still has an ATS exception (NSAppTransportSecurity)."
fi
echo "API base URL for $CONFIGURATION: $URL"
