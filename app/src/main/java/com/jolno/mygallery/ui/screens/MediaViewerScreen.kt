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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jolno.mygallery.data.FavoritesStore
import com.jolno.mygallery.data.MediaFolder
import com.jolno.mygallery.data.MediaItem
import com.jolno.mygallery.data.MediaRepository
import com.jolno.mygallery.data.WriteResult
import com.jolno.mygallery.data.createShortcut
import com.jolno.mygallery.data.editWithOtherApp
import com.jolno.mygallery.data.openWithOtherApp
import com.jolno.mygallery.data.requestManageExternalStoragePermission
import com.jolno.mygallery.data.shareItems
import com.jolno.mygallery.data.useAsOtherApp
import kotlinx.coroutines.launch

private enum class ViewerDialogState {
    Rename, Properties, Resize, DateTaken, Copy, Move, HideConfirm
}

@Composable
fun MediaViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    onBack: () -> Unit,
    autoPlay: Boolean = false
) {
    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }
    val favoritesStore = remember { FavoritesStore(context) }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var controlsVisible by remember { mutableStateOf(!autoPlay) }
    var slideshowActive by remember { mutableStateOf(autoPlay) }
    var currentPageZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { currentPageZoomed = false }

    LaunchedEffect(slideshowActive, pagerState.currentPage) {
        if (slideshowActive) {
            kotlinx.coroutines.delay(3000)
            val next = pagerState.currentPage + 1
            if (next < items.size) {
                pagerState.animateScrollToPage(next)
            } else {
                slideshowActive = false
                controlsVisible = true
            }
        }
    }
    var showMenu by remember { mutableStateOf(false) }
    var dialogState by remember { mutableStateOf<ViewerDialogState?>(null) }
    var folderList by remember { mutableStateOf<List<MediaFolder>>(emptyList()) }
    var pendingRetry by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingMoveTarget by remember { mutableStateOf<MediaFolder?>(null) }
    var favoriteVersion by remember { mutableStateOf(0) }

    val currentItem = items.getOrNull(pagerState.currentPage)
    val isFavorite = remember(currentItem?.id, favoriteVersion) {
        currentItem != null && favoritesStore.isFavorite(currentItem.id)
    }

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
        if (result.resultCode == Activity.RESULT_OK) onBack()
    }

    fun deleteCurrent() {
        val target = currentItem ?: return
        val intentSender = repository.createDeleteRequest(listOf(target))
        if (intentSender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        } else {
            scope.launch {
                repository.deleteDirectly(listOf(target))
                onBack()
            }
        }
    }

    fun moveCurrentTo(target: MediaFolder) {
        val item = currentItem ?: return
        performWrite({ repository.moveItem(item, target) }) {
            Toast.makeText(context, "移動しました", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !currentPageZoomed,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items[page]
            ZoomableImage(
                item = item,
                onTap = { controlsVisible = !controlsVisible },
                onZoomedChange = { zoomed -> if (page == pagerState.currentPage) currentPageZoomed = zoomed }
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                }
                Text(
                    text = currentItem?.displayName ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )
                IconButton(onClick = {
                    val target = currentItem ?: return@IconButton
                    performWrite({ repository.rotateImage(target, 90) }) {
                        Toast.makeText(context, "回転しました", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.RotateRight, contentDescription = "回転", tint = Color.White)
                }
                IconButton(onClick = { dialogState = ViewerDialogState.Properties }) {
                    Icon(Icons.Filled.Info, contentDescription = "情報", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "他のオプション", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("名前の変更") }, onClick = {
                            showMenu = false
                            dialogState = ViewerDialogState.Rename
                        })
                        DropdownMenuItem(text = { Text("非表示") }, onClick = {
                            showMenu = false
                            dialogState = ViewerDialogState.HideConfirm
                        })
                        DropdownMenuItem(text = { Text("コピー") }, onClick = {
                            showMenu = false
                            scope.launch {
                                folderList = repository.loadFolders()
                                dialogState = ViewerDialogState.Copy
                            }
                        })
                        DropdownMenuItem(text = { Text("移動") }, onClick = {
                            showMenu = false
                            scope.launch {
                                folderList = repository.loadFolders()
                                dialogState = ViewerDialogState.Move
                            }
                        })
                        DropdownMenuItem(text = { Text("ショートカットを作成") }, onClick = {
                            showMenu = false
                            currentItem?.let { createShortcut(context, it) }
                        })
                        DropdownMenuItem(text = { Text("別のアプリで開く") }, onClick = {
                            showMenu = false
                            currentItem?.let { openWithOtherApp(context, it) }
                        })
                        DropdownMenuItem(text = { Text("他で使う") }, onClick = {
                            showMenu = false
                            currentItem?.let { useAsOtherApp(context, it) }
                        })
                        DropdownMenuItem(text = { Text("リサイズ") }, onClick = {
                            showMenu = false
                            dialogState = ViewerDialogState.Resize
                        })
                        DropdownMenuItem(text = { Text("撮影日の値を修正") }, onClick = {
                            showMenu = false
                            dialogState = ViewerDialogState.DateTaken
                        })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 8.dp)
            ) {
                IconButton(onClick = {
                    currentItem?.let { favoritesStore.toggle(it.id); favoriteVersion++ }
                }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "お気に入り",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { currentItem?.let { editWithOtherApp(context, it) } }) {
                    Icon(Icons.Filled.Edit, contentDescription = "編集", tint = Color.White)
                }
                IconButton(onClick = { currentItem?.let { shareItems(context, listOf(it)) } }) {
                    Icon(Icons.Filled.Share, contentDescription = "共有", tint = Color.White)
                }
                IconButton(onClick = { deleteCurrent() }) {
                    Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.White)
                }
            }
        }
    }

    when (dialogState) {
        ViewerDialogState.Rename -> {
            currentItem?.let { target ->
                RenameDialog(item = target, onDismiss = { dialogState = null }, onConfirm = { newName ->
                    dialogState = null
                    performWrite({ repository.renameItem(target, newName) }) {
                        Toast.makeText(context, "名前を変更しました", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
        ViewerDialogState.Properties -> {
            currentItem?.let { target -> PropertiesDialog(item = target, onDismiss = { dialogState = null }) }
        }
        ViewerDialogState.Resize -> {
            ResizeDialog(onDismiss = { dialogState = null }, onConfirm = { maxDimension ->
                dialogState = null
                currentItem?.let { target ->
                    performWrite({ repository.resizeImage(target, maxDimension) }) {
                        Toast.makeText(context, "リサイズしました", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        ViewerDialogState.DateTaken -> {
            currentItem?.let { target ->
                DateTakenDialog(item = target, onDismiss = { dialogState = null }, onConfirm = { millis ->
                    dialogState = null
                    performWrite({ repository.updateDateTaken(target, millis) }) {
                        Toast.makeText(context, "撮影日を変更しました", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
        ViewerDialogState.Copy -> {
            FolderPickerDialog(
                folders = folderList,
                title = "コピー先を選択",
                onDismiss = { dialogState = null },
                onSelected = { target ->
                    dialogState = null
                    currentItem?.let { item ->
                        scope.launch {
                            repository.copyItem(item, target)
                            Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        ViewerDialogState.Move -> {
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
        ViewerDialogState.HideConfirm -> {
            ConfirmDialog(
                title = "非表示",
                message = "このフォルダをギャラリーから非表示にしますか?",
                onDismiss = { dialogState = null },
                onConfirm = {
                    dialogState = null
                    currentItem?.let { target ->
                        val folder = MediaFolder(
                            bucketId = target.bucketId,
                            name = "",
                            coverUri = target.uri,
                            itemCount = 0,
                            relativePath = target.relativePath
                        )
                        scope.launch {
                            repository.hideFolder(folder)
                            Toast.makeText(context, "非表示にしました", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                }
            )
        }
        null -> {}
    }

    pendingMoveTarget?.let { target ->
        ConfirmDialog(
            title = "移動",
            message = "「${target.name}」に移動しますか?",
            onDismiss = { pendingMoveTarget = null },
            onConfirm = {
                pendingMoveTarget = null
                moveCurrentTo(target)
            }
        )
    }
}

@Composable
private fun ZoomableImage(
    item: MediaItem,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit
) {
    var scale by remember(item.uri) { mutableStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(value: Offset, currentScale: Float): Offset {
        val maxX = (containerSize.width * (currentScale - 1) / 2f).coerceAtLeast(0f)
        val maxY = (containerSize.height * (currentScale - 1) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun applyScale(newScale: Float) {
        val clamped = newScale.coerceIn(1f, 5f)
        scale = clamped
        offset = if (clamped <= 1f) Offset.Zero else clampOffset(offset, clamped)
        onZoomedChange(clamped > 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(item.uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        applyScale(if (scale > 1f) 1f else 3f)
                    }
                )
            }
            .pointerInput(item.uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1f) clampOffset(offset + panChange, newScale) else Offset.Zero
                            onZoomedChange(newScale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1f) {
                            val panChange = event.calculatePan()
                            offset = clampOffset(offset + panChange, scale)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
