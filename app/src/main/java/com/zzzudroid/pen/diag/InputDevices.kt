package com.zzzudroid.pen.diag

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent

/** Диапазон одной оси устройства ввода. */
data class AxisInfo(
    val axis: String,
    val source: String,
    val min: Float,
    val max: Float,
    val resolution: Float,
    val fuzz: Float,
    val flat: Float,
)

/** Сведения об устройстве ввода (перо, тачскрин, клавиатура и т. п.). */
data class InputDeviceInfo(
    val id: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val sources: List<String>,
    val isExternal: Boolean,
    val isVirtual: Boolean,
    val axes: List<AxisInfo>,
) {
    /** Устройство, которое может быть пером или тачскрином — показываем его первым. */
    val isPointer get() = sources.any { it in POINTER_SOURCES }

    companion object {
        private val POINTER_SOURCES = setOf("STYLUS", "BLUETOOTH_STYLUS", "TOUCHSCREEN")
    }
}

// Имена источников ввода (флаги InputDevice.SOURCE_*); проверяем точное совпадение маски
private val SOURCE_NAMES = listOf(
    InputDevice.SOURCE_STYLUS to "STYLUS",
    InputDevice.SOURCE_BLUETOOTH_STYLUS to "BLUETOOTH_STYLUS",
    InputDevice.SOURCE_TOUCHSCREEN to "TOUCHSCREEN",
    InputDevice.SOURCE_TOUCHPAD to "TOUCHPAD",
    InputDevice.SOURCE_MOUSE to "MOUSE",
    InputDevice.SOURCE_MOUSE_RELATIVE to "MOUSE_RELATIVE",
    InputDevice.SOURCE_KEYBOARD to "KEYBOARD",
    InputDevice.SOURCE_DPAD to "DPAD",
    InputDevice.SOURCE_GAMEPAD to "GAMEPAD",
    InputDevice.SOURCE_JOYSTICK to "JOYSTICK",
    InputDevice.SOURCE_ROTARY_ENCODER to "ROTARY_ENCODER",
    InputDevice.SOURCE_SENSOR to "SENSOR",
    InputDevice.SOURCE_HDMI to "HDMI",
)

fun sourceNames(sources: Int): List<String> =
    SOURCE_NAMES.filter { (flag, _) -> sources and flag == flag }.map { it.second }

/** Список всех устройств ввода; перо и тачскрин — первыми. */
fun readInputDevices(context: Context): List<InputDeviceInfo> {
    val im = context.getSystemService(InputManager::class.java)
    val list: List<InputDevice> = im.inputDeviceIds.toList().mapNotNull { id -> im.getInputDevice(id) }
    return list.map { d ->
        InputDeviceInfo(
            id = d.id,
            name = d.name,
            vendorId = d.vendorId,
            productId = d.productId,
            sources = sourceNames(d.sources),
            isExternal = d.isExternal,
            isVirtual = d.isVirtual,
            axes = d.motionRanges.map { r ->
                AxisInfo(
                    axis = MotionEvent.axisToString(r.axis).removePrefix("AXIS_"),
                    source = sourceNames(r.source).joinToString("|"),
                    min = r.min,
                    max = r.max,
                    resolution = r.resolution,
                    fuzz = r.fuzz,
                    flat = r.flat,
                )
            },
        )
    }.sortedByDescending { info -> info.isPointer }
}
