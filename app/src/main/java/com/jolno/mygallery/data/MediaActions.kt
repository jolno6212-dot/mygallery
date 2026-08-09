package com.jolno.mygallery.data

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

fun openCamera(context: Context) {
    val intent = Intent("android.media.action.STILL_IMAGE_CAMERA")
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "カメラアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

fun playVideoExternally(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "動画を再生できるアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

fun shareItems(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val mimeType = if (items.all { it.kind == MediaKind.VIDEO }) "video/*"
    else if (items.all { it.kind == MediaKind.IMAGE }) "image/*" else "*/*"

    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, items.first().uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "共有"))
}

fun openWithOtherApp(context: Context, item: MediaItem) {
    val mimeType = if (item.kind == MediaKind.VIDEO) "video/*" else "image/*"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "アプリで開く"))
    }.onFailure {
        Toast.makeText(context, "開けるアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

fun editWithOtherApp(context: Context, item: MediaItem) {
    val mimeType = if (item.kind == MediaKind.VIDEO) "video/*" else "image/*"
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(item.uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "編集"))
    }.onFailure {
        Toast.makeText(context, "編集できるアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

fun useAsOtherApp(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(item.uri, if (item.kind == MediaKind.VIDEO) "video/*" else "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra("mimeType", if (item.kind == MediaKind.VIDEO) "video/*" else "image/*")
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "他で使う"))
    }.onFailure {
        Toast.makeText(context, "対応するアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

fun createShortcut(context: Context, item: MediaItem) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        Toast.makeText(context, "この端末はショートカット作成に対応していません", Toast.LENGTH_SHORT).show()
        return
    }
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, if (item.kind == MediaKind.VIDEO) "video/*" else "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val shortcut = ShortcutInfoCompat.Builder(context, "media_${item.id}")
        .setShortLabel(item.displayName.ifBlank { "メディア" })
        .setIcon(IconCompat.createWithResource(context, com.jolno.mygallery.R.mipmap.ic_launcher))
        .setIntent(viewIntent)
        .build()
    ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
}
