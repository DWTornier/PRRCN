package com.tornier.prrcn.ui.picker

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.tornier.prrcn.R
import com.tornier.prrcn.data.model.DeviceMedia
import com.tornier.prrcn.data.model.MediaType
import com.tornier.prrcn.ui.PackState
import com.tornier.prrcn.ui.PrrcnViewModel
import com.tornier.prrcn.ui.components.ScrimBadge
import com.tornier.prrcn.ui.components.formatDuration

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(
    vm: PrrcnViewModel,
    onClose: () -> Unit
) {
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionState = rememberMultiplePermissionsState(requiredPermissions)
    val anyGranted = permissionState.permissions.any { it.status.isGranted }

    val media by vm.deviceMedia.collectAsStateWithLifecycle()
    val loading by vm.loadingMedia.collectAsStateWithLifecycle()
    val packState by vm.packState.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<Long>() }

    LaunchedEffect(anyGranted) { if (anyGranted) vm.loadDeviceMedia() }

    LaunchedEffect(packState) {
        if (packState is PackState.Done) {
            vm.consumePackState()
            onClose()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (anyGranted && selected.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 0.dp
                ) {
                    Box(Modifier.fillMaxWidth().padding(16.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val chosen = media.filter { selected.contains(it.id) }
                                vm.pack(chosen)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.picker_pack, selected.size))
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            !anyGranted -> PermissionRequest(
                padding = padding,
                onGrant = { permissionState.launchMultiplePermissionRequest() }
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 4.dp, end = 4.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(media, key = { it.id }) { item ->
                    val isSelected = selected.contains(item.id)
                    PickerTile(item = item, selected = isSelected) {
                        if (isSelected) selected.remove(item.id) else selected.add(item.id)
                    }
                }
            }
        }
    }

    val state = packState
    if (state is PackState.Packing) {
        PackingOverlay(done = state.done, total = state.total)
    }

    if (anyGranted && !loading && media.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有找到可导入的媒体", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PickerTile(item: DeviceMedia, selected: Boolean, onToggle: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
    ) {
        if (item.type.isVisual) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                com.tornier.prrcn.ui.components.iconFor(item.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }

        if (item.type == MediaType.VIDEO && item.durationMs > 0) {
            ScrimBadge(
                text = formatDuration(item.durationMs),
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
            )
        }

        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            )
        }
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Color.White else Color.Black.copy(alpha = 0.25f))
        )
    }
}

@Composable
private fun PermissionRequest(padding: PaddingValues, onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
        Text(
            stringResource(R.string.picker_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        Text(
            stringResource(R.string.picker_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.picker_grant)) }
    }
}

@Composable
private fun PackingOverlay(done: Int, total: Int) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(40.dp)
        ) {
            Column(
                Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("正在打包…", style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text("$done / $total", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
