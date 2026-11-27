package com.sonylut.studio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext

private const val PREFS = "sony_lut_studio_v06"
private const val KEY_READ_TREE = "read_tree"
private const val KEY_OUTPUT_TREE = "output_tree"
private const val KEY_LAST_LUT = "last_lut"
private const val KEY_INTENSITY = "intensity"
private const val KEY_LAST_CATEGORY = "last_category"
private const val KEY_ENTRIES = "lut_entries"
private const val KEY_PRESETS = "presets"
private const val KEY_CATEGORIES = "custom_categories"
private const val KEY_EXPORT_HISTORY = "export_history"
private const val KEY_EXPORT_FORMAT = "export_format"
private const val KEY_JPEG_QUALITY = "jpeg_quality"

private val BUILT_INS = listOf(
    "CV-Arri-IceBlue.cube",
    "CV-Arri-Jamaica.cube",
    "CV-Arri-Neutral.cube",
    "CV-Arri-Tungsten.cube",
    "CV-Arri-Utopia.cube"
)
private val SYSTEM_CATEGORIES = listOf("全部", "内置 LUT", "我的 LUT", "已收藏")

private data class LutEntry(
    val id: String,
    val name: String,
    val fileName: String,
    val builtInAsset: String? = null,
    val favorite: Boolean = false,
    val categories: List<String> = emptyList()
) {
    val isBuiltIn get() = builtInAsset != null
}

private data class ColorPreset(
    val id: String,
    val name: String,
    val lutId: String,
    val intensity: Float,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

private data class PhotoRef(val uri: String, val name: String)
private data class ExportRecord(val uri: String, val name: String, val time: Long)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { StudioScreen() } }
    }
}

