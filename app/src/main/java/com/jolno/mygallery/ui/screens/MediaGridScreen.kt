package com.jolno.mygallery.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jolno.mygallery.data.AppSettingsStore
import com.jolno.mygallery.data.FavoritesStore
import com.jolno.mygallery.data.MediaFolder
import com.jolno.mygallery.data.MediaItem
import com.jolno.mygallery.data.MediaKind
import com.jolno.mygallery.data.MediaRepository
import com.jolno.mygallery.data.WriteResult
import com.jolno.mygallery.data.createShortcut
import com.jolno.mygallery.data.editWithOtherApp
import com.jolno.mygallery.data.openWithOtherApp
import com.jolno.mygallery.data.playVideoExternally
import com.jolno.mygallery.data.requestManageExternalStoragePermission
import com.jolno.mygallery.data.shareItems
import com.jolno.mygallery.data.sortedItemsBy
import com.jolno.mygallery.data.useAsOtherApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaGridScreen(
    bucketId: String?,
    bucketName: String,
    onBack: () -> Unit,
    onItemClick: (List<MediaItem>, Int) -> Unit,
    onSlideshowClick: (List<MediaItem>) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }
    val favoritesStore = remember { FavoritesStore(context) }
    val settingsStore = remember { AppSettingsStore(context) }
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    var showMenu by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<DialogState?>(null) }
    var folderList by remember { mutableStateOf<List<MediaFolder>>(emptyList()) }
    var pendingRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingMoveTarget by remember { mutableStateOf<MediaFolder?>(null) }
    var columns by remember { mutableStateOf(settingsStore.gridColumns) }
    var sortOption by remember { mutableStateOf(settingsStore.sortOption) }
    var searchQuery by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            items = if (bucketId != null) repository.loadItemsForBucket(bucketId) else repository.loadAllMediaItems()
        }
    }

    LaunchedEffect(bucketId) { refresh() }

    val displayedItems = items
        ?.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        ?.sortedItemsBy(sortOption)

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingRetry?.invoke()
        pendingRetry = null
    }

    fun performWrite(operation: suspend () -> WriteResult, onSuccess: () -> Unit) {
        scope.launch {
            when (val result = operation()) {
                is WriteResult.Success -> onSuccess()
                is WriteResult.NeedsPermission -> {
                    pendingRetry = { performWrite(operation, onSuccess) }
                    writePermissionLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                }
                is WriteResult.NeedsFullFileAccess -> {
                    Toast.makeText(context, "このフォルダへの移動には「すべてのファイルへのアクセス」の許可が必要です", Toast.LENGTH_LONG).show()
                    requestManageExternalStoragePermission(context)
                }
                is WriteResult.Failed -> Toast.makeText(context, "操作に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds = emptySet()
            refresh()
        }
    }

    fun selectedItems(): List<MediaItem> = items.orEmpty().filter { it.id in selectedIds }

    fun deleteSelected() {
        val targets = selectedItems()
        val intentSender = repository.createDeleteRequest(targets)
        if (intentSender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        } else {
            scope.launch {
                repository.deleteDirectly(targets)
                selectedIds = emptySet()
                refresh()
            }
        }
    }

    fun moveSelectedTo(target: MediaFolder) {
        selectedItems().forEach { item ->
            performWrite({ repository.moveItem(item, target) }) {}
        }
        selectedIds = emptySet()
        refresh()
        Toast.makeText(context, "移動しました", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                CenterAlignedTopAppBar(
                    title = { Text("${selectedIds.size}件選択中") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "完了")
                        }
                    },
                    actions = {
                        IconButton(onClick = { deleteSelected() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "削除")
                        }
                        IconButton(onClick = { shareItems(context, selectedItems()) }) {
                            Icon(Icons.Filled.Share, contentDescription = "共有")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "他のオプション")
                        }
                        SelectionDropdownMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            selectionCount = selectedIds.size,
                            onAction = { action ->
                                showMenu = false
                                val selected = selectedItems()
                                val single = selected.firstOrNull()
                                when (action) {
                                    SelectionAction.ROTATE -> {
                                        selected.forEach { target ->
                                            performWrite({ repository.rotateImage(target, 90) }) {}
                                        }
                                        refresh()
                                    }
                                    SelectionAction.PROPERTIES -> if (single != null) dialogState = DialogState.Properties
                                    SelectionAction.RENAME -> if (single != null) dialogState = DialogState.Rename
                                    SelectionAction.HIDE -> dialogState = DialogState.HideConfirm
                                    SelectionAction.COPY -> {
                                        scope.launch {
                                            folderList = repository.loadFolders()
                                            dialogState = DialogState.Copy
                                        }
                                    }
                                    SelectionAction.MOVE -> {
                                        scope.launch {
                                            folderList = repository.loadFolders()
                                            dialogState = DialogState.Move
                                        }
                                    }
                                    SelectionAction.SHORTCUT -> if (single != null) createShortcut(context, single)
                                    SelectionAction.OPEN_WITH -> if (single != null) openWithOtherApp(context, single)
                                    SelectionAction.USE_AS -> if (single != null) useAsOtherApp(context, single)
                                    SelectionAction.RESIZE -> dialogState = DialogState.Resize
                                    SelectionAction.EDIT -> if (single != null) editWithOtherApp(context, single)
                                    SelectionAction.FAVORITE -> {
                                        favoritesStore.addAll(selected.map { it.id })
                                        Toast.makeText(context, "お気に入りに追加しました", Toast.LENGTH_SHORT).show()
                                        selectedIds = emptySet()
                                    }
                                    SelectionAction.DATE_TAKEN -> if (single != null) dialogState = DialogState.DateTaken
                                    SelectionAction.SELECT_ALL -> selectedIds = items.orEmpty().map { it.id }.toSet()
                                }
                            }
                        )
                    }
                )
            } else {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "${bucketName}内を検索",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "検索") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { dialogState = DialogState.Sort }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "並べ替え")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "他のオプション")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("非表示項目を一時的に表示") }, onClick = {
                                showMenu = false
                                Toast.makeText(context, "この機能は未対応です", Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(text = { Text("ごみ箱を開く") }, onClick = {
                                showMenu = false
                                Toast.makeText(context, "この機能は未対応です", Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(text = { Text("グループ分け") }, onClick = {
                                showMenu = false
                                Toast.makeText(context, "この機能は未対応です", Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(
                                text = { Text("デフォルトのフォルダとして設定") },
                                enabled = bucketId != null,
                                onClick = {
                                    showMenu = false
                                    if (bucketId != null) {
                                        settingsStore.defaultBucketId = bucketId
                                        Toast.makeText(context, "デフォルトのフォルダに設定しました", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            DropdownMenuItem(text = { Text("フォルダの新規作成") }, onClick = {
                                showMenu = false
                                Toast.makeText(context, "この機能は未対応です", Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(text = { Text("列数") }, onClick = {
                                showMenu = false
                                dialogState = DialogState.Columns
                            })
                            DropdownMenuItem(text = { Text("スライドショー") }, onClick = {
                                showMenu = false
                                displayedItems?.let { onSlideshowClick(it.filter { item -> item.kind == MediaKind.IMAGE }) }
                            })
                            DropdownMenuItem(text = { Text("設定") }, onClick = {
                                showMenu = false
                                Toast.makeText(context, "この機能は未対応です", Toast.LENGTH_SHORT).show()
                            })
                        }
                    }
                }
            }
        }
    ) { padding ->
        val list = displayedItems
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (bucketId == null) {
            val orderedList = list.sortedByDescending { it.dateAddedSec }
            val groups = orderedList.groupBy { formatDateGroupLabel(it.dateAddedSec) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                groups.forEach { (label, groupItems) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header_$label") {
                        Text(
                            text = label,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    items(groupItems, key = { it.id }) { item ->
                        MediaCell(
                            item = item,
                            selected = item.id in selectedIds,
                            selectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                                } else if (item.kind == MediaKind.VIDEO) {
                                    playVideoExternally(context, item)
                                } else {
                                    val photos = orderedList.filter { it.kind == MediaKind.IMAGE }
                                    onItemClick(photos, photos.indexOf(item))
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) selectedIds = setOf(item.id)
                            }
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(list, key = { it.id }) { item ->
                    MediaCell(
                        item = item,
                        selected = item.id in selectedIds,
                        selectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                            } else if (item.kind == MediaKind.VIDEO) {
                                playVideoExternally(context, item)
                            } else {
                                val photos = list.filter { it.kind == MediaKind.IMAGE }
                                onItemClick(photos, photos.indexOf(item))
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) selectedIds = setOf(item.id)
                        }
                    )
                }
            }
        }
    }

    when (val state = dialogState) {
        is DialogState.Rename -> {
            val target = selectedItems().firstOrNull()
            if (target != null) {
                RenameDialog(
                    item = target,
                    onDismiss = { dialogState = null },
                    onConfirm = { newName ->
                        dialogState = null
                        performWrite({ repository.renameItem(target, newName) }) {
                            selectedIds = emptySet()
                            refresh()
                        }
                    }
                )
            }
        }
        is DialogState.Properties -> {
            val target = selectedItems().firstOrNull()
            if (target != null) {
                PropertiesDialog(item = target, onDismiss = { dialogState = null })
            }
        }
        is DialogState.Resize -> {
            ResizeDialog(
                onDismiss = { dialogState = null },
                onConfirm = { maxDimension ->
                    dialogState = null
                    val targets = selectedItems()
                    targets.forEach { target ->
                        performWrite({ repository.resizeImage(target, maxDimension) }) {}
                    }
                    selectedIds = emptySet()
                    refresh()
                }
            )
        }
        is DialogState.DateTaken -> {
            val target = selectedItems().firstOrNull()
            if (target != null) {
                DateTakenDialog(
                    item = target,
                    onDismiss = { dialogState = null },
                    onConfirm = { millis ->
                        dialogState = null
                        performWrite({ repository.updateDateTaken(target, millis) }) {
                            selectedIds = emptySet()
                            refresh()
                        }
                    }
                )
            }
        }
        DialogState.Copy -> {
            FolderPickerDialog(
                folders = folderList,
                title = "コピー先を選択",
                onDismiss = { dialogState = null },
                onSelected = { target ->
                    dialogState = null
                    val targets = selectedItems()
                    scope.launch {
                        targets.forEach { item -> repository.copyItem(item, target) }
                        selectedIds = emptySet()
                        refresh()
                        Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        DialogState.Move -> {
            FolderPickerDialog(
                folders = folderList,
                title = "移動先を選択",
                onDismiss = { dialogState = null },
                onSelected = { target ->
                    dialogState = null
                    pendingMoveTarget = target
                }
            )
        }
        DialogState.HideConfirm -> {
            ConfirmDialog(
                title = "非表示",
                message = "このフォルダをギャラリーから非表示にしますか?",
                onDismiss = { dialogState = null },
                onConfirm = {
                    dialogState = null
                    val target = selectedItems().firstOrNull()
                    if (target != null) {
                        val folder = MediaFolder(
                            bucketId = target.bucketId,
                            name = bucketName,
                            coverUri = target.uri,
                            itemCount = 0,
                            relativePath = target.relativePath
                        )
                        scope.launch {
                            repository.hideFolder(folder)
                            selectedIds = emptySet()
                            Toast.makeText(context, "非表示にしました", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                }
            )
        }
        DialogState.Sort -> {
            SortDialog(
                current = sortOption,
                onDismiss = { dialogState = null },
                onConfirm = {
                    dialogState = null
                    sortOption = it
                    settingsStore.sortOption = it
                }
            )
        }
        DialogState.Columns -> {
            ColumnsDialog(
                current = columns,
                onDismiss = { dialogState = null },
                onConfirm = {
                    dialogState = null
                    columns = it
                    settingsStore.gridColumns = it
                }
            )
        }
        null -> {}
    }

    pendingMoveTarget?.let { target ->
        ConfirmDialog(
            title = "移動",
            message = "選択した項目を「${target.name}」に移動しますか?",
            onDismiss = { pendingMoveTarget = null },
            onConfirm = {
                pendingMoveTarget = null
                moveSelectedTo(target)
            }
        )
    }
}

sealed class DialogState {
    data object Rename : DialogState()
    data object Properties : DialogState()
    data object Resize : DialogState()
    data object DateTaken : DialogState()
    data object Copy : DialogState()
    data object Move : DialogState()
    data object HideConfirm : DialogState()
    data object Sort : DialogState()
    data object Columns : DialogState()
}

enum class SelectionAction {
    ROTATE, PROPERTIES, RENAME, HIDE, COPY, MOVE, SHORTCUT, OPEN_WITH, USE_AS, RESIZE, EDIT, FAVORITE, DATE_TAKEN, SELECT_ALL
}

@Composable
internal fun SelectionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    selectionCount: Int,
    onAction: (SelectionAction) -> Unit
) {
    val singleOnly = selectionCount == 1
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("回転") }, onClick = { onAction(SelectionAction.ROTATE) })
        DropdownMenuItem(text = { Text("プロパティ") }, enabled = singleOnly, onClick = { onAction(SelectionAction.PROPERTIES) })
        DropdownMenuItem(text = { Text("名前の変更") }, enabled = singleOnly, onClick = { onAction(SelectionAction.RENAME) })
        DropdownMenuItem(text = { Text("非表示") }, onClick = { onAction(SelectionAction.HIDE) })
        DropdownMenuItem(text = { Text("コピー") }, onClick = { onAction(SelectionAction.COPY) })
        DropdownMenuItem(text = { Text("移動") }, onClick = { onAction(SelectionAction.MOVE) })
        DropdownMenuItem(text = { Text("ショートカットを作成") }, enabled = singleOnly, onClick = { onAction(SelectionAction.SHORTCUT) })
        DropdownMenuItem(text = { Text("別のアプリで開く") }, enabled = singleOnly, onClick = { onAction(SelectionAction.OPEN_WITH) })
        DropdownMenuItem(text = { Text("他で使う") }, enabled = singleOnly, onClick = { onAction(SelectionAction.USE_AS) })
        DropdownMenuItem(text = { Text("リサイズ") }, onClick = { onAction(SelectionAction.RESIZE) })
        DropdownMenuItem(text = { Text("編集") }, enabled = singleOnly, onClick = { onAction(SelectionAction.EDIT) })
        DropdownMenuItem(text = { Text("お気に入りに追加") }, onClick = { onAction(SelectionAction.FAVORITE) })
        DropdownMenuItem(text = { Text("撮影日の値を修正") }, enabled = singleOnly, onClick = { onAction(SelectionAction.DATE_TAKEN) })
        DropdownMenuItem(text = { Text("すべて選択") }, onClick = { onAction(SelectionAction.SELECT_ALL) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaCell(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.kind == MediaKind.VIDEO) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "動画",
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            )
        }
        if (selectionMode) {
            Box(modifier = Modifier.fillMaxSize().background(if (selected) Color.Black.copy(alpha = 0.35f) else Color.Transparent))
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
            )
        }
    }
}
