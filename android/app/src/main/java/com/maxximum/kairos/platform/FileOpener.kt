package com.maxximum.kairos.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

fun openFile(context: Context, uri: Uri, mimeType: String?) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with..."))
    } catch (_: Exception) {
        ToastUtils.show(context, "Could not open file")
    }
}