@Composable
private fun StudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val lutDir = remember { File(context.filesDir, "luts").apply { mkdirs() } }

    var entries by remember { mutableStateOf(loadEntries(prefs, context)) }
    var presets by remember { mutableStateOf(loadPresets(prefs)) }
    var customCategories by remember { mutableStateOf(loadCategories(prefs)) }
    var exportHistory by remember { mutableStateOf(loadExportHistory(prefs)) }
    var readTreeUri by remember { mutableStateOf(prefs.getString(KEY_READ_TREE, null)) }
    var outputTreeUri by remember { mutableStateOf(prefs.getString(KEY_OUTPUT_TREE, null)) }
    var lastCategory by remember { mutableStateOf(prefs.getString(KEY_LAST_CATEGORY, "全部") ?: "全部") }
    var selectedLutId by remember { mutableStateOf(prefs.getString(KEY_LAST_LUT, null)) }
    var lutInfo by remember { mutableStateOf<CubeLutInfo?>(null) }
    var lutFileName by remember { mutableStateOf("尚未选择 LUT") }
    var photoFileName by remember { mutableStateOf("尚未导入照片") }
    var sourcePhotoFile by remember { mutableStateOf<File?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var intensity by remember { mutableFloatStateOf(prefs.getFloat(KEY_INTENSITY, 100f).coerceIn(0f, 200f)) }
    var exportFormat by remember { mutableStateOf(prefs.getString(KEY_EXPORT_FORMAT, "png16") ?: "png16") }
    var jpegQuality by remember { mutableIntStateOf(prefs.getInt(KEY_JPEG_QUALITY, 98).coerceIn(95, 100)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("请先在设置中指定照片读取目录和输出目录。") }
    var comparingOriginal by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPhotoBrowser by remember { mutableStateOf(false) }
    var showLutBrowser by remember { mutableStateOf(false) }
    var showPresetManager by remember { mutableStateOf(false) }
    var showSavePreset by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf<List<PhotoRef>>(emptyList()) }
    var processingJob by remember { mutableStateOf<Job?>(null) }

    fun persistEntries() { saveEntries(prefs, entries) }
    fun persistPresets() { savePresets(prefs, presets) }
    fun persistCategories() { saveCategories(prefs, customCategories) }

    val readFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                readTreeUri = uri.toString()
                prefs.edit().putString(KEY_READ_TREE, readTreeUri).apply()
                status = "读取目录授权成功。"
                error = false
            } catch (e: Exception) {
                status = "无法保存读取目录授权：${e.message}"
                error = true
            }
        }
    }
    val outputFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                outputTreeUri = uri.toString()
                prefs.edit().putString(KEY_OUTPUT_TREE, outputTreeUri).apply()
                status = "输出目录授权成功。"
                error = false
            } catch (e: Exception) {
                status = "无法保存输出目录授权：${e.message}"
                error = true
            }
        }
    }
    val lutImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            try {
                val imported = withContext(Dispatchers.IO) {
                    val name = queryDisplayName(context, uri).ifBlank { "Imported.cube" }
                    val targetName = uniqueFileName(lutDir, name.substringAfterLast('/').let {
                        if (it.endsWith(".cube", true)) it else "$it.cube"
                    })
                    val target = File(lutDir, targetName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().buffered().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("无法读取所选 LUT。")
                    val parsed = try {
                        target.inputStream().use { CubeLutParser.parse(it) }
                    } catch (e: Exception) {
                        target.delete()
                        throw e
                    }
                    Pair(target, parsed)
                }
                val target = imported.first
                val parsed = imported.second
                val entry = LutEntry(
                    id = "custom_${System.currentTimeMillis()}_${target.name.hashCode()}",
                    name = parsed.title.ifBlank { target.nameWithoutExtension },
                    fileName = target.name
                )
                entries = entries + entry
                persistEntries()
                selectedLutId = entry.id
                lutInfo = parsed
                lutFileName = entry.name
                processedBitmap = null
                prefs.edit().putString(KEY_LAST_LUT, entry.id).apply()
                status = "已导入 LUT：${entry.name}"
                error = false
            } catch (e: Exception) {
                status = e.message ?: "LUT 导入失败。"
                error = true
            } finally { loading = false }
        }
    }

    suspend fun loadPhoto(photo: PhotoRef) {
        loading = true
        error = false
        processedBitmap?.recycle()
        processedBitmap = null
        status = "正在读取照片并校正 EXIF 方向…"
        try {
            val cached = withContext(Dispatchers.IO) {
                val file = File.createTempFile("sony_lut_source_", ".img", context.cacheDir)
                context.contentResolver.openInputStream(Uri.parse(photo.uri))?.use { input ->
                    file.outputStream().buffered().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取照片文件。")
                if (file.length() <= 0L) throw IllegalStateException("照片文件为空。")
                file
            }
            val decoded = withContext(Dispatchers.IO) { decodePreviewBitmap(cached) }
            sourcePhotoFile?.delete()
            originalBitmap?.recycle()
            sourcePhotoFile = cached
            originalBitmap = decoded
            photoFileName = photo.name
            status = "照片已载入 · EXIF 方向已校正 · 预览最长边不超过 2048 px"
        } catch (e: Exception) {
            status = e.message ?: "照片读取失败。"
            error = true
        } finally { loading = false }
    }

    fun applyLut() {
        val source = originalBitmap
        val lut = lutInfo
        if (source == null || lut == null || loading) return
        processingJob?.cancel()
        processingJob = scope.launch {
            loading = true
            error = false
            status = "正在进行四面体插值…"
            try {
                val result = withContext(Dispatchers.Default) {
                    val job = coroutineContext[Job]
                    TetrahedralLutProcessor.apply(source, lut, intensity / 100f) { row, total ->
                        if (row % 32 == 0 || row == total) job?.ensureActive()
                    }
                }
                processedBitmap?.recycle()
                processedBitmap = result
                status = "处理完成 · LUT 强度 ${intensity.toInt()}% · ${result.width} × ${result.height}"
                prefs.edit().putFloat(KEY_INTENSITY, intensity).apply()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "LUT 处理失败。"
                error = true
            } finally { loading = false }
        }
    }

    fun selectLut(entry: LutEntry) {
        if (loading) return
        scope.launch {
            loading = true
            try {
                val info = withContext(Dispatchers.IO) {
                    if (entry.builtInAsset != null) {
                        context.assets.open("luts/${entry.builtInAsset}").use { CubeLutParser.parse(it) }
                    } else {
                        val file = File(lutDir, entry.fileName)
                        if (!file.exists()) throw IllegalStateException("找不到 LUT 文件：${entry.fileName}")
                        file.inputStream().use { CubeLutParser.parse(it) }
                    }
                }
                selectedLutId = entry.id
                lutInfo = info
                lutFileName = entry.name
                processedBitmap?.recycle()
                processedBitmap = null
                prefs.edit().putString(KEY_LAST_LUT, entry.id).apply()
                status = "已选择 LUT：${entry.name}"
                if (originalBitmap != null) {
                    loading = false
                    applyLut()
                }
            } catch (e: Exception) {
                status = e.message ?: "LUT 载入失败。"
                error = true
            } finally { loading = false }
        }
    }

    fun exportCurrent() {
        val sourceFile = sourcePhotoFile
        val lut = lutInfo
        val outputUri = outputTreeUri?.let(Uri::parse)
        if (sourceFile == null || lut == null) {
            status = "请先导入照片并选择 LUT。"
            error = true
            return
        }
        if (outputUri == null) {
            status = "尚未设置输出文件夹，请先进入设置指定目录。"
            error = true
            showSettings = true
            return
        }
        scope.launch {
            loading = true
            error = false
            var full: Bitmap? = null
            var result: Bitmap? = null
            var temporary: DocumentFile? = null
            try {
                val folder = DocumentFile.fromTreeUri(context, outputUri)
                    ?: throw IllegalStateException("输出目录授权已失效，请重新设置。")
                if (!folder.canWrite()) throw IllegalStateException("输出目录不可写，请重新授权。")
                val format = exportFormat
                val quality = jpegQuality
                val extension = if (format == "png16") "png" else "jpg"
                val mimeType = if (format == "png16") "image/png" else "image/jpeg"
                status = "正在解码原始照片（sRGB 工作空间）…"
                full = withContext(Dispatchers.IO) { decodeFullResolutionBitmap(sourceFile) }
                val activeLut = lut
                val activeIntensity = intensity / 100f
                val base = photoFileName.substringBeforeLast('.', photoFileName).ifBlank { "SonyLUTStudio" }
                val lutSlug = sanitizeName(lutFileName.substringBeforeLast('.').substringAfter("·").trim().ifBlank { "LUT" })
                val finalName = uniqueDocumentName(folder, "${sanitizeName(base)}_${lutSlug}", extension)
                val tempName = ".sony_lut_${System.currentTimeMillis()}.partial"
                temporary = folder.createFile(mimeType, tempName)
                    ?: throw IllegalStateException("无法在输出目录创建临时文件。")
                if (format == "png16") {
                    status = "正在执行 Float32 LUT 处理并写入 16-bit PNG…"
                    withContext(Dispatchers.Default) {
                        val job = coroutineContext[Job]
                        context.contentResolver.openOutputStream(temporary!!.uri, "w")?.use { out ->
                            TetrahedralLutProcessor.writePng16(full!!, activeLut, activeIntensity, out) { row, total ->
                                if (row % 32 == 0 || row == total) job?.ensureActive()
                            }
                            out.flush()
                        } ?: throw IllegalStateException("无法写入 PNG 文件。")
                    }
                } else {
                    status = "正在执行 Float32 LUT 处理…"
                    result = withContext(Dispatchers.Default) {
                        val job = coroutineContext[Job]
                        TetrahedralLutProcessor.apply(full!!, activeLut, activeIntensity) { row, total ->
                            if (row % 32 == 0 || row == total) job?.ensureActive()
                        }
                    }
                    val bitmap = result ?: throw IllegalStateException("处理结果为空。")
                    status = "正在写入 JPEG（质量 $quality）…"
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(temporary!!.uri, "w")?.use { out ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                                throw IllegalStateException("JPEG 编码失败。")
                            }
                            out.flush()
                        } ?: throw IllegalStateException("无法写入输出文件。")
                    }
                }
                var exportedUri: Uri? = null
                if (!temporary!!.renameTo(finalName)) {
                    val finalFile = folder.createFile(mimeType, finalName)
                        ?: throw IllegalStateException("无法创建最终照片文件。")
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(temporary!!.uri)?.use { input ->
                            context.contentResolver.openOutputStream(finalFile.uri, "w")?.use { output ->
                                input.copyTo(output)
                            } ?: throw IllegalStateException("无法写入最终照片。")
                        } ?: throw IllegalStateException("无法读取临时导出文件。")
                    }
                    temporary!!.delete()
                    temporary = null
                    exportedUri = finalFile.uri
                } else {
                    temporary = null
                    exportedUri = folder.findFile(finalName)?.uri
                }
                if (exportedUri != null) {
                    exportHistory = (listOf(ExportRecord(exportedUri.toString(), finalName, System.currentTimeMillis())) + exportHistory)
                        .distinctBy { it.uri }.take(10)
                    saveExportHistory(prefs, exportHistory)
                }
                status = "导出完成：$finalName · ${full!!.width} × ${full!!.height} px · ${if (format == "png16") "16-bit PNG 无损" else "JPEG 质量 $quality"}"
            } catch (e: Exception) {
                status = e.message ?: "导出失败。"
                error = true
            } finally {
                temporary?.delete()
                full?.recycle()
                result?.recycle()
                loading = false
            }
        }
    }

    LaunchedEffect(entries, selectedLutId) {
        val id = selectedLutId
        if (id != null && lutInfo == null) {
            entries.firstOrNull { it.id == id }?.let { entry ->
                try {
                    val info = withContext(Dispatchers.IO) {
                        if (entry.builtInAsset != null) context.assets.open("luts/${entry.builtInAsset}").use { CubeLutParser.parse(it) }
                        else File(lutDir, entry.fileName).inputStream().use { CubeLutParser.parse(it) }
                    }
                    lutInfo = info
                    lutFileName = entry.name
                } catch (_: Exception) {
                    selectedLutId = null
                    prefs.edit().remove(KEY_LAST_LUT).apply()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(
        Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF1B2B43), Color(0xFF263B59), Color(0xFF111827)))
    )) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF07142B), modifier = Modifier.size(54.dp)) {
                    ComposeImage(
                        painter = painterResource(id = com.sonylut.studio.R.drawable.app_logo),
                        contentDescription = "Sony LUT Studio Logo",
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(Modifier.weight(1f).padding(start = 13.dp)) {
                    Text("Sony LUT Studio", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("V0.7 · High Fidelity Color Engine", color = Color(0xFFB6C5D8), fontSize = 12.sp)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Rounded.Settings, "设置", tint = Color(0xFFB9D9FF))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("让色彩，回到你的掌控。", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("在一个工作台中完成照片选择、LUT 调色与导出。", color = Color(0xFFB6C5D8), fontSize = 13.sp)
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, null, tint = Accent)
                    Text("照片", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                    Spacer(Modifier.weight(1f))
                    if (queue.isNotEmpty()) Text("待处理 ${queue.size} 张", color = Accent, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(photoFileName, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                originalBitmap?.let { Text("${it.width} × ${it.height} px（预览）", color = Muted, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                GlassButton("浏览照片", Icons.Rounded.FolderOpen, enabled = !loading && readTreeUri != null) {
                    showPhotoBrowser = true
                }
                if (readTreeUri == null) {
                    Text("请先在设置中指定照片读取文件夹。", color = Color(0xFFFFC38B), fontSize = 12.sp)
                }
                if (queue.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("待处理列表", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    queue.forEachIndexed { index, photo ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}. ${photo.name}", color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1)
                            TextButton(onClick = {
                                val next = queue[index]
                                scope.launch {
                                    loadPhoto(next)
                                    if (!error) {
                                        queue = queue.filterNot { it.uri == next.uri }
                                        if (lutInfo != null) applyLut()
                                    }
                                }
                            }) { Text("加载") }
                            IconButton(onClick = { queue = queue.filterIndexed { i, _ -> i != index } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Close, null, tint = Muted)
                            }
                        }
                    }
                }
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Tune, null, tint = Accent)
                    Text("LUT 调色", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                }
                Spacer(Modifier.height(9.dp))
                Text(lutFileName, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                lutInfo?.let { Text("${it.title} · ${it.size}³ · ${it.dataCount} 组 RGB", color = Muted, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                GlassButton("打开 LUT 浏览器", Icons.Rounded.Palette, enabled = !loading) { showLutBrowser = true }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showPresetManager = true },
                    enabled = selectedLutId != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                ) { Icon(Icons.Rounded.Bookmarks, null); Spacer(Modifier.width(8.dp)); Text("我的调色预设（${presets.size}）") }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LUT 强度", color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("${intensity.toInt()}%", color = Accent, fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    onValueChangeFinished = { prefs.edit().putFloat(KEY_INTENSITY, intensity).apply(); if (lutInfo != null && originalBitmap != null) applyLut() },
                    valueRange = 0f..200f,
                    enabled = !loading && lutInfo != null,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color.White.copy(alpha = .18f))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("原图 0%", color = Muted, fontSize = 11.sp)
                    Text("增强效果 200%", color = Muted, fontSize = 11.sp)
                }
                Button(
                    onClick = { applyLut() },
                    enabled = !loading && originalBitmap != null && lutInfo != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF102039)),
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Rounded.CheckCircle, null); Text("  应用当前强度", fontWeight = FontWeight.SemiBold) }
            }

            originalBitmap?.let { original ->
                GlassPanel {
                    Text("照片预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(if (processedBitmap == null) "等待生成 LUT 效果" else "按住照片可查看原图，松开恢复效果图", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    val preview = if (comparingOriginal || processedBitmap == null) original else processedBitmap!!
                    Box(Modifier.fillMaxWidth().pointerInput(processedBitmap, original) {
                        detectTapGestures(onPress = {
                            comparingOriginal = true
                            try { tryAwaitRelease() } finally { comparingOriginal = false }
                        })
                    }) {
                        PreviewImage(preview)
                        Surface(Modifier.align(Alignment.TopStart).padding(10.dp), color = Color(0xCC101827), shape = RoundedCornerShape(10.dp)) {
                            Text(if (comparingOriginal || processedBitmap == null) "原图" else "LUT 效果", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { exportCurrent() },
                        enabled = !loading && processedBitmap != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF102039)),
                        shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Rounded.SaveAlt, null); Text("  一键导出到指定文件夹", fontWeight = FontWeight.SemiBold) }
                    Text(if (exportFormat == "png16") "原始分辨率 · Float32 处理 · 16-bit PNG 无损导出。" else "原始分辨率 · Float32 处理 · JPEG 质量 $jpegQuality。", color = Muted, fontSize = 11.sp)
                }
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = Accent)
                    Text("处理状态", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                }
                Spacer(Modifier.height(9.dp))
                Text(status, color = if (error) Color(0xFFFFA6A6) else Color(0xFFB6C5D8), fontSize = 13.sp, lineHeight = 20.sp)
                exportHistory.firstOrNull()?.let { latest ->
                    TextButton(onClick = { openExport(context, latest) }) {
                        Icon(Icons.Rounded.OpenInNew, null, tint = Accent)
                        Text("  查看最近导出：${latest.name}", color = Accent, maxLines = 1)
                    }
                }
                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Color.White.copy(alpha = .12f))
                }
                Spacer(Modifier.height(8.dp))
                Text("V0.7：Float32 色彩处理、16-bit PNG 无损导出与 JPEG 质量控制；完整保留 V0.6 照片和 LUT 工作流。", color = Color(0xFFCBD7E6), fontSize = 12.sp, lineHeight = 19.sp)
            }
            Text("SONY LUT STUDIO · COLOR WORKFLOW", color = Color(0xFF8295AD), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (showSettings) {
        SettingsDialog(
            readUri = readTreeUri,
            outputUri = outputTreeUri,
            onChooseRead = { readFolderPicker.launch(null) },
            onChooseOutput = { outputFolderPicker.launch(null) },
            onClose = { showSettings = false },
            recentExports = exportHistory,
            exportFormat = exportFormat,
            jpegQuality = jpegQuality,
            onExportFormatChange = { exportFormat = it; prefs.edit().putString(KEY_EXPORT_FORMAT, it).apply() },
            onJpegQualityChange = { jpegQuality = it; prefs.edit().putInt(KEY_JPEG_QUALITY, it).apply() },
            onOpenExport = { openExport(context, it) }
        )
    }
    if (showPhotoBrowser && readTreeUri != null) {
        PhotoBrowserDialog(
            rootUri = readTreeUri!!,
            onClose = { showPhotoBrowser = false },
            onConfirm = { selected ->
                showPhotoBrowser = false
                if (selected.isNotEmpty()) {
                    val first = selected.first()
                    queue = selected.drop(1)
                    scope.launch { loadPhoto(first); if (!error && lutInfo != null) applyLut() }
                }
            }
        )
    }
    if (showLutBrowser) {
        LutBrowserDialog(
            entries = entries,
            customCategories = customCategories,
            selectedId = selectedLutId,
            currentPreview = originalBitmap,
            lutDirectory = lutDir,
            initialFilter = lastCategory,
            onFilterChange = { lastCategory = it; prefs.edit().putString(KEY_LAST_CATEGORY, it).apply() },
            onClose = { showLutBrowser = false },
            onImport = { lutImportPicker.launch(arrayOf("*/*")) },
            onSelect = { entry -> showLutBrowser = false; selectLut(entry) },
            onToggleFavorite = { entry ->
                entries = entries.map { if (it.id == entry.id) it.copy(favorite = !it.favorite) else it }
                persistEntries()
            },
            onUpdateCategories = { entry, categories ->
                entries = entries.map { if (it.id == entry.id) it.copy(categories = categories) else it }
                persistEntries()
            },
            onAddCategory = { category ->
                if (category.isNotBlank() && category !in SYSTEM_CATEGORIES) {
                    customCategories = (customCategories + category.trim()).distinct()
                    persistCategories()
                }
            },
            onDelete = { entry ->
                if (!entry.isBuiltIn) {
                    val linked = presets.filter { it.lutId == entry.id }
                    File(lutDir, entry.fileName).delete()
                    entries = entries.filterNot { it.id == entry.id }
                    if (selectedLutId == entry.id) {
                        selectedLutId = null
                        lutInfo = null
                        processedBitmap?.recycle()
                        processedBitmap = null
                        lutFileName = "尚未选择 LUT"
                        prefs.edit().remove(KEY_LAST_LUT).apply()
                    }
                    persistEntries()
                    if (linked.isNotEmpty()) status = "已删除 LUT；${linked.size} 个预设保留为缺失引用状态。"
                }
            },
            onRename = { entry, newName ->
                entries = entries.map { if (it.id == entry.id && !it.isBuiltIn) it.copy(name = newName) else it }
                if (selectedLutId == entry.id) lutFileName = newName
                persistEntries()
            }
        )
    }
    if (showPresetManager) {
        PresetManagerDialog(
            presets = presets,
            entries = entries,
            onClose = { showPresetManager = false },
            onSave = { name, note ->
                val id = selectedLutId
                if (id != null) {
                    presets = presets + ColorPreset("preset_${System.currentTimeMillis()}", name, id, intensity, note)
                    persistPresets()
                }
            },
            onApply = { preset ->
                val entry = entries.firstOrNull { it.id == preset.lutId }
                if (entry == null) {
                    status = "该预设引用的 LUT 已不存在。"
                    error = true
                } else {
                    intensity = preset.intensity
                    prefs.edit().putFloat(KEY_INTENSITY, intensity).apply()
                    selectLut(entry)
                }
            },
            onRename = { preset, name ->
                presets = presets.map { if (it.id == preset.id) it.copy(name = name) else it }
                persistPresets()
            },
            onDelete = { preset ->
                presets = presets.filterNot { it.id == preset.id }
                persistPresets()
            }
        )
    }
}

