package com.zzzudroid.pen.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Отдать PDF тетради в другое приложение через системное меню «Поделиться». */
fun sharePdf(context: Context, file: File) {
    if (!file.exists()) {
        Toast.makeText(context, "PDF ещё не создан", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Поделиться PDF"))
}
