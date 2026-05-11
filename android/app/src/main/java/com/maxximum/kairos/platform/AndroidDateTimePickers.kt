package com.maxximum.kairos.platform

import android.app.DatePickerDialog
import android.content.Context
import java.util.Calendar

fun showDarkDateTimePicker(context: Context, onResult: (Long) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, y, m, d ->
        android.app.TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h, min ->
            val result = Calendar.getInstance().apply {
                set(y, m, d, h, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            onResult(result)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}
