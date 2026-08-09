package com.jolno.mygallery.data

import android.net.Uri

data class MediaFolder(
    val bucketId: String,
    val name: String,
    val coverUri: Uri,
    val itemCount: Int,
    val relativePath: String = "",
    val latestDateSec: Long = 0L
)

enum class MediaKind { IMAGE, VIDEO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val bucketId: String,
    val kind: MediaKind,
    val dateAddedSec: Long,
    val durationMs: Long = 0L,
    val displayName: String = "",
    val relativePath: String = "",
    val size: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val dateTakenMs: Long = 0L
)
