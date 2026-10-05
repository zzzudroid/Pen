package com.zzzudroid.pen.diag

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.zzzudroid.pen.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Папка отчётов относительно общего хранилища. */
val REPORT_DIR = Environment.DIRECTORY_DOCUMENTS + "/Pen/diagnostics"

/** Собрать JSON-отчёт диагностики. */
fun buildReport(context: Context, snap: DiagSnapshot, devices: List<InputDeviceInfo>): JSONObject {
    val display = context.display
    return JSONObject().apply {
        put("createdAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
        put("build", JSONObject().apply {
            put("versionName", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("buildTime", BuildConfig.BUILD_TIME)
            put("gitHash", BuildConfig.GIT_HASH)
            put("gitDirty", BuildConfig.GIT_DIRTY)
        })
        put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("sdk", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
            put("incremental", Build.VERSION.INCREMENTAL)
            display?.let { d ->
                put("refreshRate", d.refreshRate.toDouble())
                put("supportedRefreshRates", JSONArray(d.supportedModes.map { it.refreshRate.toDouble() }.distinct()))
            }
            val m = context.resources.displayMetrics
            put("screenPx", "${m.widthPixels}x${m.heightPixels}")
            put("densityDpi", m.densityDpi)
        })
        put("session", JSONObject().apply {
            put("totalEvents", snap.totalEvents)
            put("hoverSeen", snap.hoverSeen)
            put("unbufferedDispatch", snap.unbuffered)
            put("buttonStates", JSONArray(snap.buttonStates.map { buttonNames(it) }))
            put("keyCodes", JSONArray(snap.keyCodes))
        })
        put("tools", JSONArray(snap.tools.map { t ->
            JSONObject().apply {
                put("tool", t.tool.name)
                put("contactSamples", t.contactSamples)
                put("hoverSamples", t.hoverSamples)
                putRange("pressure", t.pressureMin, t.pressureMax)
                put("pressureLevels", t.pressureLevels)
                putRange("tiltDeg", t.tiltMinRad.toDeg(), t.tiltMaxRad.toDeg())
                putRange("orientationDeg", t.orientationMinRad.toDeg(), t.orientationMaxRad.toDeg())
                put("contactRateHz", t.contactRateHz.round1())
                put("hoverRateHz", t.hoverRateHz.round1())
                put("avgHistoricalPerEvent", t.avgHistoricalPerEvent.round1())
            }
        }))
        put("inputDevices", JSONArray(devices.map { d ->
            JSONObject().apply {
                put("id", d.id)
                put("name", d.name)
                put("vendorId", "0x%04x".format(d.vendorId))
                put("productId", "0x%04x".format(d.productId))
                put("sources", JSONArray(d.sources))
                put("external", d.isExternal)
                put("virtual", d.isVirtual)
                put("axes", JSONArray(d.axes.map { a ->
                    JSONObject().apply {
                        put("axis", a.axis)
                        put("source", a.source)
                        put("min", a.min.toDouble())
                        put("max", a.max.toDouble())
                        put("resolution", a.resolution.toDouble())
                        put("fuzz", a.fuzz.toDouble())
                        put("flat", a.flat.toDouble())
                    }
                }))
            }
        }))
    }
}

/**
 * Сохранить отчёт в Documents/Pen/diagnostics через MediaStore (без особых разрешений)
 * и продублировать в logcat. Возвращает путь к файлу для показа пользователю.
 */
fun saveReport(context: Context, report: JSONObject): String {
    val text = report.toString(2)
    val name = "pen-diag-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".json"

    // logcat обрезает длинные строки — пишем кусками
    Log.i(TAG, "REPORT $name begin")
    text.chunked(3000).forEach { Log.i(TAG, it) }
    Log.i(TAG, "REPORT $name end")

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
        put(MediaStore.MediaColumns.RELATIVE_PATH, REPORT_DIR)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ?: error("MediaStore не создал файл")
    resolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
    return "/sdcard/$REPORT_DIR/$name"
}

private fun JSONObject.putRange(key: String, min: Float, max: Float) {
    if (min.isNaN()) put(key, JSONObject.NULL)
    else put(key, JSONObject().put("min", min.toDouble()).put("max", max.toDouble()))
}

fun Float.toDeg() = if (isNaN()) Float.NaN else Math.toDegrees(toDouble()).toFloat()

private fun Double.round1() = Math.round(this * 10) / 10.0