private val Accent = Color(0xFFB9D9FF)
private val Muted = Color(0xFF9FB9D5)

@Composable
private fun SettingsDialog(
    readUri: String?,
    outputUri: String?,
    onChooseRead: () -> Unit,
    onChooseOutput: () -> Unit,
    onClose: () -> Unit,
    recentExports: List<ExportRecord>,
    exportFormat: String,
    jpegQuality: Int,
    onExportFormatChange: (String) -> Unit,
    onJpegQualityChange: (Int) -> Unit,
    onOpenExport: (ExportRecord) -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101827)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogHeader("设置", onClose)
                GlassPanel {
                    Text("文件夹管理", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text("照片读取文件夹", color = Muted, fontSize = 12.sp)
                    Text(folderLabel(readUri), color = Color.White, fontSize = 14.sp)
                    Text("首次设置时由 Android 授权目录；之后照片浏览和选择均使用应用内界面。", color = Muted, fontSize = 11.sp)
                    GlassButton("指定读取文件夹", Icons.Rounded.FolderOpen, true, onChooseRead)
                    Spacer(Modifier.height(12.dp))
                    Text("照片输出文件夹", color = Muted, fontSize = 12.sp)
                    Text(folderLabel(outputUri), color = Color.White, fontSize = 14.sp)
                    Text("导出时直接写入此目录，不再弹出保存位置窗口。", color = Muted, fontSize = 11.sp)
                    GlassButton("指定输出文件夹", Icons.Rounded.Folder, true, onChooseOutput)
                }
                GlassPanel {
                    Text("画质与导出", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("导出格式", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = exportFormat == "png16", onClick = { onExportFormatChange("png16") }, label = { Text("16-bit PNG") })
                        FilterChip(selected = exportFormat == "jpeg", onClick = { onExportFormatChange("jpeg") }, label = { Text("JPEG") })
                    }
                    Text("16-bit PNG：RGB 每通道 16 位、无损压缩；JPEG：兼容性更好，但仍为有损格式。当前源图解码为 8-bit，不能恢复源文件已丢失的信息。", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                    if (exportFormat == "jpeg") {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("JPEG 质量", color = Color.White)
                            Text("$jpegQuality", color = Accent, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = jpegQuality.toFloat(), onValueChange = { onJpegQualityChange(it.toInt().coerceIn(95, 100)) }, valueRange = 95f..100f, steps = 4)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("处理核心：Float32 RGB · 逐行低内存处理 · sRGB 输出标记", color = Muted, fontSize = 11.sp)
                }
                GlassPanel {
                    Text("最近导出", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (recentExports.isEmpty()) {
                        Text("暂无导出记录。", color = Muted, fontSize = 12.sp)
                    } else {
                        recentExports.forEach { record ->
                            TextButton(onClick = { onOpenExport(record) }) {
                                Icon(Icons.Rounded.Image, null, tint = Accent)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(record.name, color = Color.White, maxLines = 1)
                                    Text(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(record.time)), color = Muted, fontSize = 10.sp)
                                }
                                Icon(Icons.Rounded.OpenInNew, null, tint = Muted)
                            }
                        }
                    }
                }
                GlassPanel {
                    Text("关于", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sony LUT Studio · Version 0.7 (8)", color = Muted, fontSize = 13.sp)
                    Text("Float32 色彩处理 · 16-bit PNG · 3D LUT 四面体插值", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PhotoBrowserDialog(rootUri: String, onClose: () -> Unit, onConfirm: (List<PhotoRef>) -> Unit) {
    val context = LocalContext.current
    val root = remember(rootUri) { DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) }
    var path by remember { mutableStateOf(listOfNotNull(root)) }
    var children by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var selected by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sort by remember { mutableStateOf("名称") }
    var sortMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(path, sort) {
        children = withContext(Dispatchers.IO) {
            val files = path.lastOrNull()?.listFiles()?.toList() ?: emptyList()
            val filtered = files.filter { it.isDirectory || (it.isFile && it.type?.startsWith("image/") == true) }
            when (sort) {
                "名称" -> filtered.sortedWith(compareBy<DocumentFile>({ !it.isDirectory }, { it.name?.lowercase(Locale.getDefault()) ?: "" }))
                "修改时间" -> filtered.sortedByDescending { it.lastModified() }
                "拍摄时间" -> filtered.sortedByDescending { doc ->
                    try {
                        context.contentResolver.openInputStream(doc.uri)?.use { input ->
                            ExifInterface(input).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        } ?: ""
                    } catch (_: Exception) { "" }
                }
                else -> filtered
            }
        }
    }
    val visible = children.filter { it.name.orEmpty().contains(query, true) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101827)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (path.size > 1) path = path.dropLast(1) else onClose()
                    }) { Icon(Icons.Rounded.ArrowBack, null, tint = Accent) }
                    Column(Modifier.weight(1f)) {
                        Text("选择照片", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(path.lastOrNull()?.name ?: "读取目录", color = Muted, fontSize = 11.sp, maxLines = 1)
                    }
                    Box {
                        TextButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, null, tint = Accent); Text(sort, color = Accent) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("名称", "拍摄时间", "修改时间").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { sort = option; sortMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索当前文件夹") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .25f),
                        focusedPlaceholderColor = Muted, unfocusedPlaceholderColor = Muted
                    )
                )
                Spacer(Modifier.height(10.dp))
                Text("点击文件夹进入；勾选照片可跨文件夹多选。已选 ${selected.size} 张。", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (root?.canRead() != true) "读取目录不可访问，请返回设置重新授权。" else "当前文件夹没有可显示的照片。",
                            color = if (root?.canRead() != true) Color(0xFFFFC38B) else Muted
                        )
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visible, key = { it.uri.toString() }) { doc ->
                            if (doc.isDirectory) {
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .08f))
                                        .clickable { path = path + doc }.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Rounded.Folder, null, tint = Accent, modifier = Modifier.size(48.dp))
                                    Text(doc.name ?: "文件夹", color = Color.White, fontSize = 11.sp, maxLines = 2)
                                }
                            } else {
                                PhotoGridItem(
                                    document = doc,
                                    selected = doc.uri.toString() in selected,
                                    onClick = {
                                        val key = doc.uri.toString()
                                        selected = if (key in selected) selected - key else selected + (key to (doc.name ?: "photo"))
                                    }
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = emptyMap() }) { Text("清空选择", color = Muted) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onClose, colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val chosen = selected.map { (uri, name) -> PhotoRef(uri, name) }
                            onConfirm(chosen)
                        },
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF102039))
                    ) { Text("导入 ${selected.size} 张") }
                }
            }
        }
    }
}

