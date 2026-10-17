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

## Окружение (17.10.2026)

- Проект лежит в `C:\Projects - Personal\Android\Pen` (перенесён из `C:\Projects\Personal\Pen`); соседний образец — `..\VoiceTimer`.
- Программы — общие установки: Android SDK `%LOCALAPPDATA%\Android\Sdk`, JDK 17 Eclipse Adoptium, Gradle через `gradlew`.
  Свои копии в проект не ставить.
- Реестр проектов и общих инструментов — `C:\Projects\РЕЕСТР ПРОЕКТОВ.md`.

## Устройство (измерено)

Xiaomi Pad 7 (2410CRP4CG), HyperOS 3.0, Android 16 (SDK 36), 3200×2136 @ 440 dpi, адаптивный 120 Гц.
Focus Pen: давление ≈ 8192 уровня, наклон, азимут, hover, опрос ≈ 330 Гц; **кнопка перехвачена системой** и приложению
не приходит. Подробно — `docs/design.md`, раздел 2. В Git Bash пути `/sdcard/...` подменяются — для `adb pull/push/shell ls`
ставить `MSYS_NO_PATHCONV=1`. Файлы заметок — `/sdcard/Documents/Pen/` (рядом лежит и чужое содержимое: не трогать).

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
- Большие доски: при ≥ 400 штрихов `StrokesLayer` рисует пачки `RenderNode` (запись один раз, сдвиг ±`OFFSET` = 100 000 ед.,
  пачки по 256 штрихов, перезапись изменившихся — сравнение по ссылкам); иначе — только видимые штрихи по кэшу границ
  (`strokeBounds`). Тяжёлые кэши привязывать к `contentVersion`, а не `version` (тот меняется при сдвиге). Нагрузочный
  тест и замеры — см. CHANGELOG («Большие доски»). Известное слабое место: ~4000 заметных штрихов одновременно (масштаб ~25%).
- Лассо: `canvas/Lasso.kt` (чистая геометрия, тесты), `canvas/StrokeOps.kt` (новые штрихи Ink из преобразованных
  точек — штрихи неизменяемы), режим лассо и превью в `DrawingSurface`, `ui/SelectionBar.kt`. История: `Replace`,
  `AddMany`; выделение — ссылки на объекты штрихов, снимается при любой смене истории/страницы.
- Доска — тот же `.pen` с `kind=board` и одной страницей без границ (`pageSpec == null`): `notes/BoardLayout.kt`
  (чистый расчёт экспорта/миниатюры, тесты), `NotebookViewModel.isBoard` + `boardView`; сетка и мини-карта —
  флаги `DrawingSurface.showGrid` / `showMinimap` (в «Холсте» выключены).
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
- **Ink `CanvasStrokeRenderer.draw(canvas, stroke, matrix)` НЕ применяет `matrix` к холсту** — параметр нужен только для
  расчёта толщины; положение задаёт текущая матрица `Canvas`. Всегда `canvas.save(); canvas.concat(m); draw(..., m); canvas.restore()`.
  Толщина кисти хранится в единицах холста: `размер в dp × density ÷ viewport.scale`.
- Проверка на планшете без пера: `adb shell input stylus swipe …` рисует штрихи (работает, пока настоящее перо не висит над экраном — иначе система считает касания ладонью); палец рисовать не умеет, только листает или (включив «стирать пальцем») стирает;
  скриншот `screencap` не показывает штрих в процессе (он в front-buffer) — снимать после паузы.
- API androidx.ink сверять по фактическим классам библиотеки (javap), документация к alpha-версиям расходится с 1.0.
- Ссылки между документами (формат `.pen` v3): `canvas/Link.kt` (`DocLink`, `LinkTarget`, `Bookmark`, `ViewState`), команды истории
  `LinkAdded/LinkRemoved/LinkChanged`, слой `LinksLayer` в `DrawingSurface` (тап пальцем → `onLinkClick`); `notes/MetaJson.kt`
  (чистая сериализация meta, `docId`), `notes/DocIndex.kt` (индекс `filesDir/docindex.json`, обратные ссылки), `notes/LinkRouting.kt`,
  `ui/LinkTargetDialog.kt`, `BookmarksSheet`, `BacklinksSheet`. Маршрут `note/{id}?page&bm&view&focus`. `NotebookViewModel.reloadIfChangedOnDisk()`
  на ON_RESUME — чтобы цепочка A→B→A не затёрла чужие правки.
- `InProgressStrokesView`: после `finishStroke` вызывать `requestHandoff()`, иначе изредка штрих не завершается до внешнего события.
- Настройки: `settings/AppSettings.kt` (SharedPreferences + состояние Compose, `init` в `MainActivity`, до него — значения по умолчанию), экран `ui/SettingsScreen.kt`.
- `BuildConfig.BUILD_TIME / GIT_HASH / GIT_DIRTY` — для экрана «О программе»; генерация BuildConfig
  никогда не считается up-to-date.
