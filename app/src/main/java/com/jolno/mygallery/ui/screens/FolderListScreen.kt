package com.jolno.mygallery.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.jolno.mygallery.data.openCamera
import com.jolno.mygallery.data.openWithOtherApp
import com.jolno.mygallery.data.playVideoExternally
import com.jolno.mygallery.data.shareItems
import com.jolno.mygallery.data.sortedFoldersBy
import com.jolno.mygallery.data.sortedItemsBy
import com.jolno.mygallery.data.useAsOtherApp
import kotlinx.coroutines.launch

private enum class FolderMenuAction { SORT, COLUMNS, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    showAllMedia: Boolean,
    onToggleAllMedia: () -> Unit,
    onFolderClick: (MediaFolder) -> Unit,
    onMediaItemClick: (List<MediaItem>, Int) -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }
    val settingsStore = remember { AppSettingsStore(context) }
    val favoritesStore = remember { FavoritesStore(context) }
    val scope = rememberCoroutineScope()

    var allFolders by remember { mutableStateOf<List<MediaFolder>?>(null) }
    var allItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(settingsStore.sortOption) }
    var columns by remember { mutableStateOf(settingsStore.folderColumns) }
    var dialogAction by remember { mutableStateOf<FolderMenuAction?>(null) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var selectionMenuExpanded by remember { mutableStateOf(false) }
    var selectionDialogState by remember { mutableStateOf<DialogState?>(null) }
    var folderList by remember { mutableStateOf<List<MediaFolder>>(emptyList()) }
    var pendingRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val gridState = rememberLazyGridState()

    fun refreshAllItems() {
        scope.launch { allItems = repository.loadAllMediaItems() }
    }

    LaunchedEffect(Unit) {
        allFolders = repository.loadFolders()
        allItems = repository.loadAllMediaItems()
    }

    val displayedFolders = allFolders
        ?.filter { it.name.contains(searchQuery, ignoreCase = true) }
        ?.sortedFoldersBy(sortOption)

    val displayedItems = allItems
        ?.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        ?.sortedItemsBy(sortOption)

    fun selectedMediaItems(): List<MediaItem> = allItems.orEmpty().filter { it.id in selectedIds }

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
                is WriteResult.Failed -> Toast.makeText(context, "操作に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedIds = emptySet()
            refreshAllItems()
        }
    }

    fun deleteSelectedItems() {
        val targets = selectedMediaItems()
        val intentSender = repository.createDeleteRequest(targets)
        if (intentSender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        } else {
            scope.launch {
                repository.deleteDirectly(targets)
                selectedIds = emptySet()
                refreshAllItems()
            }
        }
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
                        IconButton(onClick = { deleteSelectedItems() }) {
                            Icon(Icons.Filled.Delete, contentDescription = "削除")
                        }
                        IconButton(onClick = { shareItems(context, selectedMediaItems()) }) {
                            Icon(Icons.Filled.Share, contentDescription = "共有")
                        }
                        Box {
                            IconButton(onClick = { selectionMenuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "他のオプション")
                            }
                            SelectionDropdownMenu(
                                expanded = selectionMenuExpanded,
                                onDismiss = { selectionMenuExpanded = false },
                                selectionCount = selectedIds.size,
                                onAction = { action ->
                                    selectionMenuExpanded = false
                                    val selected = selectedMediaItems()
                                    val single = selected.firstOrNull()
                                    when (action) {
                                        SelectionAction.ROTATE -> {
                                            selected.forEach { target ->
                                                performWrite({ repository.rotateImage(target, 90) }) {}
                                            }
                                            refreshAllItems()
                                        }
                                        SelectionAction.PROPERTIES -> if (single != null) selectionDialogState = DialogState.Properties
                                        SelectionAction.RENAME -> if (single != null) selectionDialogState = DialogState.Rename
                                        SelectionAction.HIDE -> selectionDialogState = DialogState.HideConfirm
                                        SelectionAction.COPY -> {
                                            scope.launch {
                                                folderList = repository.loadFolders()
                                                selectionDialogState = DialogState.Copy
                                            }
                                        }
                                        SelectionAction.MOVE -> {
                                            scope.launch {
                                                folderList = repository.loadFolders()
                                                selectionDialogState = DialogState.Move
                                            }
                                        }
                                        SelectionAction.SHORTCUT -> if (single != null) createShortcut(context, single)
                                        SelectionAction.OPEN_WITH -> if (single != null) openWithOtherApp(context, single)
                                        SelectionAction.USE_AS -> if (single != null) useAsOtherApp(context, single)
                                        SelectionAction.RESIZE -> selectionDialogState = DialogState.Resize
                                        SelectionAction.EDIT -> if (single != null) editWithOtherApp(context, single)
                                        SelectionAction.FAVORITE -> {
                                            favoritesStore.addAll(selected.map { it.id })
                                            Toast.makeText(context, "お気に入りに追加しました", Toast.LENGTH_SHORT).show()
                                            selectedIds = emptySet()
                                        }
                                        SelectionAction.DATE_TAKEN -> if (single != null) selectionDialogState = DialogState.DateTaken
                                        SelectionAction.SELECT_ALL -> selectedIds = allItems.orEmpty().map { it.id }.toSet()
                                    }
                                }
                            )
                        }
                    }
                )
            } else {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 8.dp)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (showAllMedia) "ファイルを検索" else "フォルダを検索",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                        IconButton(onClick = { openCamera(context) }) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = "カメラを開く")
                        }
                        IconButton(onClick = {
                            selectedIds = emptySet()
                            onToggleAllMedia()
                        }) {
                            Icon(
                                imageVector = if (showAllMedia) Icons.Filled.Folder else Icons.Filled.Collections,
                                contentDescription = if (showAllMedia) "フォルダを表示" else "すべてを表示"
                            )
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "他のオプション")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("並べ替え") }, onClick = {
                                    showMenu = false
                                    dialogAction = FolderMenuAction.SORT
                                })
                                DropdownMenuItem(text = { Text("列数") }, onClick = {
                                    showMenu = false
                                    dialogAction = FolderMenuAction.COLUMNS
                                })
                                DropdownMenuItem(text = { Text("このアプリについて") }, onClick = {
                                    showMenu = false
                                    dialogAction = FolderMenuAction.ABOUT
                                })
                                DropdownMenuItem(text = { Text("アップデートを確認") }, onClick = {
                                    showMenu = false
                                    onCheckUpdate()
                                })
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showAllMedia) {
            val list = displayedItems
            if (list == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("写真・動画が見つかりません")
                }
            } else {
                val orderedList = list.sortedByDescending { it.dateAddedSec }
                val groups = orderedList.groupBy { formatDateGroupLabel(it.dateAddedSec) }
                var runningIndex = 0
                val sections = groups.map { (label, groupItems) ->
                    val start = runningIndex
                    runningIndex += 1 + groupItems.size
                    label to start
                }
                val totalFlatCount = runningIndex

                Box(Modifier.fillMaxSize().padding(padding)) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groups.forEach { (label, groupItems) ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "header_$label") {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium,
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
                                            onMediaItemClick(photos, photos.indexOf(item))
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) selectedIds = setOf(item.id)
                                    }
                                )
                            }
                        }
                    }

                    if (sections.size > 1) {
                        DateScrubber(
                            sections = sections,
                            totalItemCount = totalFlatCount,
                            gridState = gridState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                }
            }
        } else {
            val list = displayedFolders
            if (list == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("写真・動画が見つかりません")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(list, key = { it.bucketId }) { folder ->
                        FolderCell(folder = folder, onClick = { onFolderClick(folder) })
                    }
                }
            }
        }
    }

    when (dialogAction) {
        FolderMenuAction.SORT -> {
            SortDialog(
                current = sortOption,
                onDismiss = { dialogAction = null },
                onConfirm = {
                    dialogAction = null
                    sortOption = it
                    settingsStore.sortOption = it
                }
            )
        }
        FolderMenuAction.COLUMNS -> {
            ColumnsDialog(
                current = columns,
                onDismiss = { dialogAction = null },
                onConfirm = {
                    dialogAction = null
                    columns = it
                    settingsStore.folderColumns = it
                }
            )
        }
        FolderMenuAction.ABOUT -> {
            AboutDialog(onDismiss = { dialogAction = null })
        }
        null -> {}
    }

    when (val state = selectionDialogState) {
        is DialogState.Rename -> {
            val target = selectedMediaItems().firstOrNull()
            if (target != null) {
                RenameDialog(
                    item = target,
                    onDismiss = { selectionDialogState = null },
                    onConfirm = { newName ->
                        selectionDialogState = null
                        performWrite({ repository.renameItem(target, newName) }) {
                            selectedIds = emptySet()
                            refreshAllItems()
                        }
                    }
                )
            }
        }
        is DialogState.Properties -> {
            val target = selectedMediaItems().firstOrNull()
            if (target != null) {
                PropertiesDialog(item = target, onDismiss = { selectionDialogState = null })
            }
        }
        is DialogState.Resize -> {
            ResizeDialog(
                onDismiss = { selectionDialogState = null },
                onConfirm = { maxDimension ->
                    selectionDialogState = null
                    val targets = selectedMediaItems()
                    targets.forEach { target ->
                        performWrite({ repository.resizeImage(target, maxDimension) }) {}
                    }
                    selectedIds = emptySet()
                    refreshAllItems()
                }
            )
        }
        is DialogState.DateTaken -> {
            val target = selectedMediaItems().firstOrNull()
            if (target != null) {
                DateTakenDialog(
                    item = target,
                    onDismiss = { selectionDialogState = null },
                    onConfirm = { millis ->
                        selectionDialogState = null
                        performWrite({ repository.updateDateTaken(target, millis) }) {
                            selectedIds = emptySet()
                            refreshAllItems()
                        }
                    }
                )
            }
        }
        DialogState.Copy -> {
            FolderPickerDialog(
                folders = folderList,
                title = "コピー先を選択",
                onDismiss = { selectionDialogState = null },
                onSelected = { target ->
                    selectionDialogState = null
                    val targets = selectedMediaItems()
                    scope.launch {
                        targets.forEach { item -> repository.copyItem(item, target) }
                        selectedIds = emptySet()
                        refreshAllItems()
                        Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        DialogState.Move -> {
            FolderPickerDialog(
                folders = folderList,
                title = "移動先を選択",
                onDismiss = { selectionDialogState = null },
                onSelected = { target ->
                    selectionDialogState = null
                    val targets = selectedMediaItems()
                    scope.launch {
                        targets.forEach { item -> repository.moveItem(item, target) }
                        selectedIds = emptySet()
                        refreshAllItems()
                        Toast.makeText(context, "移動しました", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        DialogState.HideConfirm -> {
            ConfirmDialog(
                title = "非表示",
                message = "このフォルダをギャラリーから非表示にしますか?",
                onDismiss = { selectionDialogState = null },
                onConfirm = {
                    selectionDialogState = null
                    val target = selectedMediaItems().firstOrNull()
                    if (target != null) {
                        val folder = MediaFolder(
                            bucketId = target.bucketId,
                            name = "",
                            coverUri = target.uri,
                            itemCount = 0,
                            relativePath = target.relativePath
                        )
                        scope.launch {
                            repository.hideFolder(folder)
                            selectedIds = emptySet()
                            Toast.makeText(context, "非表示にしました", Toast.LENGTH_SHORT).show()
                            refreshAllItems()
                        }
                    }
                }
            )
        }
        null -> {}
        else -> {}
    }
}

@Composable
private fun DateScrubber(
    sections: List<Pair<String, Int>>,
    totalItemCount: Int,
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    val firstVisibleIndex = gridState.firstVisibleItemIndex
    val currentLabel = remember(firstVisibleIndex, sections) {
        sections.lastOrNull { it.second <= firstVisibleIndex }?.first ?: sections.firstOrNull()?.first.orEmpty()
    }
    val scrollFraction = if (totalItemCount > 0) firstVisibleIndex.toFloat() / totalItemCount else 0f
    val thumbFraction = if (isDragging) dragFraction else scrollFraction

    fun seekTo(y: Float) {
        val fraction = if (trackHeightPx > 0f) (y / trackHeightPx).coerceIn(0f, 1f) else 0f
        dragFraction = fraction
        val index = (fraction * sections.size).toInt().coerceIn(0, sections.size - 1)
        scope.launch { gridState.scrollToItem(sections[index].second) }
    }

    AnimatedVisibility(
        visible = isDragging || gridState.isScrollInProgress,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { trackHeightPx = it.height.toFloat() }
                .pointerInput(sections.size) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            seekTo(offset.y)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            seekTo(change.position.y)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .width(3.dp)
                    .fillMaxHeight(0.92f)
                    .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (thumbFraction * trackHeightPx).toInt().coerceIn(0, trackHeightPx.toInt())) }
                    .align(Alignment.TopEnd)
                    .padding(end = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(currentLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun FolderCell(folder: MediaFolder, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(4.dp).clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = folder.coverUri,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = folder.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = "${folder.itemCount}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