@Composable
private fun PhotoGridItem(document: DocumentFile, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = document.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(document.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth > 0 && maxOf(opts.outWidth, opts.outHeight) / sample > 320) sample *= 2
                val decoded = context.contentResolver.openInputStream(document.uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    })
                }
                if (decoded != null) applyExifOrientation(context, document.uri, decoded) else null
            } catch (_: Exception) { null }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF315578) else Color.White.copy(alpha = .07f))
            .clickable(onClick = onClick).padding(5.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF263B59))) {
            bitmap?.let { ComposeImage(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Image, null, tint = Muted) }
            if (selected) {
                Surface(Modifier.align(Alignment.TopEnd).padding(5.dp), shape = RoundedCornerShape(20.dp), color = Accent) {
                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF102039), modifier = Modifier.padding(4.dp).size(16.dp))
                }
            }
        }
        Text(document.name ?: "image", color = Color.White, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun LutBrowserDialog(
    entries: List<LutEntry>,
    customCategories: List<String>,
    selectedId: String?,
    currentPreview: Bitmap?,
    lutDirectory: File,
    initialFilter: String,
    onFilterChange: (String) -> Unit,
    onClose: () -> Unit,
    onImport: () -> Unit,
    onSelect: (LutEntry) -> Unit,
    onToggleFavorite: (LutEntry) -> Unit,
    onUpdateCategories: (LutEntry, List<String>) -> Unit,
    onAddCategory: (String) -> Unit,
    onDelete: (LutEntry) -> Unit,
    onRename: (LutEntry, String) -> Unit
) {
    var filter by remember { mutableStateOf(initialFilter) }
    LaunchedEffect(filter) { onFilterChange(filter) }
    var search by remember { mutableStateOf("") }
    var categoryTarget by remember { mutableStateOf<LutEntry?>(null) }
    var createCategoryDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LutEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<LutEntry?>(null) }
    var newCategory by remember { mutableStateOf("") }
    val categories = SYSTEM_CATEGORIES + customCategories
    val shown = entries.filter { entry ->
        val matchesFilter = when (filter) {
            "全部" -> true
            "内置 LUT" -> entry.isBuiltIn
            "我的 LUT" -> !entry.isBuiltIn
            "已收藏" -> entry.favorite
            else -> filter in entry.categories
        }
        matchesFilter && entry.name.contains(search, true)
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101827)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                DialogHeader("LUT 浏览器", onClose)
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索 LUT 名称") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = .2f))
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { category ->
                        FilterChip(selected = filter == category, onClick = { filter = category }, label = { Text(category) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${shown.size} 款 LUT", color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = onImport) { Icon(Icons.Rounded.Add, null, tint = Accent); Text("导入 .cube", color = Accent) }
                    TextButton(onClick = { newCategory = ""; createCategoryDialog = true }) { Icon(Icons.Rounded.Create, null, tint = Accent); Text("新建分类", color = Accent) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { entry ->
                        LutGridCard(
                            entry = entry, selected = entry.id == selectedId, previewSource = currentPreview,
                            lutDirectory = lutDirectory,
                            onSelect = { onSelect(entry) },
                            onFavorite = { onToggleFavorite(entry) },
                            onCategory = { categoryTarget = entry },
                            onRename = { renameTarget = entry },
                            onDelete = { deleteTarget = entry }
                        )
                    }
                }
            }
        }
    }
    if (createCategoryDialog) {
        AlertDialog(
            onDismissRequest = { createCategoryDialog = false },
            title = { Text("新建 LUT 分类") },
            text = { OutlinedTextField(value = newCategory, onValueChange = { newCategory = it }, label = { Text("分类名称") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategory.isNotBlank()) onAddCategory(newCategory.trim())
                    createCategoryDialog = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { createCategoryDialog = false }) { Text("取消") } }
        )
    }
    categoryTarget?.let { target ->
        var chosen by remember(target) { mutableStateOf(target.categories.toSet()) }
        var newTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { categoryTarget = null },
            title = { Text("管理分类 · ${target.name}") },
            text = {
                Column {
                    customCategories.forEach { cat ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = cat in chosen, onCheckedChange = { chosen = if (it) chosen + cat else chosen - cat })
                            Text(cat)
                        }
                    }
                    OutlinedTextField(value = newTag, onValueChange = { newTag = it }, label = { Text("新建自定义分类") }, singleLine = true)
                    TextButton(onClick = {
                        if (newTag.isNotBlank()) {
                            onAddCategory(newTag.trim())
                            chosen = chosen + newTag.trim()
                            newTag = ""
                        }
                    }) { Text("添加分类") }
                }
            },
            confirmButton = { TextButton(onClick = { onUpdateCategories(target, chosen.toList()); categoryTarget = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { categoryTarget = null }) { Text("取消") } }
        )
    }
    renameTarget?.let { target ->
        var name by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名 LUT") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onRename(target, name.trim()); renameTarget = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除自定义 LUT？") },
            text = { Text("将删除“${target.name}”及其文件。引用该 LUT 的调色预设会保留，但无法直接应用。") },
            confirmButton = { TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun LutGridCard(
    entry: LutEntry,
    selected: Boolean,
    previewSource: Bitmap?,
    lutDirectory: File,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
    onCategory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val thumb by produceState<Bitmap?>(initialValue = null, key1 = entry.id, key2 = previewSource) {
        val appContext = context
        value = withContext(Dispatchers.Default) {
            var small: Bitmap? = null
            try {
                val source = previewSource ?: return@withContext null
                val ratio = minOf(1f, 180f / maxOf(source.width, source.height))
                small = if (ratio >= 0.999f) source.copy(Bitmap.Config.ARGB_8888, false) else Bitmap.createScaledBitmap(
                    source,
                    (source.width * ratio).toInt().coerceAtLeast(1),
                    (source.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
                val info = withContext(Dispatchers.IO) {
                    if (entry.builtInAsset != null) {
                        appContext.assets.open("luts/${entry.builtInAsset}").use { CubeLutParser.parse(it) }
                    } else {
                        val file = File(lutDirectory, entry.fileName)
                        if (file.exists()) file.inputStream().use { CubeLutParser.parse(it) } else null
                    }
                }
                val lutInfo = info ?: return@withContext null
                TetrahedralLutProcessor.apply(small!!, lutInfo, 1f)
            } catch (_: Exception) {
                null
            } finally {
                small?.recycle()
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFF28496B) else Color.White.copy(alpha = .08f)).clickable(onClick = onSelect).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF263B59))) {
            thumb?.let { ComposeImage(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Palette, null, tint = Accent, modifier = Modifier.size(42.dp)) }
            IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(if (entry.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (entry.favorite) Color(0xFFFF8FA3) else Color.White)
            }
            Box(Modifier.align(Alignment.TopStart)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("管理分类") }, onClick = { menu = false; onCategory() })
                    if (!entry.isBuiltIn) {
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (selected) Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), color = Accent, shape = RoundedCornerShape(8.dp)) {
                Text("当前使用", color = Color(0xFF102039), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
            }
        }
        Text(entry.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 7.dp))
        Text(if (entry.isBuiltIn) "内置 LUT" else "我的 LUT", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PresetManagerDialog(
    presets: List<ColorPreset>,
    entries: List<LutEntry>,
    onClose: () -> Unit,
    onSave: (String, String) -> Unit,
    onApply: (ColorPreset) -> Unit,
    onRename: (ColorPreset, String) -> Unit,
    onDelete: (ColorPreset) -> Unit
) {
    var saveDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ColorPreset?>(null) }
    var deleteTarget by remember { mutableStateOf<ColorPreset?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101827)) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                DialogHeader("我的调色预设", onClose)
                Button(onClick = { newName = ""; note = ""; saveDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF102039))) {
                    Icon(Icons.Rounded.Add, null); Text("  保存当前调色方案")
                }
                Spacer(Modifier.height(12.dp))
                if (presets.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("还没有保存的调色预设。", color = Muted) }
                } else {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        presets.forEach { preset ->
                            val lutName = entries.firstOrNull { it.id == preset.lutId }?.name ?: "缺少 LUT"
                            GlassPanel {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(preset.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("$lutName · ${preset.intensity.toInt()}%", color = Muted, fontSize = 11.sp)
                                        if (preset.note.isNotBlank()) Text(preset.note, color = Muted, fontSize = 11.sp)
                                    }
                                    IconButton(onClick = { onApply(preset) }) { Icon(Icons.Rounded.PlayArrow, null, tint = Accent) }
                                    Box {
                                        var menu by remember { mutableStateOf(false) }
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Muted) }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; renameTarget = preset })
                                            DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; deleteTarget = preset })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (saveDialog) {
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            title = { Text("保存调色预设") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("预设名称") }, singleLine = true)
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）" )}, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) onSave(newName.trim(), note.trim()); saveDialog = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("取消") } }
        )
    }
    renameTarget?.let { target ->
        var name by remember(target) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名预设") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onRename(target, name.trim()); renameTarget = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除预设？") },
            text = { Text("确定删除“${target.name}”？") },
            confirmButton = { TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun DialogHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = Accent) }
    }
}

