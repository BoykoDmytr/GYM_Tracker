#!/usr/bin/env bash
# Runs the on-device tests (app/src/androidTest) on the CI emulator. They need the debug build, which
# is signed with another key than the release APK the smoke test installed, so that one goes first.
set -uo pipefail

APK_DIR="${1:-instrumented}"
PACKAGE=com.boykodmytr.gymtracker
OUT=smoke
mkdir -p "$OUT"

adb uninstall "$PACKAGE" > /dev/null 2>&1 || true
adb install -r "$APK_DIR/app-debug.apk" || { echo "::error::Не вдалося встановити debug APK"; exit 1; }
adb install -r "$APK_DIR/app-debug-androidTest.apk" || { echo "::error::Не вдалося встановити тестовий APK"; exit 1; }

# "am instrument" exits with 0 even when tests fail, so the result is read from its output.
adb shell am instrument -w -r "$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrumented.txt"
if grep -q "^OK (" "$OUT/instrumented.txt" && ! grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$OUT/instrumented.txt"; then
  echo "==> Instrumented tests passed"
else
  echo "::error::Тести на пристрої не пройшли (див. smoke/instrumented.txt в Artifacts)"
  exit 1
fi
