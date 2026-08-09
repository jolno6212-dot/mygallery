package com.jolno.mygallery

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.jolno.mygallery.data.AppSettingsStore
import com.jolno.mygallery.data.MediaFolder
import com.jolno.mygallery.data.MediaItem
import com.jolno.mygallery.data.MediaRepository
import com.jolno.mygallery.ui.screens.FolderListScreen
import com.jolno.mygallery.ui.screens.MediaGridScreen
import com.jolno.mygallery.ui.screens.MediaViewerScreen
import com.jolno.mygallery.ui.theme.MyGalleryTheme

private sealed class ViewerOrigin {
    data object AllMedia : ViewerOrigin()
    data class Folder(val folder: MediaFolder) : ViewerOrigin()
}

private sealed class Screen {
    data object Folders : Screen()
    data class Grid(val folder: MediaFolder) : Screen()
    data class Viewer(
        val origin: ViewerOrigin,
        val items: List<MediaItem>,
        val startIndex: Int,
        val autoPlay: Boolean = false
    ) : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyGalleryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GalleryApp()
                }
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@Composable
private fun GalleryApp() {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }

    var granted by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }

    if (!granted) {
        PermissionRequestScreen(onRequest = { launcher.launch(permissions) })
        return
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Folders) }
    var showAllMedia by remember { mutableStateOf(false) }
    val settingsStore = remember { AppSettingsStore(context) }
    val repository = remember { MediaRepository(context) }

    LaunchedEffect(Unit) {
        val defaultId = settingsStore.defaultBucketId
        if (defaultId != null) {
            val folder = repository.loadFolders().find { it.bucketId == defaultId }
            if (folder != null) screen = Screen.Grid(folder)
        }
    }

    fun backTargetFor(origin: ViewerOrigin): Screen = when (origin) {
        ViewerOrigin.AllMedia -> Screen.Folders
        is ViewerOrigin.Folder -> Screen.Grid(origin.folder)
    }

    when (val current = screen) {
        is Screen.Folders -> FolderListScreen(
            showAllMedia = showAllMedia,
            onToggleAllMedia = { showAllMedia = !showAllMedia },
            onFolderClick = { folder -> screen = Screen.Grid(folder) },
            onMediaItemClick = { items, index ->
                screen = Screen.Viewer(ViewerOrigin.AllMedia, items, index)
            }
        )
        is Screen.Grid -> {
            BackHandler { screen = Screen.Folders }
            MediaGridScreen(
                bucketId = current.folder.bucketId,
                bucketName = current.folder.name,
                onBack = { screen = Screen.Folders },
                onItemClick = { items, index ->
                    screen = Screen.Viewer(ViewerOrigin.Folder(current.folder), items, index)
                },
                onSlideshowClick = { items ->
                    if (items.isNotEmpty()) {
                        screen = Screen.Viewer(ViewerOrigin.Folder(current.folder), items, 0, autoPlay = true)
                    }
                }
            )
        }
        is Screen.Viewer -> {
            val backTarget = backTargetFor(current.origin)
            BackHandler { screen = backTarget }
            MediaViewerScreen(
                items = current.items,
                startIndex = current.startIndex,
                autoPlay = current.autoPlay,
                onBack = { screen = backTarget }
            )
        }
    }
}

@Composable
private fun PermissionRequestScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "写真・動画を表示するには権限が必要です",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onRequest) {
                Text("権限を許可する")
            }
        }
    }
}
