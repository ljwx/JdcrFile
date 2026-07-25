package com.jdcr.jdcrfile

import android.webkit.MimeTypeMap
import com.jdcr.jdcrdatabase.media.JdcrDBFeatMedia
import com.jdcr.jdcrdatabase.media.JdcrDBScannedFile
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object FileDatabaseSync {

    data class Summary(
        val scannedFileCount: Int,
        val insertedCount: Int,
        val updatedCount: Int
    )

    suspend fun sync(files: List<File>): Result<Summary> = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            val scannedAt = System.currentTimeMillis()
            val payloads = files.asSequence()
                .filter { it.isFile }
                .map { it.toScannedFile(scannedAt) }
                .toList()

            val result = JdcrDBFeatMedia.syncScannedFiles(payloads).getOrThrow()
            Result.success(
                Summary(
                    scannedFileCount = payloads.size,
                    insertedCount = result.insertedCount,
                    updatedCount = result.updatedCount
                )
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(throwable)
        }
    }

    private fun File.toScannedFile(scannedAt: Long): JdcrDBScannedFile {
        val normalizedPath = absoluteFile.normalize().path
        val normalizedSuffix = extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(normalizedSuffix)
            ?: "application/octet-stream"

        return JdcrDBScannedFile(
            uuid = stableUuid(normalizedPath),
            fileName = name,
            filePath = normalizedPath,
            fileSize = length().coerceAtLeast(0L),
            fileSuffix = normalizedSuffix,
            mimeType = mimeType,
            mediaType = mediaTypeOf(mimeType),
            sourceFrom = "local",
            scannedAt = scannedAt
        )
    }

    internal fun stableUuid(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(filePath.toByteArray(Charsets.UTF_8))
        val result = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            result[index * 2] = HEX_DIGITS[value ushr 4]
            result[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return String(result)
    }

    internal fun mediaTypeOf(mimeType: String): String = when {
        mimeType.startsWith("audio/") -> "audio"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("image/") -> "image"
        else -> "file"
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