@Composable
private fun GlassButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF102039), disabledContainerColor = Color(0xFF71839A).copy(alpha = .35f)),
        shape = RoundedCornerShape(16.dp)
    ) { Icon(icon, null); Text("  $label", fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .09f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(Modifier.padding(17.dp), content = content) }
}

@Composable
private fun PreviewImage(bitmap: Bitmap) {
    ComposeImage(bitmap.asImageBitmap(), "照片预览", Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Fit)
}

private fun loadEntries(prefs: android.content.SharedPreferences, context: Context): List<LutEntry> {
    val stored = mutableMapOf<String, JSONObject>()
    try {
        val arr = JSONArray(prefs.getString(KEY_ENTRIES, "[]"))
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            stored[obj.getString("id")] = obj
        }
    } catch (_: Exception) { }
    val result = mutableListOf<LutEntry>()
    BUILT_INS.forEach { asset ->
        val id = "builtin_${asset.lowercase(Locale.US)}"
        val meta = stored[id]
        val cats = meta?.optJSONArray("categories") ?: JSONArray()
        result += LutEntry(
            id = id,
            name = meta?.optString("name")?.takeIf { !it.isNullOrBlank() }
                ?: asset.removePrefix("CV-Arri-").removeSuffix(".cube"),
            fileName = asset,
            builtInAsset = asset,
            favorite = meta?.optBoolean("favorite") ?: false,
            categories = (0 until cats.length()).map { cats.getString(it) }
        )
    }
    stored.values.forEach { o ->
        val id = o.optString("id")
        val fileName = o.optString("fileName")
        if (id.isNotBlank() && fileName.isNotBlank() && File(context.filesDir, "luts/$fileName").exists()) {
            val cats = o.optJSONArray("categories") ?: JSONArray()
            result += LutEntry(
                id = id,
                name = o.optString("name", File(fileName).nameWithoutExtension),
                fileName = fileName,
                favorite = o.optBoolean("favorite"),
                categories = (0 until cats.length()).map { cats.getString(it) }
            )
        }
    }
    return result
}

