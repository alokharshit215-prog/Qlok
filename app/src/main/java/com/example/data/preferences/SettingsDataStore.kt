package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.SortDirection
import com.example.model.SortOption
import com.example.model.ThemeMode
import com.example.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blue_file_manager_settings")

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useBlueBrandTheme: Boolean = true,
    val defaultViewMode: ViewMode = ViewMode.LIST,
    val defaultSortOption: SortOption = SortOption.NAME,
    val defaultSortDirection: SortDirection = SortDirection.ASCENDING,
    val showHiddenFiles: Boolean = false,
    val autoRefresh: Boolean = true,
    val largeFileThresholdMb: Int = 10,
    val editorFontSizeSp: Int = 14,
    val editorWordWrap: Boolean = true,
    val editorLineNumbers: Boolean = true,
    val editorSyntaxHighlighting: Boolean = true,
    val editorDarkTheme: Boolean = true,
    val editorAutoSaveSeconds: Int = 0, // 0 = Off, 30, 60, 300
    val recycleBinEnabled: Boolean = true,
    val recycleBinAutoCleanDays: Int = 30,
    val appLockEnabled: Boolean = false,
    val appLockPinHash: String = "",
    val appLockBiometricEnabled: Boolean = false,
    val scanNotificationsEnabled: Boolean = true,
    val operationNotificationsEnabled: Boolean = true,
    val lastScanTimeMillis: Long = 0L
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BLUE_BRAND_THEME = booleanPreferencesKey("blue_brand_theme")
        val DEFAULT_VIEW_MODE = stringPreferencesKey("default_view_mode")
        val DEFAULT_SORT_OPTION = stringPreferencesKey("default_sort_option")
        val DEFAULT_SORT_DIRECTION = stringPreferencesKey("default_sort_direction")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        val LARGE_FILE_THRESHOLD_MB = intPreferencesKey("large_file_threshold_mb")
        val EDITOR_FONT_SIZE_SP = intPreferencesKey("editor_font_size_sp")
        val EDITOR_WORD_WRAP = booleanPreferencesKey("editor_word_wrap")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val EDITOR_SYNTAX_HIGHLIGHT = booleanPreferencesKey("editor_syntax_highlight")
        val EDITOR_DARK_THEME = booleanPreferencesKey("editor_dark_theme")
        val EDITOR_AUTO_SAVE_SECONDS = intPreferencesKey("editor_auto_save_seconds")
        val RECYCLE_BIN_ENABLED = booleanPreferencesKey("recycle_bin_enabled")
        val RECYCLE_BIN_AUTO_CLEAN_DAYS = intPreferencesKey("recycle_bin_auto_clean_days")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        val APP_LOCK_BIOMETRIC_ENABLED = booleanPreferencesKey("app_lock_biometric_enabled")
        val SCAN_NOTIFICATIONS = booleanPreferencesKey("scan_notifications")
        val OPERATION_NOTIFICATIONS = booleanPreferencesKey("operation_notifications")
        val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            useBlueBrandTheme = prefs[Keys.BLUE_BRAND_THEME] ?: true,
            defaultViewMode = prefs[Keys.DEFAULT_VIEW_MODE]?.let { runCatching { ViewMode.valueOf(it) }.getOrNull() } ?: ViewMode.LIST,
            defaultSortOption = prefs[Keys.DEFAULT_SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() } ?: SortOption.NAME,
            defaultSortDirection = prefs[Keys.DEFAULT_SORT_DIRECTION]?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() } ?: SortDirection.ASCENDING,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN_FILES] ?: false,
            autoRefresh = prefs[Keys.AUTO_REFRESH] ?: true,
            largeFileThresholdMb = prefs[Keys.LARGE_FILE_THRESHOLD_MB] ?: 10,
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE_SP] ?: 14,
            editorWordWrap = prefs[Keys.EDITOR_WORD_WRAP] ?: true,
            editorLineNumbers = prefs[Keys.EDITOR_LINE_NUMBERS] ?: true,
            editorSyntaxHighlighting = prefs[Keys.EDITOR_SYNTAX_HIGHLIGHT] ?: true,
            editorDarkTheme = prefs[Keys.EDITOR_DARK_THEME] ?: true,
            editorAutoSaveSeconds = prefs[Keys.EDITOR_AUTO_SAVE_SECONDS] ?: 0,
            recycleBinEnabled = prefs[Keys.RECYCLE_BIN_ENABLED] ?: true,
            recycleBinAutoCleanDays = prefs[Keys.RECYCLE_BIN_AUTO_CLEAN_DAYS] ?: 30,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            appLockPinHash = prefs[Keys.APP_LOCK_PIN_HASH] ?: "",
            appLockBiometricEnabled = prefs[Keys.APP_LOCK_BIOMETRIC_ENABLED] ?: false,
            scanNotificationsEnabled = prefs[Keys.SCAN_NOTIFICATIONS] ?: true,
            operationNotificationsEnabled = prefs[Keys.OPERATION_NOTIFICATIONS] ?: true,
            lastScanTimeMillis = prefs[Keys.LAST_SCAN_TIME] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setBlueBrandTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BLUE_BRAND_THEME] = enabled }
    }

    suspend fun setDefaultViewMode(mode: ViewMode) {
        context.dataStore.edit { it[Keys.DEFAULT_VIEW_MODE] = mode.name }
    }

    suspend fun setDefaultSort(option: SortOption, direction: SortDirection) {
        context.dataStore.edit {
            it[Keys.DEFAULT_SORT_OPTION] = option.name
            it[Keys.DEFAULT_SORT_DIRECTION] = direction.name
        }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_HIDDEN_FILES] = show }
    }

    suspend fun setAutoRefresh(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_REFRESH] = enabled }
    }

    suspend fun setLargeFileThresholdMb(mb: Int) {
        context.dataStore.edit { it[Keys.LARGE_FILE_THRESHOLD_MB] = mb }
    }

    suspend fun setEditorFontSizeSp(sp: Int) {
        context.dataStore.edit { it[Keys.EDITOR_FONT_SIZE_SP] = sp.coerceIn(10, 26) }
    }

    suspend fun setEditorWordWrap(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDITOR_WORD_WRAP] = enabled }
    }

    suspend fun setEditorLineNumbers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDITOR_LINE_NUMBERS] = enabled }
    }

    suspend fun setEditorSyntaxHighlighting(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDITOR_SYNTAX_HIGHLIGHT] = enabled }
    }

    suspend fun setEditorDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EDITOR_DARK_THEME] = enabled }
    }

    suspend fun setEditorAutoSaveSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.EDITOR_AUTO_SAVE_SECONDS] = seconds }
    }

    suspend fun setRecycleBinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RECYCLE_BIN_ENABLED] = enabled }
    }

    suspend fun setRecycleBinAutoCleanDays(days: Int) {
        context.dataStore.edit { it[Keys.RECYCLE_BIN_AUTO_CLEAN_DAYS] = days }
    }

    suspend fun setAppLock(enabled: Boolean, pinPlainText: String? = null, biometric: Boolean? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = enabled
            if (pinPlainText != null) {
                prefs[Keys.APP_LOCK_PIN_HASH] = hashPin(pinPlainText)
            }
            if (biometric != null) {
                prefs[Keys.APP_LOCK_BIOMETRIC_ENABLED] = biometric
            }
        }
    }

    suspend fun setScanNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCAN_NOTIFICATIONS] = enabled }
    }

    suspend fun setOperationNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OPERATION_NOTIFICATIONS] = enabled }
    }

    suspend fun setLastScanTime(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SCAN_TIME] = timestamp }
    }

    companion object {
        private const val PIN_SALT = "BFM_ALOK_SECURE_PIN_V1_"

        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest((PIN_SALT + pin).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun verifyPin(pin: String, storedHash: String): Boolean {
            if (storedHash.isBlank()) return false
            return hashPin(pin) == storedHash
        }
    }
}
