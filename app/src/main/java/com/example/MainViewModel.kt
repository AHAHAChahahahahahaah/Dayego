package com.example

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiState {
    data class Success(
        val packages: List<String>,
        val selectedPackage: String,
        val worlds: List<MinecraftWorld>,
        val isScanning: Boolean,
        val shizukuAvailable: Boolean,
        val shizukuPermission: Boolean,
        val resolvedPath: String = "",
        val error: String? = null
    ) : UiState
}

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(
        UiState.Success(
            packages = listOf("com.mojang.minecraftpe", "com.mojang.minecrafttrialpe"),
            selectedPackage = "com.mojang.minecraftpe",
            worlds = emptyList(),
            isScanning = false,
            shizukuAvailable = false,
            shizukuPermission = false,
            resolvedPath = "",
            error = null
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile.asStateFlow()

    private val _showToastMsg = MutableStateFlow<String?>(null)
    val showToastMsg: StateFlow<String?> = _showToastMsg.asStateFlow()

    private val _isRussian = MutableStateFlow<Boolean>(true)
    val isRussian: StateFlow<Boolean> = _isRussian.asStateFlow()

    fun toggleLanguage() {
        _isRussian.value = !_isRussian.value
    }

    /**
     * Updates Shizuku diagnostic parameters. But we NEVER lock the UI.
     */
    fun updateDiagnostics(context: Context) {
        val current = _uiState.value as? UiState.Success ?: return
        val available = ShizukuHelper.isAvailable()
        val permission = ShizukuHelper.hasPermission(context)
        _uiState.value = current.copy(
            shizukuAvailable = available,
            shizukuPermission = permission
        )
    }

    fun discoverPackages(context: Context) {
        val current = _uiState.value as? UiState.Success ?: return
        viewModelScope.launch {
            val pm = context.packageManager
            val discovered = withContext(Dispatchers.IO) {
                try {
                    val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                    apps.map { it.packageName }
                        .filter { it.startsWith("com.mojang") }
                } catch (e: Exception) {
                    emptyList<String>()
                }
            }
            if (discovered.isNotEmpty()) {
                val newList = (current.packages + discovered).distinct()
                val newSelected = if (discovered.contains(current.selectedPackage)) {
                    current.selectedPackage
                } else {
                    discovered.first()
                }
                _uiState.value = current.copy(
                    packages = newList,
                    selectedPackage = newSelected
                )
                scanWorlds(context, newSelected)
            } else {
                scanWorlds(context, current.selectedPackage)
            }
        }
    }

    fun selectPackage(context: Context, packageName: String) {
        val current = _uiState.value as? UiState.Success ?: return
        _uiState.value = current.copy(selectedPackage = packageName)
        scanWorlds(context, packageName)
    }

    /**
     * Lets user write custom packages (e.g., custom versions, mcpelauncher, test/trial folders, etc.)
     */
    fun addCustomPackage(context: Context, packageName: String) {
        val current = _uiState.value as? UiState.Success ?: return
        if (packageName.isBlank()) return
        val cleaned = packageName.trim()
        val list = current.packages.toMutableList()
        if (!list.contains(cleaned)) {
            list.add(cleaned)
        }
        _uiState.value = current.copy(
            packages = list,
            selectedPackage = cleaned
        )
        scanWorlds(context, cleaned)
    }

    fun scanWorlds(context: Context, packageName: String) {
        val current = _uiState.value as? UiState.Success ?: return
        _uiState.value = current.copy(isScanning = true, error = null)

        viewModelScope.launch {
            // Update diagnostics asynchronously too
            val isAvailableNow = ShizukuHelper.isAvailable()
            val isPermittedNow = ShizukuHelper.hasPermission(context)

            try {
                // Fetch Minecraft worlds and resolved path (this executes Shizuku shell cp & read operations)
                val (worlds, path) = withContext(Dispatchers.IO) {
                    val resolved = MinecraftWorldFinder.findWorldsPath(packageName)
                    val list = MinecraftWorldFinder.fetchWorlds(context, packageName)
                    Pair(list, resolved)
                }
                
                Log.d("MainViewModel", "Scanned worlds count: ${worlds.size}, resolved path: $path")
                
                val updated = _uiState.value as? UiState.Success
                if (updated != null) {
                    _uiState.value = updated.copy(
                        worlds = worlds,
                        resolvedPath = path,
                        isScanning = false,
                        shizukuAvailable = isAvailableNow,
                        shizukuPermission = isPermittedNow,
                        error = if (worlds.isEmpty()) {
                            "No worlds found or folder does not exist. Make sure you have worlds created under 'External' storage settings inside Minecraft and target package matches."
                        } else null
                    )
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Scan error", e)
                val updated = _uiState.value as? UiState.Success
                if (updated != null) {
                    _uiState.value = updated.copy(
                        worlds = emptyList(),
                        isScanning = false,
                        shizukuAvailable = isAvailableNow,
                        shizukuPermission = isPermittedNow,
                        error = "Error during scan: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun requestPermission() {
        ShizukuHelper.requestPermission(1001)
    }

    fun exportWorld(context: Context, world: MinecraftWorld) {
        viewModelScope.launch {
            _exportStatus.value = if (_isRussian.value) "Начало сборки..." else "Starting assembly..."
            try {
                val file = withContext(Dispatchers.IO) {
                    MinecraftWorldFinder.exportWorld(context, world) { progress ->
                        _exportStatus.value = progress
                    }
                }
                
                if (file != null && file.exists()) {
                    _exportedFile.value = file
                    _showToastMsg.value = if (_isRussian.value) {
                        "Мир '${world.displayName}' успешно экспортирован в файл .mcworld!"
                    } else {
                        "Success! World '${world.displayName}' is exported as a ZIP-compatible Minecraft world (.mcworld)!"
                    }
                } else {
                    _showToastMsg.value = if (_isRussian.value) {
                        "Не удалось скопировать или запаковать файлы мира. Проверьте права Shizuku."
                    } else {
                        "Failed to copy or package world files. Please check Shizuku authorizations."
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Export error", e)
                _showToastMsg.value = if (_isRussian.value) {
                    "Ошибка экспорта: ${e.localizedMessage}"
                } else {
                    "Export error: ${e.localizedMessage}"
                }
            } finally {
                _exportStatus.value = null
            }
        }
    }

    fun clearExportedFile() {
        _exportedFile.value = null
    }

    fun clearToastMsg() {
        _showToastMsg.value = null
    }
}
