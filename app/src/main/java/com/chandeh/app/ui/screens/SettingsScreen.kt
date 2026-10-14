package com.chandeh.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chandeh.app.BuildConfig
import com.chandeh.app.ui.theme.AppTheme

private fun openUrl(ctx: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
}

/** باز کردن بروشور معرفی برنامه (intro.pdf داخل assets) با نمایشگر PDF گوشی */
private fun openIntroPdf(ctx: Context) {
    try {
        val file = java.io.File(ctx.cacheDir, "intro.pdf")
        if (!file.exists()) {
            ctx.assets.open("intro.pdf").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "معرفی NerkhCheck"))
    } catch (_: Exception) {
    }
}

@Composable
fun SettingsScreen(
    theme: AppTheme,
    allThemes: List<AppTheme>,
    onThemeChange: (AppTheme) -> Unit,
    brsKey: String,
    onBrsKeyChange: (String) -> Unit,
    updateStatus: String?,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit
) {
    val ctx = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            "تنظیمات",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp
        )

        SettingsSection(title = "داده‌ها و اینترنت ملی", icon = Icons.Filled.CurrencyExchange) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "روی اینترنت ملی، قیمت طلا و سکه به‌صورت زنده دریافت می‌شود. برای قیمت لحظه‌ای دلار و ارزها هم، کلید رایگان BRS را وارد کنید؛ در غیر این صورت آخرین قیمت ذخیره‌شده نمایش داده می‌شود.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                var draft by remember(brsKey) { mutableStateOf(brsKey) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("کلید BRS API (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onBrsKeyChange(draft.trim()) }) {
                        Text("ذخیره")
                    }
                    OutlinedButton(
                        onClick = {
                            openUrl(
                                ctx,
                                "https://brsapi.ir/tsetmc-exchange-free-bourse-api-key-request/"
                            )
                        }
                    ) {
                        Text("دریافت کلید رایگان")
                    }
                }
                if (brsKey.isNotBlank()) {
                    Text(
                        "✓ کلید ذخیره شده و برای به‌روزرسانی بعدی استفاده می‌شود",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        SettingsSection(title = "تم رنگی", icon = Icons.Filled.Palette) {
            // ردیف افقی اسکرول‌خور تا با زیاد شدن تم‌ها صفحه شلوغ نشود
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(allThemes, key = { it.id }) { t ->
                    ThemeSwatch(
                        theme = t,
                        selected = t.id == theme.id,
                        onClick = { onThemeChange(t) }
                    )
                }
            }
        }

        SettingsSection(title = "به‌روزرسانی", icon = Icons.Filled.SystemUpdate) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "فایل‌های آپدیت (مثل تم‌های جدید) به‌صورت خودکار دریافت و همان‌لحظه اعمال می‌شوند. برای نسخه‌های جدید کد هم، خود برنامه دانلود و نصب می‌کند.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = onCheckUpdate,
                    enabled = !checkingUpdate
                ) {
                    Text(if (checkingUpdate) "در حال بررسی..." else "بررسی آپدیت")
                }
                if (updateStatus != null) {
                    Text(
                        updateStatus,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        SettingsSection(title = "درباره‌ی NerkhCheck", icon = Icons.Filled.Info) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow(
                    icon = Icons.Filled.Info,
                    title = "نسخه‌ی برنامه",
                    value = BuildConfig.VERSION_NAME
                )
                InfoRow(
                    icon = Icons.Filled.Description,
                    title = "معرفی برنامه",
                    value = "بروشور NerkhCheck",
                    onClick = { openIntroPdf(ctx) }
                )
                InfoRow(
                    icon = Icons.Filled.Code,
                    title = "گیت‌هاب",
                    value = "thaarfknight-star/NerkhCheck",
                    onClick = { openUrl(ctx, "https://github.com/thaarfknight-star/NerkhCheck") }
                )
                InfoRow(
                    icon = Icons.Filled.Article,
                    title = "لایسنس",
                    value = "Apache-2.0",
                    onClick = { showLicense = true }
                )
                InfoRow(
                    icon = Icons.Filled.Send,
                    title = "تلگرام",
                    value = "@tha_arf",
                    subtitle = "انتقاد و پیشنهاد",
                    onClick = { openUrl(ctx, "https://t.me/tha_arf") }
                )
            }
        }

        if (showLicense) {
            AlertDialog(
                onDismissRequest = { showLicense = false },
                title = { Text("لایسنس NerkhCheck") },
                text = { Text(LICENSE_TEXT, fontSize = 13.sp) },
                confirmButton = {
                    TextButton(onClick = { showLicense = false }) {
                        Text("بستن")
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box(Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(theme.accent, theme.accent2)
                    )
                )
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            theme.nameFa,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * متن صفحه‌ی «درباره» — آینه‌ی docs/about-dialog.txt در ریپو.
 * چون فایل docs داخل APK باندل نمی‌شود، متن اینجا هم نگه داشته شده
 * تا در باینری توزیعی هم باشد؛ هر تغییری در آن فایل باید اینجا هم اعمال شود.
 */
private const val LICENSE_TEXT =
    "NerkhCheck — Copyright (C) 2026 Taha Arefi (طه عارفی)\n" +
    "\n" +
    "Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
    "you may not use this file except in compliance with the License.\n" +
    "You may obtain a copy of the License at\n" +
    "\n" +
    "    http://www.apache.org/licenses/LICENSE-2.0\n" +
    "\n" +
    "Unless required by applicable law or agreed to in writing, software\n" +
    "distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
    "WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
    "See the License for the specific language governing permissions and\n" +
    "limitations under the License.\n" +
    "\n" +
    "متن کامل لایسنس: فایل LICENSE در ریشه‌ی ریپو"
