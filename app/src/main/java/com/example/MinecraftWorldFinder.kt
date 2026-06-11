package com.example

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CommandResponse(val exitCode: Int, val stdout: String, val stderr: String)

object MinecraftWorldFinder {

    private const val TAG = "MinecraftWorldFinder"

    fun findInstalledPackages(context: Context): List<String> {
        val testPackages = listOf(
            "com.mojang.minecraftpe",
            "com.mojang.minecrafttrialpe"
        )
        // Since package visibility might be restricted, let's check what's installed
        // but always return the full list or let user select it to be safe
        val pm = context.packageManager
        val installed = ArrayList<String>()
        for (pkg in testPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                installed.add(pkg)
            } catch (e: Exception) {
                // Ignore, means package is not visible or installed
            }
        }
        // If nothing was found, default to com.mojang.minecraftpe to let users type/try
        if (installed.isEmpty()) {
            installed.add("com.mojang.minecraftpe")
        }
        return installed
    }

    /**
     * Executes a command via Shizuku shell.
     */
    fun runShizukuCommand(command: String): CommandResponse {
        return try {
            val process = ShizukuShellRunner.runCommand(arrayOf("sh", "-c", command))
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                stdout.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                stderr.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            CommandResponse(exitCode, stdout.toString().trim(), stderr.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "runShizukuCommand failed command: $command", e)
            CommandResponse(-1, "", e.localizedMessage ?: "Unknown exception")
        }
    }

    fun findWorldsPath(packageName: String): String {
        val rawPath = findWorldsPathRaw(packageName)
        return rawPath.replace("/sdcard/", "/storage/emulated/0/")
            .replace(Regex("^/sdcard$"), "/storage/emulated/0")
    }

    private fun findWorldsPathRaw(packageName: String): String {
        // Strip common prefixes from the packageName to get other search terms (e.g. com.mojang... -> mojang...)
        val packageCore = packageName.replace(Regex("^(com|org|net|ru|io|games)\\."), "")
        val packagesToTry = listOf(packageName, packageCore).distinct()
        
        val candidates = mutableListOf<String>()
        for (pkg in packagesToTry) {
            candidates.add("/storage/emulated/0/Android/data/$pkg/files/games/com.mojang/minecraftWorlds")
            candidates.add("/sdcard/Android/data/$pkg/files/games/com.mojang/minecraftWorlds")
            candidates.add("/storage/emulated/0/Android/data/$pkg/files/games/minecraftWorlds")
            candidates.add("/sdcard/Android/data/$pkg/files/games/minecraftWorlds")
            candidates.add("/storage/emulated/0/Android/data/$pkg/files/minecraftWorlds")
            candidates.add("/sdcard/Android/data/$pkg/files/minecraftWorlds")
            candidates.add("/storage/emulated/0/games/com.mojang/minecraftWorlds")
            candidates.add("/sdcard/games/com.mojang/minecraftWorlds")
        }
        
        for (path in candidates) {
            val probe = runShizukuCommand("ls -d \"$path\"")
            if (probe.exitCode == 0 && probe.stdout.trim().isNotEmpty()) {
                val found = probe.stdout.trim()
                Log.d(TAG, "Resolved worlds path using candidate probe: $found")
                return found
            }
        }
        
        // Dynamic search with 'find' command if candidates didn't match directly
        for (pkg in packagesToTry) {
            val findRes = runShizukuCommand("find \"/storage/emulated/0/Android/data/$pkg\" -type d -name \"minecraftWorlds\" 2>/dev/null")
            if (findRes.exitCode == 0 && findRes.stdout.trim().isNotEmpty()) {
                val found = findRes.stdout.trim().split("\n").firstOrNull()?.trim() ?: ""
                if (found.isNotEmpty()) {
                    Log.d(TAG, "Resolved worlds path using find under storage for $pkg: $found")
                    return found
                }
            }
            
            val findResSd = runShizukuCommand("find \"/sdcard/Android/data/$pkg\" -type d -name \"minecraftWorlds\" 2>/dev/null")
            if (findResSd.exitCode == 0 && findResSd.stdout.trim().isNotEmpty()) {
                val found = findResSd.stdout.trim().split("\n").firstOrNull()?.trim() ?: ""
                if (found.isNotEmpty()) {
                    Log.d(TAG, "Resolved worlds path using find under sdcard for $pkg: $found")
                    return found
                }
            }
        }

        // Real absolute fallback: listing Android/data to find anything containing parts of the packageName
        val listDataRes = runShizukuCommand("ls -1 \"/storage/emulated/0/Android/data\"")
        if (listDataRes.exitCode == 0 && listDataRes.stdout.isNotEmpty()) {
            val folders = listDataRes.stdout.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val matchTerm = packageCore.lowercase()
            val matchedFolder = folders.firstOrNull { it.lowercase().contains(matchTerm) || matchTerm.contains(it.lowercase()) }
            if (matchedFolder != null) {
                Log.d(TAG, "Matched similar Android/data folder: $matchedFolder for packageName: $packageName")
                val findMatchedRes = runShizukuCommand("find \"/storage/emulated/0/Android/data/$matchedFolder\" -type d -name \"minecraftWorlds\" 2>/dev/null")
                if (findMatchedRes.exitCode == 0 && findMatchedRes.stdout.trim().isNotEmpty()) {
                    val found = findMatchedRes.stdout.trim().split("\n").firstOrNull()?.trim() ?: ""
                    if (found.isNotEmpty()) {
                        Log.d(TAG, "Resolved worlds path using find under matched similar folder $matchedFolder: $found")
                        return found
                    }
                }
            }
        }

        // Default to standard path if nothing else is found
        return "/storage/emulated/0/Android/data/$packageName/files/games/com.mojang/minecraftWorlds"
    }

    fun fetchWorlds(context: Context, packageName: String): List<MinecraftWorld> {
        val worlds = mutableListOf<MinecraftWorld>()
        val mainPath = findWorldsPath(packageName)
        
        Log.d(TAG, "Fetching worlds from Shizuku for $packageName at $mainPath")
        
        // 1. List directory names
        val listRes = runShizukuCommand("ls -1 \"$mainPath\"")
        val rawStdout = listRes.stdout.trim()
        
        if (rawStdout.isEmpty() && listRes.exitCode != 0) {
            Log.e(TAG, "Failed to list directory: ${listRes.stderr}")
            if (listRes.stderr.contains("Permission denied", ignoreCase = true)) {
                throw SecurityException("Shizuku has no access to target package storage (Permission denied). Please make sure Shizuku has been granted full ADB permissions.")
            }
            return emptyList()
        }
        
        val directoryNames = rawStdout.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("Permission denied", ignoreCase = true) && !it.contains("No such file", ignoreCase = true) }
            
        Log.d(TAG, "Found directories: $directoryNames")
        
        for (dirName in directoryNames) {
            val dirPath = "$mainPath/$dirName"
            
            // 2. Fetch Display Name from levelname.txt
            val nameRes = runShizukuCommand("cat \"$dirPath/levelname.txt\"")
            val displayName = if (nameRes.exitCode == 0 && nameRes.stdout.isNotEmpty()) {
                nameRes.stdout.trim()
            } else {
                dirName
            }
            
            // 3. Copy world icon (png, jpeg, jpg etc.) to external app cache to allow Shizuku write access and prevent transfer corruption
            val extCache = context.externalCacheDir ?: File("/storage/emulated/0/Android/data/${context.packageName}/cache")
            var iconFile: File? = null
            val iconNames = listOf("world_icon.png", "world_icon.jpeg", "world_icon.jpg", "world_icon.PNG", "world_icon.JPEG", "world_icon.JPG")
            for (iconName in iconNames) {
                val iconCacheDir = File(extCache, "world_icons/$packageName/$dirName")
                iconCacheDir.mkdirs()
                val localIconFile = File(iconCacheDir, iconName)
                val cpRes = runShizukuCommand("cp \"$dirPath/$iconName\" \"${localIconFile.absolutePath}\"")
                if (cpRes.exitCode == 0 && localIconFile.exists() && localIconFile.length() > 0) {
                    iconFile = localIconFile
                    break
                }
            }
            
            // 4. Fetch Size
            val sizeRes = runShizukuCommand("du -sk \"$dirPath\"")
            var sizeBytes: Long = 0
            if (sizeRes.exitCode == 0 && sizeRes.stdout.isNotEmpty()) {
                try {
                    val sizeKb = sizeRes.stdout.split(Regex("\\s+"))[0].toLong()
                    sizeBytes = sizeKb * 1024
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse size from du: ${sizeRes.stdout}")
                }
            }
            
            // 5. Fetch modified date from levelname.txt
            val dateRes = runShizukuCommand("date +%s -r \"$dirPath/levelname.txt\"")
            var lastModified = System.currentTimeMillis()
            if (dateRes.exitCode == 0 && dateRes.stdout.isNotEmpty()) {
                try {
                    val secs = dateRes.stdout.trim().toLong()
                    lastModified = secs * 1000
                } catch (e: Exception) {
                    // Try setting from levelname directory directly
                    val dateDirRes = runShizukuCommand("date +%s -r \"$dirPath\"")
                    if (dateDirRes.exitCode == 0 && dateDirRes.stdout.isNotEmpty()) {
                        try {
                            val secs = dateDirRes.stdout.trim().toLong()
                            lastModified = secs * 1000
                        } catch (e: Exception) {
                            // Default unchanged
                        }
                    }
                }
            }
            
            worlds.add(
                MinecraftWorld(
                    directoryName = dirName,
                    displayName = displayName,
                    localIconFile = iconFile,
                    sizeBytes = sizeBytes,
                    packageSource = packageName,
                    lastModified = lastModified
                )
            )
        }
        
        return worlds.sortedByDescending { it.lastModified }
    }

    /**
     * Recursively zips files.
     */
    private fun zipFolderContents(srcFolder: File, relativeTo: File, zos: ZipOutputStream, progressCallback: (Int, Int, String) -> Unit, totalCount: Int, currentIndex: IntArray) {
        val files = srcFolder.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipFolderContents(file, relativeTo, zos, progressCallback, totalCount, currentIndex)
            } else {
                currentIndex[0]++
                val entryPath = file.relativeTo(relativeTo).path.replace('\\', '/')
                progressCallback(currentIndex[0], totalCount, entryPath)
                
                try {
                    val entry = ZipEntry(entryPath)
                    zos.putNextEntry(entry)
                    val fis = FileInputStream(file)
                    val buffer = ByteArray(4096)
                    var count: Int
                    while (fis.read(buffer).also { count = it } != -1) {
                        zos.write(buffer, 0, count)
                    }
                    zos.closeEntry()
                    fis.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to zip file: ${file.absolutePath}", e)
                }
            }
        }
    }

    private fun countFiles(file: File): Int {
        if (!file.exists()) return 0
        if (!file.isDirectory) return 1
        var count = 0
        val contents = file.listFiles() ?: return 0
        for (child in contents) {
            count += if (child.isDirectory) countFiles(child) else 1
        }
        return count
    }

    fun exportWorld(context: Context, world: MinecraftWorld, onProgress: (String) -> Unit): File? {
        val mainPath = "${findWorldsPath(world.packageSource)}/${world.directoryName}"
        
        // Prepare external app folder for temporary assembly (so Shizuku/ADB shell has permission to write)
        val extCache = context.externalCacheDir ?: File("/storage/emulated/0/Android/data/${context.packageName}/cache")
        val tempExportDir = File(extCache, "temp_export/${world.directoryName}")
        tempExportDir.deleteRecursively()
        tempExportDir.mkdirs()
        
        onProgress("Connecting via Shizuku...")
        Log.d(TAG, "Copying $mainPath files to ${tempExportDir.absolutePath} via Shizuku")
        
        val cpRes = runShizukuCommand("cp -r \"$mainPath/.\" \"${tempExportDir.absolutePath}\"")
        val copiedFiles = tempExportDir.listFiles()
        if (cpRes.exitCode != 0 && (copiedFiles == null || copiedFiles.isEmpty())) {
            onProgress("Error copying files: ${cpRes.stderr}")
            Log.e(TAG, "Shizuku copy failed with error: ${cpRes.stderr} (and no files were copied)")
            return null
        }
        
        if (!tempExportDir.exists() || tempExportDir.listFiles()?.isEmpty() == true) {
            onProgress("Failed to copy files (Empty world directory).")
            Log.e(TAG, "Temporary copy directory is empty or missing")
            return null
        }
        
        onProgress("Counting files in package...")
        val totalFiles = countFiles(tempExportDir)
        Log.d(TAG, "Total files to compress: $totalFiles")
        
        val exportDir = File(context.cacheDir, "export")
        exportDir.mkdirs()
        
        // Sanitize name for file system
        val sanitizedName = world.displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outputFile = File(exportDir, "$sanitizedName.mcworld")
        if (outputFile.exists()) {
            outputFile.delete()
        }
        
        try {
            val fos = FileOutputStream(outputFile)
            val zos = ZipOutputStream(fos)
            zos.setLevel(1) // Fast compression to save time
            
            val currentIndex = intArrayOf(0)
            zipFolderContents(tempExportDir, tempExportDir, zos, { current, total, name ->
                onProgress("In compression: $current / $total files\n$name")
            }, totalFiles, currentIndex)
            
            zos.close()
            fos.close()
            
            Log.d(TAG, "Zipped successfully: ${outputFile.absolutePath} [${outputFile.length()} bytes]")
            
            // Cleanup temp export directory
            tempExportDir.deleteRecursively()
            return outputFile
        } catch (e: Exception) {
            onProgress("Compression error: ${e.localizedMessage}")
            Log.e(TAG, "Failed compression on ${outputFile.name}", e)
            tempExportDir.deleteRecursively()
            return null
        }
    }


}
