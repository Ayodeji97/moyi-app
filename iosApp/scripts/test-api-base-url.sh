#!/bin/sh
# Checks iosApp/scripts/api-base-url.sh without Xcode, so CI (Linux) covers iOS too:
# Release is exactly production over HTTPS, whatever local.properties says; Debug is local
# by default, takes a local override, and refuses every production host.
set -eu

SCRIPT="$(dirname -- "$0")/api-base-url.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
PROPS="$WORK/local.properties"
failures=0

expect_url() { # name configuration local.properties-line expected-url
  printf '%s\n' "$3" >"$PROPS"
  actual=$(CONFIGURATION=$2 MOYI_LOCAL_PROPERTIES=$PROPS ${SH:-sh} "$SCRIPT" resolve 2>&1) || actual="FAILED: $actual"
  if [ "$actual" = "$4" ]; then echo "ok    $1"; else echo "FAIL  $1: got '$actual', expected '$4'"; failures=$((failures + 1)); fi
}

expect_failure() { # name configuration local.properties-line
  printf '%s\n' "$3" >"$PROPS"
  if out=$(CONFIGURATION=$2 MOYI_LOCAL_PROPERTIES=$PROPS ${SH:-sh} "$SCRIPT" resolve 2>&1); then
    echo "FAIL  $1: accepted, printed '$out'"
    failures=$((failures + 1))
  else
    echo "ok    $1"
  fi
}

PROD="https://api.getmoyi.app/api/v1"

expect_url "Release is production" Release "" "$PROD"
expect_url "Release ignores a local override" Release "moyi.apiBaseUrl=http://localhost:8080/api/v1" "$PROD"
expect_url "Debug defaults to localhost" Debug "" "http://localhost:8080/api/v1"
expect_url "Debug takes a local override" Debug "moyi.apiBaseUrl=http://mac.local:8080/api/v1" "http://mac.local:8080/api/v1"
expect_url "Debug takes the last override, spaces trimmed" Debug "$(printf 'moyi.apiBaseUrl=http://a:1/api/v1\nmoyi.apiBaseUrl = http://b:2/api/v1 ')" "http://b:2/api/v1"
expect_url "Debug ignores other keys" Debug "moyi.devMediaHosts=192.168.1.70" "http://localhost:8080/api/v1"

expect_failure "Debug refuses production" Debug "moyi.apiBaseUrl=$PROD"
expect_failure "Debug refuses the apex" Debug "moyi.apiBaseUrl=https://getmoyi.app/api/v1"
expect_failure "Debug refuses upper case" Debug "moyi.apiBaseUrl=https://API.GetMoyi.App/api/v1"
expect_failure "Debug refuses a trailing dot" Debug "moyi.apiBaseUrl=https://api.getmoyi.app./api/v1"
expect_failure "Debug refuses a port" Debug "moyi.apiBaseUrl=http://api.getmoyi.app:8080/api/v1"
expect_failure "Debug refuses production's IP" Debug "moyi.apiBaseUrl=http://91.98.71.136/api/v1"
expect_failure "Debug refuses a user part" Debug "moyi.apiBaseUrl=https://x@api.getmoyi.app/api/v1"
expect_failure "Debug refuses a URL without /api/v1" Debug "moyi.apiBaseUrl=http://localhost:8080"
expect_failure "Debug refuses a URL without a scheme" Debug "moyi.apiBaseUrl=localhost:8080/api/v1"
expect_failure "An unknown configuration fails" Staging ""

[ "$failures" -eq 0 ] || { echo "$failures check(s) failed"; exit 1; }
echo "All API base URL checks passed."
