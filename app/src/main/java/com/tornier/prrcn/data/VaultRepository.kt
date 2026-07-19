package com.tornier.prrcn.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tornier.prrcn.data.model.MediaType
import com.tornier.prrcn.data.model.Vault
import com.tornier.prrcn.data.model.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Packs private media into obfuscated zip archives ("vaults") so they are invisible to
 * the system media scanner (stored under a `.nomedia` directory with a non-media
 * extension), and reads them back for browsing / exporting.
 */
class VaultRepository(private val context: Context) {

    companion object {
        const val EXT = "prz"
        private const val DIR_NAME = "PRRCN"
    }

    /**
     * Resolve the directory vaults live in. When [customPath] is a usable, writable
     * directory it is used; otherwise falls back to the app-specific external dir which
     * is always writable and never indexed by the media scanner.
     */
    fun resolveVaultDir(customPath: String?): File {
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.isDirectory || f.mkdirs()) return ensureNoMedia(f)
        }
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return ensureNoMedia(dir)
    }

    /** A human-readable representation of the default public location, for display only. */
    fun defaultPublicLocationLabel(): String =
        "${Environment.DIRECTORY_DCIM}/../$DIR_NAME (应用私有目录)"

    private fun ensureNoMedia(dir: File): File {
        runCatching {
            val nomedia = File(dir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        }
        return dir
    }

    // ---------------------------------------------------------------- listing

    suspend fun listVaults(customPath: String?): List<Vault> = withContext(Dispatchers.IO) {
        val dir = resolveVaultDir(customPath)
        val files = dir.listFiles { f -> f.isFile && f.extension.equals(EXT, true) } ?: emptyArray()
        files.mapNotNull { readVaultMeta(it) }.sortedByDescending { it.lastModified }
    }

    private fun readVaultMeta(file: File): Vault? = runCatching {
        ZipFile(file).use { zip ->
            var count = 0
            var cover: String? = null
            var total = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                count++
                total += if (e.size >= 0) e.size else 0
                if (cover == null) {
                    val t = MediaType.fromMime(null, e.name)
                    if (t == MediaType.IMAGE || t == MediaType.GIF || t == MediaType.VIDEO || t == MediaType.LIVE_PHOTO) {
                        cover = e.name
                    }
                }
            }
            Vault(
                file = file,
                displayName = file.nameWithoutExtension,
                itemCount = count,
                sizeBytes = file.length(),
                lastModified = file.lastModified(),
                coverEntry = cover
            )
        }
    }.getOrNull()

    suspend fun readEntries(vault: Vault): List<VaultEntry> = withContext(Dispatchers.IO) {
        val result = ArrayList<VaultEntry>()
        runCatching {
            ZipFile(vault.file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    result += VaultEntry(
                        name = e.name,
                        type = MediaType.fromMime(null, e.name),
                        size = if (e.size >= 0) e.size else 0
                    )
                }
            }
        }
        result.sortedBy { it.name }
    }

    // ---------------------------------------------------------------- packing

    data class PackSource(val displayName: String, val uri: Uri)

    /**
     * Copy every source into a brand new vault. Returns the created [Vault].
     * [onProgress] reports (done, total).
     */
    suspend fun packIntoNewVault(
        customPath: String?,
        vaultName: String,
        sources: List<PackSource>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Vault? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) return@withContext null
        val dir = resolveVaultDir(customPath)
        val target = uniqueFile(dir, vaultName)
        val usedNames = HashSet<String>()
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            zos.setLevel(Deflater.BEST_SPEED)
            sources.forEachIndexed { index, src ->
                val entryName = uniqueEntryName(usedNames, src.displayName)
                try {
                    context.contentResolver.openInputStream(src.uri)?.use { input ->
                        zos.putNextEntry(ZipEntry(entryName))
                        input.copyTo(zos, 64 * 1024)
                        zos.closeEntry()
                    }
                } catch (_: Exception) {
                    // Skip individual failures so one bad file does not abort the batch.
                }
                onProgress(index + 1, sources.size)
            }
        }
        readVaultMeta(target)
    }

    /** Delete the original device media after a successful pack (best-effort). */
    suspend fun deleteSources(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        uris.forEach { uri ->
            runCatching {
                if (context.contentResolver.delete(uri, null, null) > 0) deleted++
            }
        }
        deleted
    }

    // ---------------------------------------------------------------- reading

    /** Extract a single entry into the cache dir and return the file (loadable by Coil). */
    suspend fun extractToCache(vault: Vault, entryName: String): File? =
        withContext(Dispatchers.IO) {
            val safe = entryName.replace('/', '_').replace('\\', '_')
            val outDir = File(context.cacheDir, "vault_view/${vault.file.nameWithoutExtension}")
            outDir.mkdirs()
            val out = File(outDir, safe)
            if (out.exists() && out.length() > 0) return@withContext out
            runCatching {
                ZipInputStream(vault.file.inputStream().buffered()).use { zis ->
                    var e = zis.nextEntry
                    while (e != null) {
                        if (e.name == entryName) {
                            out.outputStream().buffered().use { zis.copyTo(it, 64 * 1024) }
                            return@runCatching out
                        }
                        e = zis.nextEntry
                    }
                    null
                }
            }.getOrNull()
        }

    // ---------------------------------------------------------------- deleting

    suspend fun deleteVault(vault: Vault): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            File(context.cacheDir, "vault_view/${vault.file.nameWithoutExtension}").deleteRecursively()
        }
        vault.file.delete()
    }

    // ---------------------------------------------------------------- exporting

    /** Export one entry back into the public gallery / downloads. */
    suspend fun exportEntry(vault: Vault, entry: VaultEntry): Boolean =
        withContext(Dispatchers.IO) {
            val cached = extractToCache(vault, entry.name) ?: return@withContext false
            exportFileToMediaStore(cached, entry.name, entry.type)
        }

    /** Export every item in a vault. Returns number of successfully exported entries. */
    suspend fun exportVault(vault: Vault): Int = withContext(Dispatchers.IO) {
        val entries = readEntries(vault)
        var ok = 0
        entries.forEach { if (exportEntry(vault, it)) ok++ }
        ok
    }

    private fun exportFileToMediaStore(file: File, name: String, type: MediaType): Boolean {
        val resolver = context.contentResolver
        val (collection, relative) = when (type) {
            MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name.substringAfterLast('/'))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relative/$DIR_NAME")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- helpers

    private fun uniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.$EXT")
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($i).$EXT")
            i++
        }
        return candidate
    }

    private fun uniqueEntryName(used: HashSet<String>, name: String): String {
        var n = name
        var i = 1
        while (!used.add(n)) {
            val dot = name.lastIndexOf('.')
            n = if (dot > 0) "${name.substring(0, dot)}_$i${name.substring(dot)}" else "${name}_$i"
            i++
        }
        return n
    }
}
