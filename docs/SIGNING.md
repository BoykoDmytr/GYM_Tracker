# Підпис APK і оновлення

Android оновлює застосунок лише тоді, коли нова версія підписана **тим самим ключем**, що й
встановлена. Інакше буде «Застосунок не встановлено», і лишиться тільки видалити стару версію —
разом з усією історією тренувань.

Тому release-збірки в GitHub Actions підписуються твоїм приватним ключем, який лежить у секретах
репозиторію. У самому репозиторії ключа немає: репозиторій публічний, і ключ у ньому дозволив би
будь-кому зібрати «оновлення» твого застосунку з доступом до твоїх даних.

## Поки ключа немає

Збірка все одно працює: APK підписується тимчасовим debug-ключем і лежить в **Artifacts** запуску.
Цього досить, щоб подивитися застосунок. Але debug-ключ на GitHub щоразу новий, тож наступну збірку
не вийде встановити поверх попередньої без видалення. Не веди в ній справжні тренування.
У Releases такі збірки не публікуються.

## Налаштування ключа (один раз, ~5 хвилин)

Потрібен `keytool`: він є в будь-якому JDK і в Android Studio
(Windows: `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`).

1. Створи ключ. Команда запитає пароль — придумай надійний і збережи в менеджері паролів:
   ```
   keytool -genkeypair -keystore gym-tracker.p12 -storetype PKCS12 -alias gymtracker -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Gym Tracker"
   ```
2. Перетвори файл на текст base64 (скопіюється в буфер обміну):
   - Windows (PowerShell): `[Convert]::ToBase64String([IO.File]::ReadAllBytes("gym-tracker.p12")) | Set-Clipboard`
   - macOS: `base64 -i gym-tracker.p12 | pbcopy`
   - Linux: `base64 -w0 gym-tracker.p12` (скопіюй вивід)
3. GitHub → репозиторій → **Settings → Secrets and variables → Actions → New repository secret**,
   два секрети:
   - `SIGNING_KEYSTORE_BASE64` — текст із кроку 2;
   - `SIGNING_KEYSTORE_PASSWORD` — пароль із кроку 1.
4. **Actions → Android build → Run workflow**. Коли всі кроки зелені, APK зʼявиться в Releases.

Файл `gym-tracker.p12` і пароль збережи в надійному місці. Якщо їх втратити, наступну версію не
вийде встановити поверх — лише з видаленням даних. Не клади файл у репозиторій (`*.p12` уже в
`.gitignore`).

Ключ можна створити й в Android Studio (*Build → Generate Signed App Bundle or APK → Create new*):
назву ключа (alias) збірка визначить сама, якщо він у файлі один. Якщо там задано окремий пароль
ключа, відмінний від пароля файлу, додай ще секрет `SIGNING_KEY_PASSWORD`. Якщо щось не так
(пароль, base64 чи назва), крок *Decode the release signing key* у запуску прямо напише, що саме.

## Встановлення на телефон

- Остання опублікована версія (після налаштування ключа):
  https://github.com/BoykoDmytr/GYM_Tracker/releases/latest/download/gym-tracker.apk
- Будь-яка збірка: **Actions →** потрібний запуск **→ Artifacts → `gym-tracker-apk`**
  (zip, усередині APK; потрібен вхід у GitHub).

Під час першого встановлення Android попросить дозволити встановлення з браузера або файлового
менеджера. Якщо раніше ставив тестову (debug) збірку, спершу видали її: у підписаної версії інший
ключ.
