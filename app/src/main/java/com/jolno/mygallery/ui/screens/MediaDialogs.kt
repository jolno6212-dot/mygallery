package com.jolno.mygallery.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jolno.mygallery.BuildConfig
import com.jolno.mygallery.data.MediaFolder
import com.jolno.mygallery.data.MediaItem
import com.jolno.mygallery.data.SortField
import com.jolno.mygallery.data.SortOption
import com.jolno.mygallery.data.SortOrder
import com.jolno.mygallery.data.UpdateInfo
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RenameDialog(item: MediaItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(item.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名前の変更") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun PropertiesDialog(item: MediaItem, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val sizeText = formatFileSize(item.size)
    val dateText = if (item.dateTakenMs > 0) dateFormat.format(item.dateTakenMs) else dateFormat.format(item.dateAddedSec * 1000)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("プロパティ") },
        text = {
            Column {
                Text("名前: ${item.displayName}")
                Text("場所: ${item.relativePath}")
                Text("サイズ: $sizeText")
                if (item.width > 0 && item.height > 0) {
                    Text("解像度: ${item.width} x ${item.height}")
                }
                Text("日時: $dateText")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

@Composable
fun ResizeDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("1920") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("リサイズ") },
        text = {
            Column {
                Text("最大辺のピクセル数を入力してください")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let { onConfirm(it) } },
                enabled = text.toIntOrNull() != null && text.toInt() > 0
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun DateTakenDialog(item: MediaItem, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val initial = if (item.dateTakenMs > 0) item.dateTakenMs else item.dateAddedSec * 1000
    val format = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var text by remember { mutableStateOf(format.format(initial)) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("撮影日の値を修正") },
        text = {
            Column {
                Text("形式: yyyy-MM-dd HH:mm")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    singleLine = true,
                    isError = error
                )
                if (error) Text("日時の形式が正しくありません")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = runCatching { format.parse(text)?.time }.getOrNull()
                if (parsed != null) onConfirm(parsed) else error = true
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun FolderPickerDialog(
    folders: List<MediaFolder>,
    title: String,
    onDismiss: () -> Unit,
    onSelected: (MediaFolder) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(shape = androidx.compose.material3.MaterialTheme.shapes.large) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
                LazyColumn {
                    items(folders, key = { it.bucketId }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(folder) }
                        )
                    }
                }
            }
        }
    }
}

private val sortFieldLabels = mapOf(
    SortField.NAME to "名前",
    SortField.DATE to "日付",
    SortField.SIZE to "サイズ"
)

@Composable
fun SortDialog(current: SortOption, onDismiss: () -> Unit, onConfirm: (SortOption) -> Unit) {
    var field by remember { mutableStateOf(current.field) }
    var order by remember { mutableStateOf(current.order) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("並べ替え") },
        text = {
            Column {
                SortField.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { field = option }
                    ) {
                        RadioButton(selected = field == option, onClick = { field = option })
                        Text(sortFieldLabels[option] ?: option.name)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { order = SortOrder.ASC }
                ) {
                    RadioButton(selected = order == SortOrder.ASC, onClick = { order = SortOrder.ASC })
                    Text("昇順")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { order = SortOrder.DESC }
                ) {
                    RadioButton(selected = order == SortOrder.DESC, onClick = { order = SortOrder.DESC })
                    Text("降順")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(SortOption(field, order)) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun ColumnsDialog(current: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var value by remember { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("列数") },
        text = {
            Column {
                Text("$value 列")
                androidx.compose.material3.Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toInt() },
                    valueRange = 1f..6f,
                    steps = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("このアプリについて") },
        text = {
            Column {
                Text("マイギャラリー")
                Text("バージョン: ${BuildConfig.VERSION_NAME}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("アップデートがあります") },
        text = {
            Column {
                Text("新しいバージョン: ${info.versionName}")
                if (downloading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        Text("ダウンロード中…")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) { Text("更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) { Text("後で") }
        }
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
