package com.tornier.prrcn.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tornier.prrcn.data.MediaRepository
import com.tornier.prrcn.data.Settings
import com.tornier.prrcn.data.SettingsRepository
import com.tornier.prrcn.data.ThemeMode
import com.tornier.prrcn.data.VaultRepository
import com.tornier.prrcn.data.model.DeviceMedia
import com.tornier.prrcn.data.model.MediaType
import com.tornier.prrcn.data.model.Vault
import com.tornier.prrcn.data.model.VaultEntry
import com.tornier.prrcn.ui.color.BrandSeed
import com.tornier.prrcn.ui.color.blurBitmap
import com.tornier.prrcn.ui.color.loadMediaSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface PackState {
    data object Idle : PackState
    data class Packing(val done: Int, val total: Int) : PackState
    data class Done(val vault: Vault?, val deletedSources: Int) : PackState
    data object Error : PackState
}

/**
 * Single source of truth for the whole app: theme seed colour + acrylic backdrop, the
 * list of vaults, the device media picker, packing progress and user settings.
 */
class PrrcnViewModel(app: Application) : AndroidViewModel(app) {

    private val mediaRepo = MediaRepository(app)
    private val vaultRepo = VaultRepository(app)
    private val settingsRepo = SettingsRepository(app)

    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _vaults = MutableStateFlow<List<Vault>>(emptyList())
    val vaults: StateFlow<List<Vault>> = _vaults.asStateFlow()

    private val _seedColor = MutableStateFlow(BrandSeed)
    val seedColor: StateFlow<Color> = _seedColor.asStateFlow()

    private val _background = MutableStateFlow<Bitmap?>(null)
    val background: StateFlow<Bitmap?> = _background.asStateFlow()

    private val _deviceMedia = MutableStateFlow<List<DeviceMedia>>(emptyList())
    val deviceMedia: StateFlow<List<DeviceMedia>> = _deviceMedia.asStateFlow()

    private val _loadingMedia = MutableStateFlow(false)
    val loadingMedia: StateFlow<Boolean> = _loadingMedia.asStateFlow()

    private val _packState = MutableStateFlow<PackState>(PackState.Idle)
    val packState: StateFlow<PackState> = _packState.asStateFlow()

    /** The vault currently opened for browsing (navigation payload). */
    private val _selectedVault = MutableStateFlow<Vault?>(null)
    val selectedVault: StateFlow<Vault?> = _selectedVault.asStateFlow()

    /** Index of the entry to open in the full-screen viewer. */
    private val _viewerStartIndex = MutableStateFlow(0)
    val viewerStartIndex: StateFlow<Int> = _viewerStartIndex.asStateFlow()

    fun selectVault(vault: Vault) { _selectedVault.value = vault }
    fun openViewerAt(index: Int) { _viewerStartIndex.value = index }

    init {
        viewModelScope.launch {
            settings.collect { refreshVaults() }
        }
    }

    // ------------------------------------------------------------- vaults

    fun refreshVaults() {
        viewModelScope.launch {
            _vaults.value = vaultRepo.listVaults(settings.value.vaultLocation)
        }
    }

    fun deleteVault(vault: Vault, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            vaultRepo.deleteVault(vault)
            refreshVaults()
            onDone()
        }
    }

    suspend fun readEntries(vault: Vault): List<VaultEntry> = vaultRepo.readEntries(vault)

    suspend fun extractForView(vault: Vault, entryName: String): File? =
        vaultRepo.extractToCache(vault, entryName)

    fun exportEntry(vault: Vault, entry: VaultEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(vaultRepo.exportEntry(vault, entry)) }
    }

    fun exportVault(vault: Vault, onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(vaultRepo.exportVault(vault)) }
    }

    // ------------------------------------------------------------- picker

    fun loadDeviceMedia() {
        viewModelScope.launch {
            _loadingMedia.value = true
            _deviceMedia.value = mediaRepo.queryDeviceMedia()
            _loadingMedia.value = false
        }
    }

    fun pack(selected: List<DeviceMedia>) {
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _packState.value = PackState.Packing(0, selected.size)
            val name = "导入 " + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val sources = selected.map { VaultRepository.PackSource(it.displayName, it.uri) }
            val vault = vaultRepo.packIntoNewVault(
                customPath = settings.value.vaultLocation,
                vaultName = name,
                sources = sources
            ) { done, total -> _packState.value = PackState.Packing(done, total) }

            var deleted = 0
            if (vault != null && settings.value.deleteSourceAfterImport) {
                deleted = vaultRepo.deleteSources(selected.map { it.uri })
            }
            refreshVaults()
            _packState.value = if (vault != null) PackState.Done(vault, deleted) else PackState.Error
        }
    }

    fun consumePackState() { _packState.value = PackState.Idle }

    // ------------------------------------------------------------- theming

    /** Called whenever the user views a media item; drives the seed colour + backdrop. */
    fun onMediaViewed(model: Any?, type: MediaType?) {
        if (model == null) return
        viewModelScope.launch {
            val seed = loadMediaSeed(getApplication(), model, type) ?: return@launch
            _seedColor.value = seed.color
            if (settings.value.dynamicBackground && seed.thumbnail != null) {
                val blurred = withContext(Dispatchers.Default) { blurBitmap(seed.thumbnail) }
                _background.value = blurred
            }
        }
    }

    // ------------------------------------------------------------- settings

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setDynamicBackground(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setDynamicBackground(enabled)
        if (!enabled) _background.value = null
    }.let { }
    fun setDeleteSource(enabled: Boolean) =
        viewModelScope.launch { settingsRepo.setDeleteSourceAfterImport(enabled) }.let { }
    fun setVaultLocation(path: String) =
        viewModelScope.launch { settingsRepo.setVaultLocation(path) }.let { }

    fun defaultLocationLabel(): String = vaultRepo.defaultPublicLocationLabel()
}
