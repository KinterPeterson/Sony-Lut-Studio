package com.sonylut.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { StudioScreen() } }
    }
}

/** A LUT shipped inside the app's `assets/luts` directory. */
private data class BuiltinLut(val asset: String, val label: String)

private val BUILTIN_LUTS = listOf(
    BuiltinLut("luts/CV-Arri-IceBlue.cube", "Arri IceBlue"),
    BuiltinLut("luts/CV-Arri-Jamaica.cube", "Arri Jamaica"),
    BuiltinLut("luts/CV-Arri-Neutral.cube", "Arri Neutral"),
    BuiltinLut("luts/CV-Arri-Tungsten.cube", "Arri Tungsten"),
    BuiltinLut("luts/CV-Arri-Utopia.cube", "Arri Utopia")
)

@Composable
private fun StudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lutInfo by remember { mutableStateOf<CubeLutInfo?>(null) }
    var lutLabel by remember { mutableStateOf("尚未选择 LUT") }
    var selectedBuiltin by remember { mutableStateOf<String?>(null) }

    var photoFileName by remember { mutableStateOf("尚未选择 JPEG 照片") }
    // Full-resolution original, retained for lossless export.
    var fullBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Downscaled copy used only for the on-screen preview.
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedPreview by remember { mutableStateOf<Bitmap?>(null) }

    var status by remember { mutableStateOf("先选择 LUT 文件和 JPEG 照片") }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lutIntensity by remember { mutableFloatStateOf(100f) }
    var comparingOriginal by remember { mutableStateOf(false) }
    var processingJob by remember { mutableStateOf<Job?>(null) }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val full = fullBitmap
        val lut = lutInfo
        if (uri == null) return@rememberLauncherForActivityResult
        if (full == null || lut == null) {
            status = "缺少照片或 LUT，无法导出。"
            error = true
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            loading = true
            error = false
            status = "正在全分辨率处理并导出无损 PNG…"
            try {
                val result = withContext(Dispatchers.Default) {
                    TetrahedralLutProcessor.apply(full, lut, lutIntensity / 100f)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        if (!result.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IllegalStateException("PNG 编码失败。")
                        }
                    } ?: throw IllegalStateException("无法创建导出文件。")
                }
                status = "导出完成 · 无损 PNG · ${result.width} × ${result.height} px"
            } catch (e: Exception) {
                status = e.message ?: "照片导出失败。"
                error = true
            } finally {
                loading = false
            }
        }
    }

    fun applyPreviewLut() {
        val source = previewBitmap
        val lut = lutInfo
        if (source == null || lut == null) return
        processingJob?.cancel()
        processingJob = scope.launch {
            loading = true
            error = false
            status = "正在进行四面体插值…"
            try {
                val result = withContext(Dispatchers.Default) {
                    TetrahedralLutProcessor.apply(source, lut, lutIntensity / 100f)
                }
                processedPreview = result
                status = "预览处理完成 · LUT 强度 ${lutIntensity.toInt()}% · ${result.width} × ${result.height}"
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

    fun loadBuiltinLut(item: BuiltinLut) {
        scope.launch {
            loading = true
            error = false
            status = "正在读取内置 LUT…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    context.assets.open(item.asset).use { stream -> CubeLutParser.parse(stream) }
                }
                lutInfo = parsed
                selectedBuiltin = item.asset
                lutLabel = item.label
                processedPreview = null
                status = "已加载内置 LUT：${item.label}"
            } catch (e: Exception) {
                status = e.message ?: "内置 LUT 读取失败。"
                error = true
            } finally {
                loading = false
            }
            applyPreviewLut()
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
                selectedBuiltin = null
                lutLabel = displayName(context, uri)
                processedPreview = null
                status = "LUT 解析成功：${parsed.title}"
            } catch (e: Exception) {
                status = e.message ?: "LUT 读取失败，请检查文件。"
                error = true
            } finally {
                loading = false
            }
            applyPreviewLut()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true
            error = false
            processedPreview = null
            status = "正在读取照片…"
            try {
                val decoded = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
                fullBitmap = decoded.first
                previewBitmap = decoded.second
                photoFileName = displayName(context, uri)
                status = "照片已载入（原图 ${decoded.first.width} × ${decoded.first.height}，预览 ${decoded.second.width} × ${decoded.second.height}）"
            } catch (e: Exception) {
                status = e.message ?: "照片读取失败。"
                error = true
            } finally {
                loading = false
            }
            applyPreviewLut()
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
                Text("内置多款 LUT，导入照片后实时调整色彩强度，全分辨率无损导出。", color = Color(0xFFB6C5D8), fontSize = 14.sp)
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = Color(0xFFB9D9FF))
                    Text("LUT 文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(lutLabel, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                lutInfo?.let { lut ->
                    Spacer(Modifier.height(5.dp))
                    Text("${lut.title} · ${lut.size}³ · ${lut.dataCount} 组 RGB", color = Color(0xFF9FB9D5), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                Text("内置 LUT", color = Color(0xFFB6C5D8), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUILTIN_LUTS.forEach { item ->
                        val active = selectedBuiltin == item.asset
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) Color(0xFFB9D9FF).copy(alpha = .22f) else Color.White.copy(alpha = .08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !loading) { loadBuiltinLut(item) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    item.label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (active) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFFB9D9FF), modifier = Modifier.size(18.dp))
                                } else {
                                    Text("内置", color = Color(0xFF9FB9D5), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlassButton("导入外部 .cube LUT", Icons.Rounded.Add, enabled = !loading) {
                    lutPicker.launch(arrayOf("*/*"))
                }
            }

            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, null, tint = Color(0xFFB9D9FF))
                    Text("照片", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(photoFileName, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                fullBitmap?.let { full ->
                    Spacer(Modifier.height(5.dp))
                    Text("原图 ${full.width} × ${full.height} px（用于无损导出）", color = Color(0xFF9FB9D5), fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                GlassButton("选择 JPEG 照片", Icons.Rounded.Add, enabled = !loading) {
                    photoPicker.launch(arrayOf("image/jpeg", "image/*"))
                }
            }

            if (fullBitmap != null) {
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, tint = Color(0xFFB9D9FF))
                        Text("色彩处理", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (lutInfo == null) "请先选择一个内置或外部 3D LUT 文件。" else "调整 LUT 强度，找到适合当前照片的色彩表现。",
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
                        onValueChangeFinished = { if (lutInfo != null && previewBitmap != null) applyPreviewLut() },
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
                        Text("强化上限 200%", color = Color(0xFF9FB9D5), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { applyPreviewLut() },
                        enabled = !loading && previewBitmap != null && lutInfo != null,
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

            fullBitmap?.let { full ->
                GlassPanel {
                    Text("照片预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (processedPreview == null) "等待生成 LUT 效果" else "按住下方照片可临时查看原图，松开恢复效果图",
                        color = Color(0xFFB6C5D8), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    val previewSource = previewBitmap ?: full
                    val preview = if (comparingOriginal || processedPreview == null) previewSource else processedPreview!!
                    Box(
                        Modifier.fillMaxWidth()
                            .pointerInput(processedPreview, previewSource) {
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
                                if (comparingOriginal || processedPreview == null) "原图" else "LUT 效果",
                                color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    processedPreview?.let {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                exportPicker.launch("SonyLUTStudio_${System.currentTimeMillis()}.png")
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
                            Text("  导出全分辨率无损 PNG", fontWeight = FontWeight.SemiBold)
                        }
                        Text("按原始分辨率处理照片并导出 PNG，无有损压缩；导出尺寸为 ${full.width} × ${full.height} px。", color = Color(0xFF9FB9D5), fontSize = 11.sp)
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
                    "V0.5 内置 5 款 Arri 风格 LUT，强度上限提升至 200%；照片按原始分辨率处理并以无损 PNG 导出，界面预览仍使用缩放后的副本以保证流畅度。",
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

private const val PREVIEW_MAX_EDGE = 2048
private const val FULL_RES_PIXEL_BUDGET = 24_000_000L

private fun decodePhoto(context: Context, uri: Uri): Pair<Bitmap, Bitmap> {
    val resolver = context.contentResolver
    // Some document providers do not reliably reopen the same URI stream.
    // Copy the selected image once to the app cache, then decode that local file.
    val cachedPhoto = File.createTempFile("lut_photo_", ".img", context.cacheDir)
    try {
        var copied = false
        var streamError: Exception? = null

        try {
            val input = resolver.openInputStream(uri)
            if (input != null) {
                input.use { source ->
                    cachedPhoto.outputStream().buffered().use { target -> source.copyTo(target) }
                }
                copied = cachedPhoto.length() > 0L
            }
        } catch (e: Exception) {
            streamError = e
        }

        // Fallback for providers that expose a file descriptor but return a null
        // or unusable InputStream from openInputStream().
        if (!copied) {
            try {
                val descriptor = resolver.openFileDescriptor(uri, "r")
                if (descriptor != null) {
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { source ->
                        cachedPhoto.outputStream().buffered().use { target -> source.copyTo(target) }
                    }
                    copied = cachedPhoto.length() > 0L
                }
            } catch (e: Exception) {
                if (streamError == null) streamError = e
            }
        }

        if (!copied) {
            val detail = streamError?.message?.takeIf { it.isNotBlank() }
            throw IllegalArgumentException(
                if (detail != null) "系统文件提供器无法读取照片：$detail" else "系统文件提供器没有返回照片数据，请尝试从手机本地文件夹重新选择。"
            )
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cachedPhoto.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("无法识别照片尺寸或文件格式，请选择 JPEG 图片。")
        }

        val fullSample = sampleSizeForPixelBudget(bounds.outWidth, bounds.outHeight, FULL_RES_PIXEL_BUDGET)
        val previewSample = sampleSizeForLongestEdge(bounds.outWidth, bounds.outHeight, PREVIEW_MAX_EDGE)
        val full = decodeFile(cachedPhoto.absolutePath, fullSample)
        val preview = decodeFile(cachedPhoto.absolutePath, previewSample)
        return full to preview
    } finally {
        cachedPhoto.delete()
    }
}

private fun decodeFile(path: String, sample: Int): Bitmap {
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
        ?: throw IllegalArgumentException("照片数据已读取，但 Android 无法解码该图片格式。")
}

private fun sampleSizeForLongestEdge(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (maxOf(width / sample, height / sample) > maxEdge) sample *= 2
    return sample
}

private fun sampleSizeForPixelBudget(width: Int, height: Int, maxPixels: Long): Int {
    var sample = 1
    while ((width / sample).toLong() * (height / sample) > maxPixels) sample *= 2
    return sample
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
