package com.example.dndsound.data.library

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.example.dndsound.core.library.IndexedFile
import com.example.dndsound.core.library.LibraryRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Walks the user-picked library tree via [DocumentsContract] directly —
 * [androidx.documentfile.provider.DocumentFile] builds a coroutine-unfriendly
 * chain of per-file resolver queries and is far too slow for big libraries.
 *
 * Emits [IndexedFile]s for audio files and reads small `meta.json` files
 * alongside them; every other entry is skipped by name only.
 */
class SafScanner(context: Context) {

    /** Thrown when the persisted tree URI can no longer be read. */
    class LibraryAccessException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private val resolver: ContentResolver = context.contentResolver

    /** True when the tree URI still has a persisted, usable read permission. */
    fun hasAccess(treeUri: Uri): Boolean {
        val persisted = resolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
        if (treeUri !in persisted) return false
        return try {
            val docId = DocumentsContract.getDocumentId(treeUri)
            resolver.query(childrenUri(treeUri, docId), PROJECTION, null, null, null)?.use { true } != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Walks the whole tree and returns every audio file plus meta.json files.
     * Audio entries carry cached/unknown durations.
     */
    suspend fun scan(
        treeUri: Uri,
        durationCache: (path: String) -> Long? = { null },
    ): List<IndexedFile> = withContext(Dispatchers.IO) {
        val rootId = try {
            DocumentsContract.getDocumentId(treeUri)
        } catch (e: IllegalArgumentException) {
            throw LibraryAccessException("Not a document tree URI: $treeUri", e)
        }
        val out = mutableListOf<IndexedFile>()
        val queue = ArrayDeque<Pair<String, String>>() // documentId -> relative dir
        queue += rootId to ""
        while (queue.isNotEmpty()) {
            val (docId, relDir) = queue.removeFirst()
            val cursor = try {
                resolver.query(childrenUri(treeUri, docId), PROJECTION, null, null, null)
                    ?: throw LibraryAccessException("Provider returned null for $relDir")
            } catch (e: Exception) {
                throw LibraryAccessException("Cannot list $relDir", e)
            }
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mimeType = c.stringOrNull(2)
                    val size = c.getLong(4)
                    val modified = c.getLong(5)
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // Hidden folders are skipped entirely.
                        if (!name.startsWith(".")) queue += id to relDir.joinDir(name)
                        continue
                    }
                    val path = relDir.joinDir(name)
                    when {
                        name.equals(META_NAME, ignoreCase = true) -> out += IndexedFile(
                            relativePath = path,
                            uri = childUri.toString(),
                            metaText = readTextOrNull(childUri),
                        )

                        LibraryRules.isAudioFile(name) -> out += IndexedFile(
                            relativePath = path,
                            uri = childUri.toString(),
                            sizeBytes = size,
                            lastModifiedMs = modified,
                            durationMs = durationCache(path),
                        )
                    }
                }
            }
        }
        out
    }

    /** Creates the convention skeleton (music/ambience/sounds/weather) if absent. */
    suspend fun createFolderStructure(treeUri: Uri): Unit = withContext(Dispatchers.IO) {
        for (dir in listOf("music", "ambience", "sounds", "weather")) {
            try {
                DocumentsContract.createDocument(
                    resolver,
                    treeUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    dir,
                )
            } catch (_: Exception) {
                // Already exists or provider refused; harmless either way.
            }
        }
    }

    private fun childrenUri(treeUri: Uri, parentDocId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(treeUri.authority, parentDocId),
            parentDocId,
        )

    private fun String.joinDir(child: String) = if (isEmpty()) child else "$this/$child"

    private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun readTextOrNull(uri: Uri): String? = try {
        resolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            String(bytes, Charsets.UTF_8).ifEmpty { null }
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val META_NAME = "meta.json"
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
