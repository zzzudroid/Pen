# Pen

Личное Android-приложение для рукописного ввода стилусом на Xiaomi Pad 7 + Xiaomi Focus Pen:
заметки, разметка PDF, бесконечная доска, рисование. Проектный документ — [docs/design.md](docs/design.md).

Сейчас готово:
- **Холст** — рисование пером с низкой задержкой (androidx.ink), ручка/маркер/выделитель, палитра,
  штриховой ластик (в т. ч. кнопкой пера), отмена/повтор, масштаб и сдвиг, защита от ладони.
  Жесты: один палец — сдвиг, два — масштаб, тап двумя — отмена, тремя — повтор. Штрихи пока не сохраняются.
- **Диагностика пера** — живые значения давления/наклона/кнопок/hover, сводка за сессию, тестовая зона и JSON-отчёт.
- «О программе» — версия, время сборки, git-хэш.

## Требования

- JDK 17 (`JAVA_HOME`)
- Android SDK с platform 35 и build-tools 35
- `local.properties` в корне (не в git) с путём к SDK:

```properties
# Windows
sdk.dir=C:/Users/<user>/AppData/Local/Android/Sdk
# Linux
sdk.dir=/home/<user>/Android/Sdk
```

## Сборка и тесты

```bash
# Linux
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # юнит-тесты (JVM)
```

```powershell
# Windows (PowerShell)
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

## Установка на планшет

1. На планшете: «Настройки → О планшете» — 7 раз нажать на «Версия ОС», затем
   «Расширенные настройки → Для разработчиков» → включить «Отладка по USB»
   (на HyperOS также «Установка через USB»).
2. Подключить по USB и подтвердить отладку на планшете.
3. Установить:

```bash
adb devices                                            # планшет должен быть в списке
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Без провода (Wi‑Fi‑отладка)

1. «Для разработчиков → Отладка по Wi‑Fi» → «Подключить по коду».
2. На ПК: `adb pair <ip>:<порт-сопряжения>` и ввести код.
3. `adb connect <ip>:<порт>` (порт — с главного экрана «Отладка по Wi‑Fi»), дальше `adb install` как обычно.

## Диагностика пера

1. Открыть «Диагностика пера», провести штрихи от самого лёгкого до самого сильного нажима,
   наклонять перо, подержать его над экраном, понажимать кнопки.
2. Нажать «Сохранить отчёт» (иконка дискеты) — JSON ляжет в `Documents/Pen/diagnostics/`.
3. Забрать на ПК:

```bash
adb pull /sdcard/Documents/Pen/diagnostics/ .
```

Живой журнал событий: `adb logcat -s PenDiag` (движения пишутся с уровнем `V`, остальное — `D`).

## Release

Подпись release читается из `keystore.properties` в корне (в `.gitignore`):

```properties
storeFile=app/release.keystore
storePassword=...
keyAlias=pen
keyPassword=...
```

```bash
./gradlew assembleRelease
```
