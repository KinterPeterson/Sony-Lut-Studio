package com.sonylut.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF080D18)
private val Glass = Color(0x20FFFFFF)
private val Accent = Color(0xFF8BCBFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 24)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 24)
        setContent {
            MaterialTheme {
                SonyLUTApp()
            }
        }
    }
}

@Composable
private fun SonyLUTApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF17253C), Background, Color(0xFF080B12))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(42.dp))

            Text(
                text = "SONY LUT STUDIO",
                color = Accent,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "色彩，重新定义。",
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Professional LUT Photo Studio",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(34.dp))

            GlassCard {
                Text(
                    text = "开始创作",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "为你的照片赋予理想的色彩。",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(24.dp))
                GlassButton("选择照片", Icons.Default.Collections) {
                    // Photo picker will be connected in the next development stage.
                }
                Spacer(Modifier.height(12.dp))
                GlassButton("导入 LUT 文件", Icons.Default.Add) {
                    // SAF-based .cube import will be connected in the next development stage.
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "工作空间",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(
                    title = "我的 LUT",
                    subtitle = "管理色彩预设",
                    icon = Icons.Default.ColorLens,
                    modifier = Modifier.weight(1f)
                )
                InfoCard(
                    title = "最近作品",
                    subtitle = "查看处理照片",
                    icon = Icons.Default.Collections,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.weight(1f))

            NavigationBar(
                containerColor = Color(0xCC172235),
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.12f),
                        RoundedCornerShape(28.dp)
                    ),
                tonalElevation = 0.dp
            ) {
                val items = listOf(
                    Triple("首页", Icons.Default.Collections, 0),
                    Triple("LUT", Icons.Default.ColorLens, 1),
                    Triple("设置", Icons.Default.Settings, 2)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f),
                            indicatorColor = Color.White.copy(alpha = 0.08f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Glass, RoundedCornerShape(28.dp))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(28.dp)
            )
            .padding(22.dp),
        content = content
    )
}

@Composable
private fun GlassButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent)
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Glass, RoundedCornerShape(22.dp))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}
