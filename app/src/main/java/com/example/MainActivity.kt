package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.*
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Reaction listeners for Shizuku binder state changes
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d("MCExporter", "Shizuku Binder Received successfully via listener")
        viewModel.updateDiagnostics(this)
        // Auto trigger scan on connect
        val state = viewModel.uiState.value as? UiState.Success
        if (state != null) {
            viewModel.scanWorlds(this, state.selectedPackage)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d("MCExporter", "Shizuku Binder reported Dead")
        viewModel.updateDiagnostics(this)
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        Log.d("MCExporter", "Shizuku Permission result received")
        viewModel.updateDiagnostics(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (e: Exception) {
            Log.e("MCExporter", "Failed to add Shizuku lifecycle listeners", e)
        }

        // Initialize diagnosis values and run initial check/scan
        viewModel.updateDiagnostics(this)
        val defaultPkg = (viewModel.uiState.value as UiState.Success).selectedPackage
        viewModel.scanWorlds(this, defaultPkg)

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bg_overlay),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart),
                        contentScale = ContentScale.FillWidth
                    )

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        topBar = { MainTopAppBar(viewModel) }
                    ) { innerPadding ->
                        MainScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateDiagnostics(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (e: Exception) {
            // Ignore
        }
    }
}

val IconArrowDownward: ImageVector
    get() = ImageVector.Builder(
        name = "ArrowDownward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 4f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(12f, 19f)
            lineTo(19f, 12f)
        }
    }.build()

