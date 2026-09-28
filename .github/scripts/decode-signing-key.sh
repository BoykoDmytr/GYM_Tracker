#!/usr/bin/env bash
# Restores the release keystore from the SIGNING_KEYSTORE_BASE64 secret, checks the password and
# the key alias, and exports what the Gradle build needs. Errors are shown on the run page.
set -euo pipefail

keystore="${RUNNER_TEMP:-/tmp}/release-key.p12"
printf '%s' "${SIGNING_KEYSTORE_BASE64:-}" | base64 --decode --ignore-garbage > "$keystore" 2> /dev/null || true

# English output regardless of the runner locale, so the parsing below is stable.
if ! listing=$(keytool -J-Duser.language=en -list -v -keystore "$keystore" -storepass:env SIGNING_KEYSTORE_PASSWORD 2> /dev/null); then
  echo "::error::Не вдалося відкрити ключ підпису. SIGNING_KEYSTORE_BASE64 має містити весь base64-текст файлу ключа, а SIGNING_KEYSTORE_PASSWORD — його пароль (docs/SIGNING.md)."
  exit 1
fi

# Aliases of entries that hold a private key (the only ones that can sign).
mapfile -t keys < <(printf '%s\n' "$listing" | awk '
  /^Alias name: / { alias = substr($0, length("Alias name: ") + 1) }
  /^Entry type: PrivateKeyEntry/ { print alias }')

alias="${SIGNING_KEY_ALIAS:-}"
if [ -z "$alias" ]; then
  if [ "${#keys[@]}" -ne 1 ]; then
    echo "::error::У файлі ключа ${#keys[@]} ключів (${keys[*]:-немає}). Додай секрет SIGNING_KEY_ALIAS з назвою потрібного."
    exit 1
  fi
  alias="${keys[0]}"
else
  # Aliases are case-insensitive in Java keystores; use the stored spelling.
  match=$(printf '%s\n' "${keys[@]}" | grep -Fxi -- "$alias" | head -n 1 || true)
  if [ -z "$match" ]; then
    echo "::error::У файлі ключа немає ключа «$alias». Доступні: ${keys[*]:-немає}. Виправ або видали секрет SIGNING_KEY_ALIAS."
    exit 1
  fi
  alias="$match"
fi

echo "SIGNING_KEYSTORE_FILE=$keystore" >> "$GITHUB_ENV"
echo "SIGNING_KEY_ALIAS=$alias" >> "$GITHUB_ENV"
echo "Підпис ключем «$alias»"
