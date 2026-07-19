package com.tornier.prrcn.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tornier.prrcn.R
import com.tornier.prrcn.data.model.MediaType
import com.tornier.prrcn.data.model.Vault
import com.tornier.prrcn.ui.PrrcnViewModel
import com.tornier.prrcn.ui.components.VaultStaggeredGrid

private const val RECENT_LIMIT = 8

@Composable
fun HomeScreen(
    vm: PrrcnViewModel,
    contentPadding: PaddingValues,
    onOpenVault: (Vault) -> Unit,
    onSeeAll: () -> Unit,
    onImport: () -> Unit
) {
    val vaults by vm.vaults.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshVaults() }

    // Give the app an initial themed backdrop from the most recent item.
    LaunchedEffect(vaults.firstOrNull()?.file?.absolutePath) {
        val newest = vaults.firstOrNull()
        val entry = newest?.coverEntry
        if (newest != null && entry != null) {
            val file = vm.extractForView(newest, entry)
            vm.onMediaViewed(file, MediaType.fromMime(null, entry))
        }
    }

    val recent = vaults.take(RECENT_LIMIT)
    val pad = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp
    )

    if (vaults.isEmpty()) {
        EmptyHome(contentPadding = contentPadding, onImport = onImport)
    } else {
        VaultStaggeredGrid(
            vaults = recent,
            contentPadding = pad,
            coverExtractor = vm::extractForView,
            onOpenVault = onOpenVault,
            header = {
                Column {
                    HomeHeader()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_recent),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (vaults.size > RECENT_LIMIT) {
                            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.nav_vaults)) }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.mipmap.prrcnxxxhdpi),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "隐私媒体保险库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyHome(contentPadding: PaddingValues, onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.mipmap.prrcnxxxhdpi),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onImport) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_import))
            }
        }
    }
}