private fun saveEntries(prefs: android.content.SharedPreferences, entries: List<LutEntry>) {
    val arr = JSONArray()
    entries.forEach { e ->
        arr.put(JSONObject().put("id", e.id).put("name", e.name).put("fileName", e.fileName)
            .put("favorite", e.favorite).put("categories", JSONArray(e.categories)))
    }
    prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
}

private fun loadPresets(prefs: android.content.SharedPreferences): List<ColorPreset> {
    val json = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ColorPreset(o.getString("id"), o.getString("name"), o.getString("lutId"), o.optDouble("intensity", 100.0).toFloat(), o.optString("note"), o.optLong("createdAt"))
        }
    } catch (_: Exception) { emptyList() }
}

private fun savePresets(prefs: android.content.SharedPreferences, presets: List<ColorPreset>) {
    val arr = JSONArray()
    presets.forEach { p ->
        arr.put(JSONObject().put("id", p.id).put("name", p.name).put("lutId", p.lutId)
            .put("intensity", p.intensity.toDouble()).put("note", p.note).put("createdAt", p.createdAt))
    }
    prefs.edit().putString(KEY_PRESETS, arr.toString()).apply()
}

private fun loadExportHistory(prefs: android.content.SharedPreferences): List<ExportRecord> {
    val arr = try { JSONArray(prefs.getString(KEY_EXPORT_HISTORY, "[]")) } catch (_: Exception) { JSONArray() }
    return (0 until arr.length()).mapNotNull { i ->
        try {
            val o = arr.getJSONObject(i)
            ExportRecord(o.getString("uri"), o.getString("name"), o.optLong("time"))
        } catch (_: Exception) { null }
    }
}

