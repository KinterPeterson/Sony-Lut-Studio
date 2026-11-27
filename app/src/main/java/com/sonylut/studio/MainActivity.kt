package com.sonylut.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.exifinterface.media.ExifInterface
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import java.io.File

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

    var lutInfo by remember { mutableStateOf<CubeLutInfo?>(null) }
    var lutFileName by remember { mutableStateOf("尚未导入 .cube 文件") }
    var photoFileName by remember { mutableStateOf("尚未选择 JPEG 照片") }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("先导入 LUT 文件和 JPEG 照片") }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lutIntensity by remember { mutableFloatStateOf(100f) }
    var comparingOriginal by remember { mutableStateOf(false) }
    var processingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var sourcePhotoFile by remember { mutableStateOf<File?>(null) }
    var exportSourceFile by remember { mutableStateOf<File?>(null) }
    var exportLut by remember { mutableStateOf<CubeLutInfo?>(null) }
    var exportIntensity by remember { mutableFloatStateOf(1f) }
    var builtInMenuExpanded by remember { mutableStateOf(false) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val sourceFile = exportSourceFile
        val lut = exportLut
        if (uri != null && sourceFile != null && lut != null) scope.launch {
            loading = true
            error = false
            status = "正在解码原始照片并校正 EXIF 方向…"
            var fullBitmap: Bitmap? = null
            var resultBitmap: Bitmap? = null
            try {
                withContext(Dispatchers.IO) {
                    fullBitmap = decodeFullResolutionBitmap(sourceFile)
                }
                val source = fullBitmap ?: throw IllegalStateException("原始照片解码失败。")
                status = "正在以原始分辨率逐行应用 LUT…"
                resultBitmap = withContext(Dispatchers.Default) {
                    val job = coroutineContext[kotlinx.coroutines.Job]
                    TetrahedralLutProcessor.apply(source, lut, exportIntensity) { row, total ->
                        if (row % 32 == 0 || row == total) job?.ensureActive()
                    }
                }
                val output = resultBitmap ?: throw IllegalStateException("LUT 处理失败。")
                status = "正在写入 JPEG（质量 98）…"
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        if (!output.compress(Bitmap.CompressFormat.JPEG, 98, out)) {
                            throw IllegalStateException("JPEG 编码失败。")
                        }
                    } ?: throw IllegalStateException("无法创建导出文件。")
                }
                status = "导出完成 · ${output.width} × ${output.height} px · JPEG 质量 98"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "照片导出失败。"
                error = true
            } finally {
                fullBitmap?.recycle()
                resultBitmap?.recycle()
                loading = false
                exportSourceFile = null
                exportLut = null
            }
        }
    }

    fun loadBuiltInLut(assetName: String) {
        if (loading) return
        scope.launch {
            loading = true
            error = false
            status = "正在载入内置 LUT…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    context.assets.open("luts/$assetName").use { CubeLutParser.parse(it) }
                }
                lutInfo = parsed
                lutFileName = "内置 · $assetName"
                processedBitmap = null
                lutIntensity = 100f
                status = "内置 LUT 载入成功：${parsed.title}"
            } catch (e: Exception) {
                status = e.message ?: "内置 LUT 载入失败。"
                error = true
            } finally {
                loading = false
            }
        }
    }

    val lutPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true
            error = false
            status = "正在读取并验证 LUT…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw CubeLutParseException("无法打开所选 LUT 文件。")
                    CubeLutParser.parse(stream)
                }
                lutInfo = parsed
                lutFileName = displayName(context, uri)
                processedBitmap = null
                lutIntensity = 100f
                status = "LUT 解析成功：${parsed.title}"
            } catch (e: Exception) {
                status = e.message ?: "LUT 读取失败，请检查文件。"
                error = true
            } finally {
                loading = false
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true
            error = false
            processedBitmap = null
            status = "正在读取照片并校正 EXIF 方向…"
            try {
                val cached = withContext(Dispatchers.IO) { copyUriToCache(context, uri) }
                val decoded = withContext(Dispatchers.IO) { decodePreviewBitmap(cached) }
                sourcePhotoFile?.delete()
                sourcePhotoFile = cached
                originalBitmap?.recycle()
                originalBitmap = decoded
                photoFileName = displayName(context, uri)
                status = "照片已载入 · EXIF 方向已校正 · 预览最长边不超过 2048 px"
            } catch (e: Exception) {
                status = e.message ?: "照片读取失败。"
                error = true
            } finally {
                loading = false
            }
        }
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
                    val job = coroutineContext[kotlinx.coroutines.Job]
                    TetrahedralLutProcessor.apply(source, lut, lutIntensity / 100f) { row, total ->
                        if (row % 32 == 0 || row == total) job?.ensureActive()
                    }
                }
                processedBitmap = result
                status = "处理完成 · LUT 强度 ${lutIntensity.toInt()}% · ${result.width} × ${result.height}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                status = e.message ?: "LUT 处理失败。"
                error = true
            } finally {
                loading = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(Color(0xFF101827), Color(0xFF1B2B43), Color(0xFF263B59), Color(0xFF111827))
            )
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = .12f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ColorLens, null, tint = Color(0xFFB9D9FF), modifier = Modifier.size(29.dp))
                    }
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Sony LUT Studio", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("V0.5 · 交互式 LUT 调色工作台", color = Color(0xFFB6C5D8), fontSize = 13.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("让色彩，回到你的掌控。", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("导入照片与 .cube LUT，实时调整色彩强度并对比效果。", color = Color(0xFFB6C5D8), fontSize = 14.sp)
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = Color(0xFFB9D9FF))
                    Text("LUT 文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(lutFileName, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                lutInfo?.let { lut ->
                    Spacer(Modifier.height(5.dp))
                    Text("${lut.title} · ${lut.size}³ · ${lut.dataCount} 组 RGB", color = Color(0xFF9FB9D5), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                GlassButton("导入 .cube LUT", Icons.Rounded.Add, enabled = !loading) {
                    lutPicker.launch(arrayOf("*/*"))
                }
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { builtInMenuExpanded = true },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB9D9FF))
                    ) { Text("选择内置 Arri LUT") }
                    DropdownMenu(
                        expanded = builtInMenuExpanded,
                        onDismissRequest = { builtInMenuExpanded = false }
                    ) {
                        listOf(
                            "CV-Arri-IceBlue.cube",
                            "CV-Arri-Jamaica.cube",
                            "CV-Arri-Neutral.cube",
                            "CV-Arri-Tungsten.cube",
                            "CV-Arri-Utopia.cube"
                        ).forEach { asset ->
                            DropdownMenuItem(
                                text = { Text(asset.removePrefix("CV-Arri-").removeSuffix(".cube")) },
                                onClick = {
                                    builtInMenuExpanded = false
                                    loadBuiltInLut(asset)
                                }
                            )
                        }
                    }
                }
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, null, tint = Color(0xFFB9D9FF))
                    Text("照片", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(photoFileName, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                originalBitmap?.let {
                    Spacer(Modifier.height(5.dp))
                    Text("${it.width} × ${it.height} px（当前预览图）", color = Color(0xFF9FB9D5), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                GlassButton("选择 JPEG 照片", Icons.Rounded.Add, enabled = !loading) {
                    photoPicker.launch(arrayOf("image/jpeg", "image/*"))
                }
            }

            if (originalBitmap != null) {
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, tint = Color(0xFFB9D9FF))
                        Text("色彩处理", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (lutInfo == null) "请先导入一个有效的 3D LUT 文件。" else "调整 LUT 强度，找到适合当前照片的色彩表现。",
                        color = Color(0xFFB6C5D8), fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("LUT 强度", color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${lutIntensity.toInt()}%", color = Color(0xFFB9D9FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = lutIntensity,
                        onValueChange = { value -> lutIntensity = value },
                        onValueChangeFinished = { if (lutInfo != null && originalBitmap != null) applyLut() },
                        valueRange = 0f..200f,
                        enabled = !loading && lutInfo != null,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFB9D9FF),
                            activeTrackColor = Color(0xFFB9D9FF),
                            inactiveTrackColor = Color.White.copy(alpha = .18f)
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("原图 0%", color = Color(0xFF9FB9D5), fontSize = 11.sp)
                        Text("增强效果 200%", color = Color(0xFF9FB9D5), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { applyLut() },
                        enabled = !loading && originalBitmap != null && lutInfo != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB9D9FF),
                            contentColor = Color(0xFF102039),
                            disabledContainerColor = Color(0xFF71839A).copy(alpha = .35f),
                            disabledContentColor = Color.White.copy(alpha = .55f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null)
                        Text("  应用当前强度", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            originalBitmap?.let { original ->
                GlassPanel {
                    Text("照片预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (processedBitmap == null) "等待生成 LUT 效果" else "按住下方照片可临时查看原图，松开恢复效果图",
                        color = Color(0xFFB6C5D8), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    val preview = if (comparingOriginal || processedBitmap == null) original else processedBitmap!!
                    Box(
                        Modifier.fillMaxWidth()
                            .pointerInput(processedBitmap, original) {
                                detectTapGestures(onPress = {
                                    comparingOriginal = true
                                    try { tryAwaitRelease() } finally { comparingOriginal = false }
                                })
                            }
                    ) {
                        PreviewImage(preview)
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                            color = Color(0xCC101827),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                if (comparingOriginal || processedBitmap == null) "原图" else "LUT 效果",
                                color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    processedBitmap?.let {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val file = sourcePhotoFile
                                val lut = lutInfo
                                if (file != null && lut != null) {
                                    exportSourceFile = file
                                    exportLut = lut
                                    exportIntensity = lutIntensity / 100f
                                    exportPicker.launch("SonyLUTStudio_${System.currentTimeMillis()}.jpg")
                                } else {
                                    status = "找不到原始照片缓存，请重新导入照片。"
                                    error = true
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFB9D9FF),
                                contentColor = Color(0xFF102039)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null)
                            Text("  导出处理后的 JPEG", fontWeight = FontWeight.SemiBold)
                        }
                        Text("导出将重新读取原始照片，以原始分辨率处理并保存 JPEG（质量 98）。", color = Color(0xFF9FB9D5), fontSize = 11.sp)
                    }
                }
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = Color(0xFFB9D9FF))
                    Text("处理状态与版本说明", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(status, color = if (error) Color(0xFFFFA6A6) else Color(0xFFB6C5D8), fontSize = 13.sp, lineHeight = 20.sp)
                if (loading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFB9D9FF),
                        trackColor = Color.White.copy(alpha = .12f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "V0.5 支持 5 款内置 Arri LUT、0–200% 强度、EXIF 方向校正及原始分辨率 JPEG 导出。处理按行执行以降低像素缓存开销。",
                    color = Color(0xFFCBD7E6), fontSize = 12.sp, lineHeight = 19.sp
                )
            }

            Text("SONY LUT STUDIO · COLOR WORKFLOW", color = Color(0xFF8295AD), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun PreviewImage(bitmap: Bitmap) {
    ComposeImage(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "照片预览",
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun GlassButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFB9D9FF),
            contentColor = Color(0xFF102039),
            disabledContainerColor = Color(0xFF71839A).copy(alpha = .35f),
            disabledContentColor = Color.White.copy(alpha = .55f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, null)
        Text("  $label", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .09f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

private fun copyUriToCache(context: Context, uri: Uri): File {
    val cached = File.createTempFile("sony_lut_source_", ".img", context.cacheDir)
    var copied = false
    var streamError: Exception? = null
    try {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cached.outputStream().buffered().use { output -> input.copyTo(output) }
                copied = cached.length() > 0L
            }
        } catch (e: Exception) {
            streamError = e
        }
        if (!copied) {
            try {
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                if (descriptor != null) {
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        cached.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    copied = cached.length() > 0L
                }
            } catch (e: Exception) {
                if (streamError == null) streamError = e
            }
        }
        if (!copied) {
            throw IllegalArgumentException(
                streamError?.message?.let { "系统文件提供器无法读取照片：$it" }
                    ?: "系统文件提供器没有返回照片数据，请尝试从手机本地文件夹重新选择。"
            )
        }
        return cached
    } catch (e: Exception) {
        cached.delete()
        throw e
    }
}

private fun decodePreviewBitmap(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("无法识别照片尺寸或文件格式，请选择 JPEG 图片。")
    }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    ) ?: throw IllegalArgumentException("Android 无法解码该图片格式。")
    return applyExifOrientation(file, decoded)
}

private fun decodeFullResolutionBitmap(file: File): Bitmap {
    val decoded = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    ) ?: throw IllegalArgumentException("无法解码原始照片。")
    return applyExifOrientation(file, decoded)
}

/** Applies all eight EXIF orientation cases and returns pixels in display orientation. */
private fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
    val orientation = try {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    if (orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED) return bitmap

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
    val matrix = Matrix().apply { setValues(values) }
    val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (corrected !== bitmap) bitmap.recycle()
    return corrected
}

private fun displayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "selected_file"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = cursor.getString(index) ?: name
        }
    }
    return name
}
