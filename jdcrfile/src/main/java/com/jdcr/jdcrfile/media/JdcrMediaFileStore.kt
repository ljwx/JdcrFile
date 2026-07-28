package com.jdcr.jdcrfile.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale

enum class JdcrMediaFileType {
    VIDEO,
    IMAGE,
    AUDIO,
}

data class JdcrMediaFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val suffix: String,
    val mimeType: String,
    val type: JdcrMediaFileType,
    val createdAtMs: Long,
    val modifiedAtMs: Long,
    val durationMs: Long,
    val width: Long,
    val height: Long,
    val rotation: Long,
)

data class JdcrRenameExtensionReport(
    val inspected: Int,
    val matched: Int,
    val renamed: Int,
    val failed: Int,
)

class JdcrMediaFileStore(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    suspend fun readSelected(uriStrings: Collection<String>): List<JdcrMediaFile> =
        withContext(Dispatchers.IO) {
            uriStrings.distinct().mapNotNull { value ->
                val uri = Uri.parse(value)
                persistAccess(uri)
                readMedia(uri)
            }
        }

    suspend fun scanTree(
        treeUri: String,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        onBatch: suspend (files: List<JdcrMediaFile>, discovered: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        require(batchSize > 0) { "batchSize must be greater than zero" }
        val tree = Uri.parse(treeUri)
        persistAccess(tree)
        val batch = ArrayList<JdcrMediaFile>(batchSize)
        var discovered = 0
        walkDocuments(tree) { uri, _, _, _ ->
            readMedia(uri)?.let { file ->
                batch += file
                discovered++
                if (batch.size == batchSize) {
                    onBatch(batch.toList(), discovered)
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) onBatch(batch.toList(), discovered)
        discovered
    }

    suspend fun delete(uriText: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriText)
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> runCatching {
                if (DocumentsContract.isDocumentUri(appContext, uri)) {
                    DocumentsContract.deleteDocument(resolver, uri)
                } else {
                    resolver.delete(uri, null, null) > 0
                }
            }.getOrDefault(false)
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.delete() == true
            else -> false
        }
    }

    suspend fun renameExtension(
        treeUri: String,
        fromExtension: String,
        toExtension: String,
        onProgress: suspend (inspected: Int) -> Unit = {},
    ): JdcrRenameExtensionReport = withContext(Dispatchers.IO) {
        val sourceExtension = normalizeExtension(fromExtension)
        val targetExtension = normalizeExtension(toExtension)
        require(sourceExtension.isNotBlank() && targetExtension.isNotBlank()) { "文件后缀不能为空" }
        val tree = Uri.parse(treeUri)
        persistAccess(tree)
        var inspected = 0
        var matched = 0
        var renamed = 0
        var failed = 0
        walkDocuments(tree) { uri, name, _, flags ->
            inspected++
            if (name.substringAfterLast('.', "").equals(sourceExtension, ignoreCase = true)) {
                matched++
                val targetName = "${name.substringBeforeLast('.')}.$targetExtension"
                val supportsRename = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
                val succeeded = supportsRename && runCatching {
                    DocumentsContract.renameDocument(resolver, uri, targetName) != null
                }.getOrDefault(false)
                if (succeeded) renamed++ else failed++
            }
            if (inspected % PROGRESS_INTERVAL == 0) onProgress(inspected)
        }
        onProgress(inspected)
        JdcrRenameExtensionReport(inspected, matched, renamed, failed)
    }

    fun persistAccess(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.getOrDefault(false)

    private suspend fun walkDocuments(
        tree: Uri,
        onDocument: suspend (uri: Uri, name: String, mimeType: String, flags: Int) -> Unit,
    ) {
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        pending += DocumentsContract.getTreeDocumentId(tree)
        while (pending.isNotEmpty()) {
            val documentId = pending.removeFirst()
            if (!visited.add(documentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
            resolver.query(childrenUri, DOCUMENT_PROJECTION, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val flagsIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val mime = cursor.getString(mimeIndex) ?: BINARY_MIME
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending += id
                    } else {
                        DocumentsContract.buildDocumentUriUsingTree(tree, id)?.let { uri ->
                            onDocument(
                                uri,
                                cursor.getString(nameIndex) ?: id.substringAfterLast('/'),
                                mime,
                                if (cursor.isNull(flagsIndex)) 0 else cursor.getInt(flagsIndex),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun readMedia(uri: Uri): JdcrMediaFile? {
        var name = uri.lastPathSegment ?: "unknown"
        var size = 0L
        var modifiedAt = 0L
        resolver.query(uri, SELECTED_PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                    name = cursor.getString(it) ?: name
                }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let {
                    size = cursor.getLong(it)
                }
                cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { modifiedAt = cursor.getLong(it) }
            }
        }
        val mime = resolver.getType(uri).takeUnless { it.isNullOrBlank() || it == BINARY_MIME }
            ?: mimeFromName(name)
        val type = mediaTypeOf(mime) ?: return null
        val embedded = readEmbeddedMetadata(uri, type)
        val createdAt = embedded.createdAtMs.takeIf { it > 0L }
            ?: modifiedAt.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        return JdcrMediaFile(
            uri = uri.toString(),
            name = name,
            sizeBytes = size,
            suffix = name.substringAfterLast('.', "").lowercase(),
            mimeType = mime,
            type = type,
            createdAtMs = createdAt,
            modifiedAtMs = modifiedAt,
            durationMs = embedded.durationMs,
            width = embedded.width,
            height = embedded.height,
            rotation = embedded.rotation,
        )
    }

    private fun readEmbeddedMetadata(uri: Uri, type: JdcrMediaFileType): EmbeddedMetadata = runCatching {
        when (type) {
            JdcrMediaFileType.IMAGE -> readImageMetadata(uri)
            JdcrMediaFileType.VIDEO, JdcrMediaFileType.AUDIO -> readAvMetadata(uri)
        }
    }.getOrDefault(EmbeddedMetadata())

    private fun readImageMetadata(uri: Uri): EmbeddedMetadata {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val exif = resolver.openInputStream(uri)?.use(::ExifInterface)
        return EmbeddedMetadata(
            createdAtMs = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let(::parseMediaDate) ?: 0L,
            width = bounds.outWidth.coerceAtLeast(0).toLong(),
            height = bounds.outHeight.coerceAtLeast(0).toLong(),
            rotation = orientationToDegrees(exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) ?: 1),
        )
    }

    private fun readAvMetadata(uri: Uri): EmbeddedMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            EmbeddedMetadata(
                createdAtMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    ?.let(::parseMediaDate) ?: 0L,
                durationMs = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                width = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                rotation = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) % 360L,
            )
        } finally {
            retriever.release()
        }
    }

    private data class EmbeddedMetadata(
        val createdAtMs: Long = 0L,
        val durationMs: Long = 0L,
        val width: Long = 0L,
        val height: Long = 0L,
        val rotation: Long = 0L,
    )

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
        const val PROGRESS_INTERVAL = 50
        const val BINARY_MIME = "application/octet-stream"

        val SELECTED_PROJECTION = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )

        fun normalizeExtension(value: String): String = value.trim().trimStart('.').lowercase()

        fun MediaMetadataRetriever.longMetadata(key: Int): Long =
            extractMetadata(key)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L

        fun mediaTypeOf(mime: String): JdcrMediaFileType? = when {
            mime.startsWith("video/") -> JdcrMediaFileType.VIDEO
            mime.startsWith("image/") -> JdcrMediaFileType.IMAGE
            mime.startsWith("audio/") -> JdcrMediaFileType.AUDIO
            else -> null
        }

        fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v", "vdat" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> BINARY_MIME
        }

        fun orientationToDegrees(orientation: Int): Long = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90L
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180L
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270L
            else -> 0L
        }

        fun parseMediaDate(value: String): Long {
            val formats = listOf(
                "yyyy:MM:dd HH:mm:ss",
                "yyyyMMdd'T'HHmmss.SSSX",
                "yyyyMMdd'T'HHmmssX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            )
            return formats.firstNotNullOfOrNull { pattern ->
                runCatching { SimpleDateFormat(pattern, Locale.US).parse(value)?.time }.getOrNull()
            } ?: 0L
        }
    }
}