private fun saveExportHistory(prefs: android.content.SharedPreferences, records: List<ExportRecord>) {
    val arr = JSONArray()
    records.take(10).forEach { r ->
        arr.put(JSONObject().put("uri", r.uri).put("name", r.name).put("time", r.time))
    }
    prefs.edit().putString(KEY_EXPORT_HISTORY, arr.toString()).apply()
}

private fun openExport(context: Context, record: ExportRecord) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(record.uri), "image/jpeg")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "无法打开该照片，请检查文件访问权限。", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun loadCategories(prefs: android.content.SharedPreferences): List<String> {
    val saved = prefs.getString(KEY_CATEGORIES, null)
    if (saved == null) return listOf("电影风格", "冷色调", "暖色调", "胶片风格")
    val arr = try { JSONArray(saved) } catch (_: Exception) { JSONArray() }
    return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
}

private fun saveCategories(prefs: android.content.SharedPreferences, categories: List<String>) {
    prefs.edit().putString(KEY_CATEGORIES, JSONArray(categories.distinct()).toString()).apply()
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "Imported.cube"
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index) ?: name
        }
    }
    return name
}

private fun uniqueFileName(dir: File, requested: String): String {
    val base = requested.removeSuffix(".cube")
    var candidate = "$base.cube"
    var i = 1
    while (File(dir, candidate).exists()) { candidate = "${base}_$i.cube"; i++ }
    return candidate
}

