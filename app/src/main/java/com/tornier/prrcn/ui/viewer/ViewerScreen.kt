package com.tornier.prrcn.ui.viewer

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tornier.prrcn.data.model.MediaType
import com.tornier.prrcn.data.model.Vault
import com.tornier.prrcn.data.model.VaultEntry
import com.tornier.prrcn.ui.PrrcnViewModel
import com.tornier.prrcn.ui.components.iconFor
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    vm: PrrcnViewModel,
    onBack: () -> Unit
) {
    val vault by vm.selectedVault.collectAsStateWithLifecycle()
    val startIndex by vm.viewerStartIndex.collectAsStateWithLifecycle()
    val current = vault
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val entries by produceState(initialValue = emptyList<VaultEntry>(), key1 = current.file.absolutePath) {
        value = vm.readEntries(current)
    }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, entries.lastIndex),
        pageCount = { entries.size }
    )
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Whenever the visible page settles, recolour the whole UI from that item.
    LaunchedEffect(pagerState, entries) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val entry = entries.getOrNull(page) ?: return@collect
            val file = vm.extractForView(current, entry.name)
            vm.onMediaViewed(file, entry.type)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            val page = pagerState.currentPage.coerceIn(0, entries.lastIndex)
            TopAppBar(
                title = { Text("${page + 1} / ${entries.size}", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val entry = entries.getOrNull(page) ?: return@IconButton
                        vm.exportEntry(current, entry) { ok ->
                            scope.launch { snackbar.showSnackbar(if (ok) "已导出到相册" else "导出失败") }
                        }
                    }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            val entry = entries[page]
            ViewerPage(vault = current, entry = entry, extractor = vm::extractForView)
        }
    }
}

@Composable
private fun ViewerPage(
    vault: Vault,
    entry: VaultEntry,
    extractor: suspend (Vault, String) -> File?
) {
    val context = LocalContext.current
    val file by produceState<File?>(initialValue = null, key1 = entry.name) {
        value = extractor(vault, entry.name)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            file == null -> CircularProgressIndicator()
            entry.type == MediaType.IMAGE || entry.type == MediaType.GIF || entry.type == MediaType.LIVE_PHOTO -> {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(file).crossfade(true).build(),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Video / audio / other: preview + open with an external player.
                if (entry.type == MediaType.VIDEO) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(file).crossfade(true).build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(onClick = { openExternally(context, file!!, entry) }) {
                    Icon(
                        Icons.Rounded.PlayCircle,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(72.dp)
                    )
                }
                if (entry.type != MediaType.VIDEO) {
                    Text(
                        text = entry.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
                    )
                }
            }
        }
    }
    if (file != null && entry.type == MediaType.AUDIO) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(iconFor(entry.type), null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(160.dp))
        }
    }
}

private fun openExternally(context: android.content.Context, file: File, entry: VaultEntry) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime = when (entry.type) {
        MediaType.VIDEO -> "video/*"
        MediaType.AUDIO -> "audio/*"
        else -> "*/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
