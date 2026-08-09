package com.jolno.mygallery.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

sealed class WriteResult {
    data object Success : WriteResult()
    data class NeedsPermission(val intentSender: IntentSender) : WriteResult()
    data object Failed : WriteResult()
}

class MediaRepository(private val context: Context) {

    suspend fun loadFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        val items = loadAllItems()
        items
            .groupBy { it.bucketId }
            .mapNotNull { (bucketId, list) ->
                val newest = list.maxByOrNull { it.dateAddedSec } ?: return@mapNotNull null
                val name = bucketNames[bucketId] ?: bucketId
                MediaFolder(
                    bucketId = bucketId,
                    name = name,
                    coverUri = newest.uri,
                    itemCount = list.size,
                    relativePath = newest.relativePath,
                    latestDateSec = newest.dateAddedSec
                )
            }
            .sortedByDescending { it.latestDateSec }
    }

    suspend fun loadItemsForBucket(bucketId: String): List<MediaItem> = withContext(Dispatchers.IO) {
        loadAllItems().filter { it.bucketId == bucketId }.sortedByDescending { it.dateAddedSec }
    }

    suspend fun loadAllMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        loadAllItems().sortedByDescending { it.dateAddedSec }
    }

    private val bucketNames = HashMap<String, String>()

    private fun loadAllItems(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Video.VideoColumns.DURATION
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val queryUri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
            val dateTakenCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
            val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val bucketId = cursor.getString(bucketIdCol) ?: "unknown"
                val bucketName = cursor.getString(bucketNameCol) ?: "不明"
                bucketNames[bucketId] = bucketName
                val type = cursor.getInt(typeCol)
                val kind = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) MediaKind.VIDEO else MediaKind.IMAGE
                val dateAdded = cursor.getLong(dateCol)
                val duration = if (kind == MediaKind.VIDEO && durationCol >= 0) cursor.getLong(durationCol) else 0L

                val contentUri = if (kind == MediaKind.VIDEO) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val uri = ContentUris.withAppendedId(contentUri, id)

                result += MediaItem(
                    id = id,
                    uri = uri,
                    bucketId = bucketId,
                    kind = kind,
                    dateAddedSec = dateAdded,
                    durationMs = duration,
                    displayName = cursor.getString(nameCol) ?: "",
                    relativePath = cursor.getString(pathCol) ?: "",
                    size = cursor.getLong(sizeCol),
                    width = if (widthCol >= 0) cursor.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) cursor.getInt(heightCol) else 0,
                    dateTakenMs = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                )
            }
        }
        return result
    }

    /** API 30+: 削除の確認をユーザーに求めるためのIntentSenderを返す。API29以下ではnull。 */
    fun createDeleteRequest(items: List<MediaItem>): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri }).intentSender
        } else {
            null
        }
    }

    suspend fun deleteDirectly(items: List<MediaItem>): WriteResult = withContext(Dispatchers.IO) {
        try {
            items.forEach { item -> context.contentResolver.delete(item.uri, null, null) }
            WriteResult.Success
        } catch (e: RecoverableSecurityException) {
            WriteResult.NeedsPermission(e.userAction.actionIntent.intentSender)
        } catch (e: Exception) {
            WriteResult.Failed
        }
    }

    private fun <T> guardedWrite(block: () -> T): WriteResult {
        return try {
            block()
            WriteResult.Success
        } catch (e: RecoverableSecurityException) {
            WriteResult.NeedsPermission(e.userAction.actionIntent.intentSender)
        } catch (e: Exception) {
            WriteResult.Failed
        }
    }

    suspend fun renameItem(item: MediaItem, newDisplayName: String): WriteResult = withContext(Dispatchers.IO) {
        guardedWrite {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, newDisplayName)
            }
            context.contentResolver.update(item.uri, values, null, null)
        }
    }

    suspend fun rotateImage(item: MediaItem, degrees: Int): WriteResult = withContext(Dispatchers.IO) {
        if (item.kind != MediaKind.IMAGE) return@withContext WriteResult.Failed
        guardedWrite {
            val bitmap = context.contentResolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: error("decode failed")
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            context.contentResolver.openOutputStream(item.uri, "wt")?.use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    }

    suspend fun resizeImage(item: MediaItem, maxDimension: Int): WriteResult = withContext(Dispatchers.IO) {
        if (item.kind != MediaKind.IMAGE) return@withContext WriteResult.Failed
        guardedWrite {
            val bitmap = context.contentResolver.openInputStream(item.uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: error("decode failed")
            val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (ratio < 1f) {
                val newWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                context.contentResolver.openOutputStream(item.uri, "wt")?.use { out ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        }
    }

    suspend fun updateDateTaken(item: MediaItem, epochMillis: Long): WriteResult = withContext(Dispatchers.IO) {
        guardedWrite {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DATE_TAKEN, epochMillis)
            }
            context.contentResolver.update(item.uri, values, null, null)

            if (item.kind == MediaKind.IMAGE) {
                runCatching {
                    context.contentResolver.openFileDescriptor(item.uri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, format.format(epochMillis))
                        exif.saveAttributes()
                    }
                }
            }
        }
    }

    suspend fun copyItem(item: MediaItem, targetFolder: MediaFolder): WriteResult = withContext(Dispatchers.IO) {
        guardedWrite {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, item.displayName)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, targetFolder.relativePath)
                put(MediaStore.Files.FileColumns.MIME_TYPE, context.contentResolver.getType(item.uri))
            }
            val contentUri = if (item.kind == MediaKind.VIDEO) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val newUri = context.contentResolver.insert(contentUri, values) ?: error("insert failed")
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                context.contentResolver.openOutputStream(newUri)?.use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun moveItem(item: MediaItem, targetFolder: MediaFolder): WriteResult {
        val copyResult = copyItem(item, targetFolder)
        if (copyResult !is WriteResult.Success) return copyResult
        return deleteDirectly(listOf(item))
    }

    /** フォルダを非表示にする(.nomedia ファイルを MediaStore 経由で作成)。 */
    suspend fun hideFolder(folder: MediaFolder): WriteResult = withContext(Dispatchers.IO) {
        guardedWrite {
            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, ".nomedia")
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, folder.relativePath)
                put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: error("insert failed")
            context.contentResolver.openOutputStream(uri)?.close()
        }
    }

    fun getPicturesFolder(): MediaFolder = MediaFolder(
        bucketId = "pictures_root",
        name = "Pictures",
        coverUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        itemCount = 0,
        relativePath = Environment.DIRECTORY_PICTURES + File.separator
    )
}
