package com.tornier.prrcn.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tornier.prrcn.R
import com.tornier.prrcn.data.model.Vault
import com.tornier.prrcn.data.model.VaultEntry
import com.tornier.prrcn.ui.PrrcnViewModel
import com.tornier.prrcn.ui.components.MediaThumbnail
import com.tornier.prrcn.ui.components.ScrimBadge
import com.tornier.prrcn.ui.components.iconFor
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    vm: PrrcnViewModel,
    onBack: () -> Unit,
    onOpenEntry: (Int) -> Unit
) {
    val vault by vm.selectedVault.collectAsStateWithLifecycle()
    val current = vault
    if (current == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val entries by produceState(initialValue = emptyList<VaultEntry>(), key1 = current.file.absolutePath) {
        value = vm.readEntries(current)
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(current.displayName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { snackbar.showSnackbar("正在导出…") }
                        vm.exportVault(current) { ok ->
                            scope.launch { snackbar.showSnackbar("已导出 $ok 项到相册") }
                        }
                    }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.vault_export))
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.vault_delete))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.vault_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(entries, key = { _, e -> e.name }) { index, entry ->
                    EntryTile(
                        vault = current,
                        entry = entry,
                        extractor = vm::extractForView,
                        onClick = { onOpenEntry(index) }
                    )
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.vault_delete)) },
            text = { Text("确定要永久删除「${current.displayName}」及其中的 ${current.itemCount} 项媒体吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteVault(current) { onBack() }
                }) { Text(stringResource(R.string.vault_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

private fun entryRatio(name: String): Float {
    val h = abs(name.hashCode())
    return 0.8f + (h % 45) / 100f // 0.8 .. 1.24
}

@Composable
private fun EntryTile(
    vault: Vault,
    entry: VaultEntry,
    extractor: suspend (Vault, String) -> File?,
    onClick: () -> Unit
) {
    val file by produceState<File?>(initialValue = null, key1 = entry.name) {
        value = if (entry.type.isVisual) extractor(vault, entry.name) else null
    }
    Box(
        modifier = Modifier
            .aspectRatio(entryRatio(entry.name))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        MediaThumbnail(model = file, type = entry.type, modifier = Modifier.fillMaxSize())
        ScrimBadge(
            text = entry.type.name,
            icon = iconFor(entry.type),
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
    }
}
