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
- `canvas/` — холст: `DrawingSurface` (слой готовых штрихов + `InProgressStrokesView`, вся маршрутизация
  указателей, жесты, ластик), `CanvasViewModel` (штрихи в мировых координатах, история, вид, кисть),
  `Viewport` и `History` — чистый Kotlin с тестами. `StylusPresence` получает hover из `MainActivity`.
- `CanvasViewModel` — со страницами: `canvas/Page.kt` (`PageState` — штрихи и история страницы, `PageSpec`,
  `PageTemplate`), `canvas/PageRenderer.kt` (стол, лист и шаблон в мировых координатах; A4 = 1050×1485, 5 ед./мм).
- `notes/` — тетради: `NotebookStore` (Documents/Pen, уникальные имена, атомарная запись, rename с учётом
  регистронезависимости /sdcard), `NotebookFormat` (формат `.pen` v1), `NotebookViewModel` (наследник
  `CanvasViewModel`, автосохранение в `NotebookStore.saveScope` под `writeLock`), `FileNames` (чистый Kotlin, тесты).
- Лассо: `canvas/Lasso.kt` (чистая геометрия, тесты), `canvas/StrokeOps.kt` (новые штрихи Ink из преобразованных
  точек — штрихи неизменяемы), режим лассо и превью в `DrawingSurface`, `ui/SelectionBar.kt`. История: `Replace`,
  `AddMany`; выделение — ссылки на объекты штрихов, снимается при любой смене истории/страницы.
- `recognition/` — поиск по рукописи: `LineSegmenter` и `TextSearch` (чистый Kotlin, тесты), `HandwritingRecognizer`
  (ML Kit Digital Ink, модель `ru`, пакет `com.google.mlkit.vision.digitalink.recognition`), `SearchIndex`
  (`filesDir/recognition/index.json`, инкрементально по `meta.modified`). Формат `.pen` не затрагивает.
- Разметка PDF: у страницы свой `PageState.spec` и `pdfPage`; `CanvasViewModel.pageSpec` = spec текущей страницы.
  `notes/PdfSource.kt` (импорт, извлечение `source.pdf` в cacheDir, `PdfSources.init` в MainActivity),
  `notes/PdfPageCache.kt` (фоновый рендер `PdfRenderer` под Mutex, ступени 512–4096, LRU). Формат `.pen` v2.
  Подложка рисуется через `DrawingSurface.pageBackground` — canvas не зависит от notes.
- `notes/PdfExporter` — векторный PDF рядом с `.pen` (контуры `outlinesToPath`, не `CanvasStrokeRenderer`),
  пишется под тем же `writeLock` сразу после `.pen`; ошибка PDF не откатывает `.pen` (`pdfError`).
  «Поделиться» — `FileProvider` (`${applicationId}.files`), перед отправкой `saveAndAwait()`.
- API androidx.ink сверять по фактическим классам библиотеки (javap), документация к alpha-версиям расходится с 1.0.
- `BuildConfig.BUILD_TIME / GIT_HASH / GIT_DIRTY` — для экрана «О программе»; генерация BuildConfig
  никогда не считается up-to-date.