val IconDownloadSymbol: ImageVector
    get() = ImageVector.Builder(
        name = "DownloadSymbol",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            // Arrow shaft
            moveTo(12f, 3f)
            lineTo(12f, 13f)
            // Arrow head
            moveTo(8f, 9f)
            lineTo(12f, 13f)
            lineTo(16f, 9f)
            // Tray/Bracket
            moveTo(5f, 16f)
            lineTo(5f, 19f)
            lineTo(19f, 19f)
            lineTo(19f, 16f)
        }
    }.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isRussian by viewModel.isRussian.collectAsState()
    val state = uiState as? UiState.Success

    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(
                onClick = {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.ananas.pinelauncher")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            Toast.makeText(context, if (isRussian) "Приложение PineLauncher не найдено" else "PineLauncher app not found", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            ) {
                SpriteIcon(
                    spriteRes = R.drawable.icons,
                    indexX = 1,
                    indexY = 3,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        title = {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.07f)
                ),
                border = BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRussian) "Мои миры" else "My Worlds",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                if (state != null) {
                    val isShizukuOk = state.shizukuAvailable && state.shizukuPermission
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.07f),
                        border = BorderStroke(
                            1.dp, 
                            if (isShizukuOk) EmeraldLight.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (!state.shizukuAvailable) {
                                    Toast.makeText(
                                        context, 
                                        if (isRussian) "Служба Shizuku сообщает об отключении." else "Shizuku Service is reporting disconnected.", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (!state.shizukuPermission) {
                                    viewModel.requestPermission()
                                } else {
                                    Toast.makeText(
                                        context, 
                                        if (isRussian) "Shizuku активен и авторизован!" else "Shizuku is active and authorized!", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isShizukuOk) EmeraldLight else ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isShizukuOk) {
                                    if (isRussian) "АКТИВЕН" else "ACTIVE"
                                } else {
                                    if (isRussian) "ОШИБКА" else "ERROR"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = if (isShizukuOk) EmeraldLight else ErrorRed
                                )
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { viewModel.toggleLanguage() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "RU",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (isRussian) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SpriteIcon(
                            spriteRes = R.drawable.icons,
                            indexX = 1,
                            indexY = 1,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "EN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = if (!isRussian) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

const val CustomWidgetLength = 0

@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val exportStatus by viewModel.exportStatus.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()
    val toastMessage by viewModel.showToastMsg.collectAsState()

    val state = uiState as? UiState.Success ?: return

    val isRussian by viewModel.isRussian.collectAsState()

    var showCustomPackageDialog by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null && exportedFile != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    exportedFile?.inputStream()?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(
                    context,
                    if (isRussian) "Файл успешно сохранен!" else "File saved successfully!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to save file", e)
                Toast.makeText(
                    context,
                    "${if (isRussian) "Не удалось сохранить файл" else "Failed to save file"}: ${e.localizedMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Toast watcher
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToastMsg()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Status warning banner (Gentle diagnostics only, NO screen locking!)
            AnimatedVisibility(
                visible = !(state.shizukuAvailable && state.shizukuPermission),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.07f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpriteIcon(
                                spriteRes = R.drawable.icons,
                                indexX = 2,
                                indexY = 1,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRussian) "Как настроить / Помощь" else "Troubleshooting Guide",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRussian) {
                                "Даже если Shizuku указан неактивным, утилита пытается отправить запросы. Запустите Shizuku на девайсе вручную и разрешите доступ."
                            } else {
                                "Even if Shizuku reports inactive, we will attempt scans. Launch the Shizuku app manually and grant permission for this tool to operate."
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!state.shizukuAvailable) {
                                Button(
                                    onClick = {
                                        // Open Shizuku application directly
                                        try {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                            if (launchIntent != null) {
                                                context.startActivity(launchIntent)
                                            } else {
                                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                                                context.startActivity(webIntent)
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context, 
                                                if (isRussian) "Не удалось открыть Shizuku. Перейдите вручную." else "Could not open Shizuku. Navigate manually.", 
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldLight,
                                        contentColor = EmeraldDark
                                    ),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = if (isRussian) "Открыть Shizuku" else "Open Shizuku App", 
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.requestPermission() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.07f),
                                    contentColor = EmeraldLight
                                ),
                                border = BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (isRussian) "Разрешить доступ" else "Grant Permission", 
                                    color = EmeraldLight,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Folder details & Package selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = if (isRussian) "Целевая папка игры" else "Target Game Folder",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = EmeraldLight
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick chips (HORIZONTALLY SCROLLABLE to support many items!)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        state.packages.forEach { pkg ->
                            val isSelected = state.selectedPackage == pkg
                            val labelRes = if (pkg == "com.mojang.minecraftpe") {
                                "Minecraft"
                            } else if (pkg == "com.mojang.minecrafttrialpe") {
                                "Minecraft Trial"
                            } else {
                                pkg.substringAfterLast(".")
                            }
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectPackage(context, pkg) },
                                label = { 
                                    Text(
                                        text = labelRes, 
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    ) 
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldLight.copy(alpha = 0.15f),
                                    selectedLabelColor = EmeraldLight,
                                    containerColor = Color.White.copy(alpha = 0.04f),
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    selectedBorderColor = EmeraldLight.copy(alpha = 0.5f),
                                    borderColor = Color.White.copy(alpha = 0.15f),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                )
                            )
                        }
                    }
 
                    // Add custom pack button
                    IconButton(
                        onClick = { showCustomPackageDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .size(42.dp)
                    ) {
                        SpriteIcon(
                            spriteRes = R.drawable.icons,
                            indexX = 0,
                            indexY = 1,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
 
                Spacer(modifier = Modifier.height(14.dp))
 
                // Display selected path log
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpriteIcon(
                                spriteRes = R.drawable.icons,
                                indexX = 0,
                                indexY = 0,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRussian) "Активный пакет: ${state.selectedPackage}" else "Active Package: ${state.selectedPackage}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val displayPath = if (state.resolvedPath.isNotEmpty()) {
                            state.resolvedPath
                        } else {
                            "/storage/emulated/0/Android/data/${state.selectedPackage}/files/games/com.mojang/minecraftWorlds"
                        }
                        Text(
                            text = if (isRussian) "Путь: $displayPath" else "Path: $displayPath",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
 
                Spacer(modifier = Modifier.height(20.dp))
 
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRussian) "Миры (${state.worlds.size})" else "Worlds (${state.worlds.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White
                        )
                    )
 
                    Button(
                        onClick = { viewModel.scanWorlds(context, state.selectedPackage) },
                        enabled = !state.isScanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldLight,
                            contentColor = EmeraldDark
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SpriteIcon(
                            spriteRes = R.drawable.icons,
                            indexX = 2,
                            indexY = 0,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRussian) "ОБНОВИТЬ" else "SCAN", 
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // World Lists
            if (state.isScanning) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = EmeraldLight)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isRussian) "Запрос через активный Shizuku..." else "Querying via active Shizuku binder...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else if (state.worlds.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.worlds, key = { world -> world.directoryName + "_" + world.packageSource }) { world ->
                        WorldCard(world = world, isRussian = isRussian, onExport = { viewModel.exportWorld(context, world) })
                    }
                }
            } else {
                // Empty view or error view
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SpriteIcon(
                        spriteRes = R.drawable.icons,
                        indexX = 2,
                        indexY = 1,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isRussian) "Миры не обнаружены" else "No Worlds Detected",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRussian) {
                            "Миры не найдены. Убедитесь, что у вас есть миры, созданные во внешнем (External) хранилище Minecraft."
                        } else {
                            "No worlds found. Make sure your active Minecraft worlds are stored in External storage."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.scanWorlds(context, state.selectedPackage) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldLight,
                            contentColor = EmeraldDark
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isRussian) "ФОРСИРОВАТЬ СКАНИРОВАНИЕ" else "FORCE SCAN NOW", 
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Processing progress dialog
        exportStatus?.let { status ->
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = EmeraldLight)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (isRussian) "Экспорт мира Minecraft" else "Exporting Minecraft World",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRussian) {
                                when {
                                    status.startsWith("Connecting") -> "Установка соединения Shizuku..."
                                    status.startsWith("Counting files") -> "Подсчет файлов мира..."
                                    status.startsWith("In compression") -> {
                                        status.replace("In compression:", "Сжатие архива:")
                                              .replace("files", "файлов")
                                    }
                                    status.startsWith("Error copying") -> status.replace("Error copying files:", "Ошибка копирования файлов:")
                                    status.startsWith("Failed to copy") -> "Ошибка: файлы мира не скопированы"
                                    else -> status
                                }
                            } else {
                                status
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // World Export Success Share sheets Dialog
        exportedFile?.let { file ->
            Dialog(
                onDismissRequest = { viewModel.clearExportedFile() }
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = EmeraldDark.copy(alpha = 0.3f),
                            border = BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.3f)),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                SpriteIcon(
                                    spriteRes = R.drawable.icons,
                                    indexX = 1,
                                    indexY = 0,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isRussian) "Мир экспортирован!" else "World Exported!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal,
                                color = EmeraldLight
                            ),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRussian) "Имя файла: ${file.name}" else "Filename: ${file.name}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRussian) "Размер файла: ${formatSize(file.length())}" else "File Size: ${formatSize(file.length())}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                             ) {
                                // Save/Download button
                                Button(
                                    onClick = {
                                        try {
                                            createDocumentLauncher.launch(file.name)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "${if (isRussian) "Ошибка сохранения" else "Failed to save file"}: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldLight,
                                        contentColor = Color(0xFF081C0E)
                                    )
                                ) {
                                    SpriteIcon(
                                        spriteRes = R.drawable.icons,
                                        indexX = 3,
                                        indexY = 0,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isRussian) "Скачать" else "Save",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
 
                                // Share button
                                Button(
                                    onClick = {
                                        try {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                             )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                             }
                                            context.startActivity(Intent.createChooser(intent, if (isRussian) "Поделиться файлом .mcworld" else "Share .mcworld file"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "${if (isRussian) "Ошибка при отправке" else "Failed to share file"}: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        contentColor = EmeraldLight
                                    ),
                                    border = BorderStroke(1.dp, EmeraldLight.copy(alpha = 0.4f))
                                ) {
                                    SpriteIcon(
                                        spriteRes = R.drawable.icons,
                                        indexX = 3,
                                        indexY = 1,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isRussian) "Отправить" else "Share",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
 
                            // Close button
                            OutlinedButton(
                                onClick = { viewModel.clearExportedFile() },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(
                                    text = if (isRussian) "Закрыть" else "Close", 
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom package dialog
        if (showCustomPackageDialog) {
            var inputPackage by remember { mutableStateOf("") }
            
            Dialog(
                onDismissRequest = { showCustomPackageDialog = false }
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = if (isRussian) "Добавить имя пакета" else "Register Custom Package",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = EmeraldLight
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRussian) {
                                "Введите ID пакета для кастомных версий или клонов Minecraft:"
                            } else {
                                "Enter package ID for custom Minecraft variants or clones:"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = inputPackage,
                            onValueChange = { inputPackage = it },
                            placeholder = { 
                                Text(
                                    text = "e.g. com.mojang.minecraftpe.mod",
                                    fontSize = 12.sp
                                ) 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = EmeraldM3,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showCustomPackageDialog = false }) {
                                Text(
                                    text = if (isRussian) "Отмена" else "Cancel", 
                                    fontWeight = FontWeight.Normal,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (inputPackage.isNotBlank()) {
                                        viewModel.addCustomPackage(context, inputPackage)
                                        showCustomPackageDialog = false
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldLight,
                                    contentColor = EmeraldDark
                                )
                            ) {
                                Text(
                                    text = if (isRussian) "Добавить" else "Add & Scan",
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpriteIcon(
    spriteRes: Int,
    indexX: Int,
    indexY: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(spriteRes) {
        BitmapFactory.decodeResource(context.resources, spriteRes)
    }
    val icon = remember(bitmap, indexX, indexY) {
        if (bitmap != null) {
            Bitmap.createBitmap(
                bitmap,
                indexX * 9,
                indexY * 9,
                9,
                9
            )
        } else {
            null
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.None
        )
    }
}

@Composable
fun WorldCard(world: MinecraftWorld, isRussian: Boolean = false, onExport: () -> Unit) {
    val bitmapState = remember(world.localIconFile) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(world.localIconFile) {
        if (world.localIconFile != null && world.localIconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(world.localIconFile.absolutePath)
                bitmapState.value = bitmap
            } catch (e: Exception) {
                Log.e("WorldCard", "Failed to decode icon", e)
            }
        } else {
            bitmapState.value = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.07f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = bitmapState.value
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "World Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Minecraft chest pixel-art placeholder
                    SpriteIcon(
                        spriteRes = R.drawable.icons,
                        indexX = 3,
                        indexY = 0,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = world.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                
                Text(
                    text = world.directoryName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatSize(world.sizeBytes),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = EmeraldLight
                        )
                    )
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                    Text(
                        text = formatTimestamp(world.lastModified, isRussian),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Export (PineLauncher Styled neon button)
            IconButton(
                onClick = onExport,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(EmeraldLight)
                    .size(44.dp)
            ) {
                SpriteIcon(
                    spriteRes = R.drawable.icons,
                    indexX = 3,
                    indexY = 0,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

fun formatTimestamp(epoch: Long, isRussian: Boolean = false): String {
    return try {
        val date = Date(epoch)
        val locale = if (isRussian) Locale("ru") else Locale.US
        val sdf = SimpleDateFormat("HH:mm, dd MMM yy", locale)
        sdf.format(date)
    } catch (e: Exception) {
        if (isRussian) "Неизвестная дата" else "Unknown Date"
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) return "Large Size"
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
