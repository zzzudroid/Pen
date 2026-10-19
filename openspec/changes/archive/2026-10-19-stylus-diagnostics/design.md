## Context

Репозиторий пуст, это первый код проекта. Цели и стек описаны в `docs/design.md`; мотивация — в `proposal.md`. Устройство одно (Xiaomi Pad 7), приложение только для себя, поэтому совместимость со старыми версиями Android и требования магазина не учитываются.

## Goals / Non-Goals

**Goals:**
- Рабочий каркас, на который дальше ляжет `canvas-core` без переделки.
- Максимально «сырые» данные пера — без сглаживания и интерпретации.

**Non-Goals:**
- Низкая задержка, Ink API, front-buffered rendering — это `canvas-core`; тестовая зона намеренно простая.
- Хранилище документов, тетради, синхронизация.

## Decisions

1. **Один модуль `app`, пакет `com.zzzudroid.pen`, сборка по образцу moibabki-android и VoiceTimer.** AGP 8.7.2, Kotlin 2.0.21, Gradle 8.10.2, JDK 17, version catalog, `abiFilters = arm64-v8a`: эти версии уже лежат в кэше Gradle на машине и проверены в прошлых проектах. Многомодульность пока лишняя; разделим на `:core:ink`, `:feature:notes` и т. п., когда появятся движок и хранилище.
2. **minSdk = targetSdk = compileSdk = 35.** Устройство одно, на Android 15. Убирает проверки версий.
3. **Ввод через `pointerInteropFilter` / `View.onTouchEvent` + `onGenericMotionEvent`, а не через Compose `pointerInput`.** Compose-абстракция теряет часть полей (`AXIS_TILT`, коды кнопок, hover-детали, имя устройства). Для диагностики нужен исходный `MotionEvent`, поэтому статистика собирается на уровне Activity (`dispatchTouchEvent` / `dispatchGenericMotionEvent` / `dispatchKeyEvent`) — до Compose, чтобы hover и кнопки не терялись в interop-обёртке; тестовая зона — собственный `View` внутри `AndroidView`.
4. **Hover и кнопки** читаются из `onGenericMotionEvent` (`ACTION_HOVER_*`, `ACTION_BUTTON_PRESS/RELEASE`) и `onTouchEvent`; оба потока пишутся в один журнал событий.
5. **Уровни давления** считаем как число уникальных значений `pressure`, округлённых до 1e-5, за сессию — оценка снизу, но достаточно, чтобы отличить 4096 от 8192.
6. **Частота событий** — по `eventTime` исторических точек, а не по кадрам отрисовки, чтобы видеть реальную частоту опроса пера (может быть 240+ Гц при 144 Гц экрана).
7. **Сведения о сборке — как в VoiceTimer:** `BUILD_TIME` (`yyyy-MM-dd HH:mm`), `GIT_HASH` (короткий хэш) и `GIT_DIRTY` (есть незакоммиченные изменения) в `buildConfigField`; для задач `generate*BuildConfig` отключаем up-to-date (`outputs.upToDateWhen { false }`), чтобы время не «залипало» от прошлой сборки. Подпись release — из `keystore.properties`, если файл есть.
8. **Сохранение отчёта** — через `MediaStore` в `Documents/Pen/diagnostics/`, без разрешения на доступ ко всем файлам. Эта же папка позже станет хранилищем документов, доступным с ПК.

## Risks / Trade-offs

- [HyperOS может перехватывать кнопку пера системными жестами (скриншот, заметка)] → фиксируем это в отчёте как факт; обход ищем в `canvas-core`.
- [Перо может не отдавать наклон/азимут] → диагностика явно показывает «нет данных», дизайн кистей учтёт.
- [Время сборки в BuildConfig ломает инкрементальную сборку] → пересоздаётся только BuildConfig, это секунды; проверено в VoiceTimer.
