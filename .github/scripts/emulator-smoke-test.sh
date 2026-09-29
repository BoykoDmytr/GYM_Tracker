#!/usr/bin/env bash
# Installs the release APK on the CI emulator, opens every tab and starts a workout.
# Fails when the app crashes, e.g. because R8 removed something that is only used at runtime.
set -uo pipefail

APK_DIR="${1:-dist}"
PACKAGE=com.boykodmytr.gymtracker
OUT=smoke
mkdir -p "$OUT"
APK=$(ls "$APK_DIR"/*.apk | head -n 1)

log() { echo "==> $*"; }

screenshot() { adb exec-out screencap -p > "$OUT/$1.png" || true; }

fail() {
  echo "::error::$1"
  screenshot failure
  adb logcat -d -b crash > "$OUT/crash.txt" 2>&1 || true
  adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
  cat "$OUT/crash.txt"
  exit 1
}

check_alive() {
  adb shell pidof "$PACKAGE" > /dev/null || fail "Застосунок не працює після кроку «$1» (ймовірно, впав)"
  if adb logcat -d -b crash | grep -q "$PACKAGE"; then
    fail "Падіння на кроці «$1»"
  fi
}

# Taps the first on-screen element whose text equals one of the arguments; $TAP_ATTEMPTS tries.
tap_text() {
  local attempts="${TAP_ATTEMPTS:-8}" point
  for _ in $(seq 1 "$attempts"); do
    adb shell uiautomator dump /sdcard/window.xml > /dev/null 2>&1
    adb pull /sdcard/window.xml "$OUT/window.xml" > /dev/null 2>&1
    point=$(python3 .github/scripts/find_text.py "$OUT/window.xml" "$@")
    if [ -n "$point" ]; then
      log "tap '$1' at $point"
      # shellcheck disable=SC2086 # "x y" must split into two arguments
      adb shell input tap $point
      return 0
    fi
    sleep 2
  done
  return 1
}

# Like tap_text, but swipes the screen up between attempts to reach items further down a list.
scroll_tap_text() {
  local size width height point
  size=$(adb shell wm size | sed -n 's/^Physical size: //p' | tr -d '\r')
  width=${size%x*}
  height=${size#*x}
  for _ in $(seq 1 8); do
    adb shell uiautomator dump /sdcard/window.xml > /dev/null 2>&1
    adb pull /sdcard/window.xml "$OUT/window.xml" > /dev/null 2>&1
    point=$(python3 .github/scripts/find_text.py "$OUT/window.xml" "$@")
    if [ -n "$point" ]; then
      log "tap '$1' at $point"
      # shellcheck disable=SC2086 # "x y" must split into two arguments
      adb shell input tap $point
      return 0
    fi
    adb shell input swipe $((width / 2)) $((height * 3 / 4)) $((width / 2)) $((height / 4)) 300
    sleep 1
  done
  return 1
}

log "Installing $APK"
adb install -r "$APK" || fail "Не вдалося встановити APK"
# The first launch asks for notifications; grant it up front so no system dialog covers the app.
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb logcat -c

adb shell am start -W -n "$PACKAGE/.MainActivity" || fail "Не вдалося запустити застосунок"
sleep 8
check_alive "запуск"
screenshot 01-home

index=2
for tab in "Історія" "Статистика" "Програми" "Профіль" "Головна"; do
  tap_text "$tab" || fail "Не знайдено вкладку «$tab»"
  sleep 3
  check_alive "$tab"
  screenshot "0$index-tab"
  index=$((index + 1))
done

# CSV export and import screens (new code, checked in the minified build).
tap_text "Налаштування" || fail "Не знайдено кнопку налаштувань"
sleep 3
check_alive "налаштування"
scroll_tap_text "Експорт даних" || fail "Не знайдено пункт «Експорт даних»"
sleep 3
check_alive "експорт даних"
screenshot 07-export
adb shell input keyevent KEYCODE_BACK
sleep 2
scroll_tap_text "Імпорт даних" || fail "Не знайдено пункт «Імпорт даних»"
sleep 3
check_alive "імпорт даних"
TAP_ATTEMPTS=3 tap_text "Вибрати CSV-файл" || fail "Немає кнопки вибору CSV-файлу"
sleep 3
screenshot 08-import-picker
# Close the system file picker, then the import screen and settings.
adb shell input keyevent KEYCODE_BACK
sleep 2
check_alive "вибір файлу"
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
check_alive "повернення з налаштувань"

tap_text "Почати тренування" "Почати позапланове тренування" || fail "Немає кнопки старту тренування"
sleep 3
check_alive "перегляд тренування"
screenshot 09-preview

tap_text "Почати" || fail "Немає кнопки «Почати»"
sleep 4
check_alive "старт тренування"
screenshot 10-workout

# The workout screen redraws its clock every second, so UiAutomator may never see it idle.
# Logging a set is attempted, but its absence is not a failure.
if TAP_ATTEMPTS=3 tap_text "Завершити підхід"; then
  sleep 2
  if TAP_ATTEMPTS=3 tap_text "Зберегти"; then
    sleep 3
    check_alive "запис підходу"
    screenshot 11-rest
  fi
else
  log "Workout screen never went idle for UiAutomator; skipping set logging"
fi

adb shell input keyevent KEYCODE_BACK
sleep 3
check_alive "повернення на головну"
screenshot 12-home-active

log "Posted notifications:"
adb shell dumpsys notification --noredact | grep "pkg=$PACKAGE" | head -5 || true
log "Smoke test passed"
