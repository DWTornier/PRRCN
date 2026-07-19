package com.tornier.prrcn.ui.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tornier.prrcn.R
import com.tornier.prrcn.data.model.Vault
import com.tornier.prrcn.ui.PrrcnViewModel
import com.tornier.prrcn.ui.components.VaultStaggeredGrid

@Composable
fun VaultsScreen(
    vm: PrrcnViewModel,
    contentPadding: PaddingValues,
    onOpenVault: (Vault) -> Unit
) {
    val vaults by vm.vaults.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshVaults() }

    val pad = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = contentPadding.calculateTopPadding() + 8.dp,
        bottom = contentPadding.calculateBottomPadding() + 96.dp
    )

    if (vaults.isEmpty()) {
        Box(
            Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        VaultStaggeredGrid(
            vaults = vaults,
            contentPadding = pad,
            coverExtractor = vm::extractForView,
            onOpenVault = onOpenVault,
            header = {
                Text(
                    text = stringResource(R.string.nav_vaults),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        )
    }
}
