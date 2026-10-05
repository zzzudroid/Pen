# CLAUDE.md

## О проекте

Pen — личное Android-приложение для рукописного ввода стилусом на Xiaomi Pad 7 (HyperOS 2 / Android 15)
с Xiaomi Focus Pen. Проектный документ — `docs/design.md` (после правок пересобрать HTML:
`python tools/md2html.py docs/design.md`).

## Правила работы

- Вся работа — через OpenSpec (`openspec/changes/`, скиллы `opsx:*`); найденные follow-up оформлять новым change.
- После правок кода собирать APK самому, не просить пользователя.
- Баги ввода пера — сначала факты с устройства (экран диагностики, `adb logcat -s PenDiag`), потом код.
- Комментарии в коде, UI, документация, коммиты — на русском.
- Разработка идёт на Windows и Linux: машинно-зависимое — только в `local.properties` / `keystore.properties` (оба не в git).
- Каждое изменение отражать в `CHANGELOG.md`.

## Команды

```bash
./gradlew assembleDebug          # Windows: .\gradlew.bat assembleDebug
./gradlew testDebugUnitTest      # юнит-тесты (чистый JVM)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s PenDiag
```

## Архитектура (сейчас)

- Один модуль `app`, пакет `com.zzzudroid.pen`, minSdk = targetSdk = 35.
- `MainActivity` перехватывает сырые `MotionEvent` (`dispatchTouchEvent` / `dispatchGenericMotionEvent`)
  и `KeyEvent` до Compose и передаёт в `DiagnosticsViewModel` — так видны hover и кнопки пера
  независимо от Compose-обёртки.
- `diag/PenStats.kt` — чистая логика сводки (без Android), покрыта JVM-тестами.
- `diag/PenInputView.kt` — тестовая зона (обычный `View` в `AndroidView`, отрисовка через Bitmap).
- `BuildConfig.BUILD_TIME / GIT_HASH / GIT_DIRTY` — для экрана «О программе»; генерация BuildConfig
  никогда не считается up-to-date.
