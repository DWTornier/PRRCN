package com.tornier.prrcn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Theme preference. Default is [DARK] per product requirement. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicBackground: Boolean = true,
    val deleteSourceAfterImport: Boolean = false,
    val vaultLocation: String = ""
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prrcn_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_BG = booleanPreferencesKey("dynamic_background")
        val DELETE_SOURCE = booleanPreferencesKey("delete_source")
        val VAULT_LOCATION = stringPreferencesKey("vault_location")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: ThemeMode.DARK.name) }
                .getOrDefault(ThemeMode.DARK),
            dynamicBackground = p[Keys.DYNAMIC_BG] ?: true,
            deleteSourceAfterImport = p[Keys.DELETE_SOURCE] ?: false,
            vaultLocation = p[Keys.VAULT_LOCATION] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME] = mode.name }.let { }

    suspend fun setDynamicBackground(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DYNAMIC_BG] = enabled }.let { }

    suspend fun setDeleteSourceAfterImport(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DELETE_SOURCE] = enabled }.let { }

    suspend fun setVaultLocation(path: String) =
        context.dataStore.edit { it[Keys.VAULT_LOCATION] = path }.let { }
}
