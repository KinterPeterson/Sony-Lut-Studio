package com.sonylut.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
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
import kotlinx.coroutines.withContext

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
            status = "正在读取照片…"
            try {
                val decoded = withContext(Dispatchers.IO) { decodePreviewBitmap(context, uri) }
                originalBitmap = decoded
                photoFileName = displayName(context, uri)
                status = "照片已载入（预览最长边不超过 2048 px）"
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
        scope.launch {
            loading = true
            error = false
            status = "正在进行四面体插值…"
            try {
                val result = withContext(Dispatchers.Default) {
                    TetrahedralLutProcessor.apply(source, lut)
                }
                processedBitmap = result
                status = "处理完成 · ${result.width} × ${result.height}"
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
                    Text("V0.3 · JPEG 照片与 3D LUT 处理", color = Color(0xFFB6C5D8), fontSize = 13.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("让色彩，回到你的掌控。", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("导入照片与 .cube LUT，在手机上预览色彩变换。", color = Color(0xFFB6C5D8), fontSize = 14.sp)
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
                        if (lutInfo == null) "请先导入一个有效的 3D LUT 文件。" else "已准备就绪，可以将当前 LUT 应用到照片预览。",
                        color = Color(0xFFB6C5D8), fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
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
                        Text("  应用 LUT 并生成预览", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            originalBitmap?.let { original ->
                GlassPanel {
                    Text("照片预览", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text("原图", color = Color(0xFFB6C5D8), fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                    PreviewImage(original)
                    processedBitmap?.let { result ->
                        Spacer(Modifier.height(16.dp))
                        Text("LUT 处理结果", color = Color(0xFFB6C5D8), fontSize = 12.sp)
                        Spacer(Modifier.height(7.dp))
                        PreviewImage(result)
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
                    "V0.3 支持 .cube 解析、JPEG 照片导入及 3D LUT 四面体插值预览。当前为缩放预览处理，暂不导出完整分辨率 JPEG；完整分辨率分块处理与导出将在后续版本完善。",
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

private fun decodePreviewBitmap(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IllegalArgumentException("无法打开照片文件。")
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("无法识别照片尺寸或文件格式。")
    }

    val maxDimension = 2048
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("照片解码失败。")
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
