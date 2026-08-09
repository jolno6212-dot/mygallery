package com.jolno.mygallery.ui.screens

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun formatDateGroupLabel(epochSec: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        isSameDay(target, today) -> "今日"
        isSameDay(target, yesterday) -> "昨日"
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("M月d日", Locale.JAPAN).format(target.time)
        else -> SimpleDateFormat("yyyy年M月d日", Locale.JAPAN).format(target.time)
    }
}
