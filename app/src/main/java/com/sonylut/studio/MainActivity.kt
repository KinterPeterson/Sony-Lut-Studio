package com.sonylut.studio

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var info by remember { mutableStateOf<CubeLutInfo?>(null) }
    var fileName by remember { mutableStateOf("还没有选择 .cube 文件") }
    var status by remember { mutableStateOf("尚未导入 LUT 文件") }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true; error = false; info = null
            fileName = displayName(context, uri)
            status = "正在读取并验证文件…"
            try {
                val result = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw CubeLutParseException("无法打开所选文件。")
                    CubeLutParser.parse(stream)
                }
                info = result
                status = "LUT 文件解析成功"
            } catch (e: Exception) {
                status = e.message ?: "读取失败，请检查文件。"
                error = true
            } finally { loading = false }
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF101827), Color(0xFF1B2B43), Color(0xFF263B59), Color(0xFF111827))))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .12f), modifier = Modifier.size(54.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ColorLens, null, tint = Color(0xFFB9D9FF), modifier = Modifier.size(29.dp)) }
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Sony LUT Studio", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("V0.2 · 3D LUT 导入与解析", color = Color(0xFFB6C5D8), fontSize = 13.sp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("让色彩，回到你的掌控。", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("导入 .cube 文件，检查 3D LUT 参数与数据完整性。", color = Color(0xFFB6C5D8), fontSize = 14.sp)
            }
            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = Color(0xFFB9D9FF))
                    Text("LUT 文件", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(fileName, color = Color(0xFFE5EDF8), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(status, color = if (error) Color(0xFFFFA6A6) else Color(0xFFB6C5D8), fontSize = 13.sp)
                if (loading) { Spacer(Modifier.height(12.dp)); CircularProgressIndicator(color = Color(0xFFB9D9FF), strokeWidth = 2.dp) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9D9FF), contentColor = Color(0xFF102039)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Text("  导入 .cube LUT", fontWeight = FontWeight.SemiBold)
                }
            }
            info?.let { lut ->
                GlassPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF83E0B2))
                        Text("解析结果", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    DetailRow("LUT 名称", lut.title)
                    DetailRow("类型", "3D LUT")
                    DetailRow("尺寸", "${lut.size} × ${lut.size} × ${lut.size}")
                    DetailRow("颜色数据", "${lut.dataCount} 组 RGB")
                    DetailRow("DOMAIN_MIN", lut.domainMin.joinToString(", ") { "%.4f".format(it) })
                    DetailRow("DOMAIN_MAX", lut.domainMax.joinToString(", ") { "%.4f".format(it) })
                }
            }
            GlassPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null, tint = Color(0xFFB9D9FF))
                    Text("本版本说明", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("V0.2 已实现 .cube 文件选择、读取和基础格式校验。照片导入、LUT 实际运算与 JPEG 导出将在后续版本加入。", color = Color(0xFFCBD7E6), fontSize = 13.sp, lineHeight = 20.sp)
            }
            Text("SONY LUT STUDIO · COLOR WORKFLOW", color = Color(0xFF8295AD), fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .09f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Color(0xFF9FB2C9), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
    }
    HorizontalDivider(color = Color.White.copy(alpha = .08f))
}

private fun displayName(context: Context, uri: Uri): String {
    var name = uri.lastPathSegment ?: "selected.cube"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) name = cursor.getString(i) ?: name
        }
    }
    return name
}