private fun uniqueDocumentName(folder: DocumentFile, base: String, extension: String): String {
    var candidate = "$base.$extension"
    var i = 1
    while (folder.findFile(candidate) != null) { candidate = "${base}_${i.toString().padStart(3, '0')}.$extension"; i++ }
    return candidate
}

private fun sanitizeName(value: String): String =
    value.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_').take(70).ifBlank { "Photo" }

private fun folderLabel(uri: String?): String {
    if (uri == null) return "尚未设置"
    return try { Uri.parse(uri).lastPathSegment?.substringAfterLast(':') ?: "已设置目录" } catch (_: Exception) { "已设置目录" }
}

private fun decodePreviewBitmap(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("无法识别照片尺寸或格式。")
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }) ?: throw IllegalArgumentException("Android 无法解码该图片。")
    return applyExifOrientation(file, decoded)
}

private fun decodeFullResolutionBitmap(file: File): Bitmap {
    val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB) })
        ?: throw IllegalArgumentException("无法解码原始照片。")
    return applyExifOrientation(file, decoded)
}

private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
    val orientation = try {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
    return applyExifOrientation(orientation, bitmap)
}

private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    val orientation = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
    return applyExifOrientation(orientation, bitmap)
}

private fun applyExifOrientation(orientation: Int, bitmap: Bitmap): Bitmap {
    if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return bitmap
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val values = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> floatArrayOf(-1f, 0f, w, 0f, 1f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> floatArrayOf(-1f, 0f, w, 0f, -1f, h, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> floatArrayOf(1f, 0f, 0f, 0f, -1f, h, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_ROTATE_90 -> floatArrayOf(0f, -1f, h, 1f, 0f, 0f, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_TRANSVERSE -> floatArrayOf(0f, -1f, h, -1f, 0f, w, 0f, 0f, 1f)
        ExifInterface.ORIENTATION_ROTATE_270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, w, 0f, 0f, 1f)
        else -> return bitmap
    }
    val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { setValues(values) }, true)
    if (corrected !== bitmap) bitmap.recycle()
    return corrected
}
