package com.zzzudroid.pen

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.zzzudroid.pen.canvas.StylusPresence
import com.zzzudroid.pen.diag.DiagnosticsViewModel
import com.zzzudroid.pen.notes.PdfSources
import com.zzzudroid.pen.ui.AppRoot

class MainActivity : ComponentActivity() {

    // ViewModel диагностики живёт на уровне Activity: ему нужны события клавиш,
    // которые приходят только в Activity (часть перьев шлёт кнопки как KeyEvent)
    private val diagnostics: DiagnosticsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Папка для извлечённых PDF-подложок тетрадей
        PdfSources.init(this)
        enableEdgeToEdge()
        setContent { AppRoot(diagnostics) }
    }

    // Сырые события перехватываем до Compose: так диагностика видит всё — касания,
    // hover и нажатия кнопок пера — независимо от того, над каким элементом перо
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        diagnostics.onRawMotion(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        StylusPresence.onGenericMotion(ev)
        diagnostics.onRawMotion(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        diagnostics.onKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }
}
