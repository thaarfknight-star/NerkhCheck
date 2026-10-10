package com.chandeh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** سبز/قرمز مخصوص اعداد روی پس‌زمینه‌ی تیره */
val ProfitGreen = Color(0xFF34D399)
val LossRed = Color(0xFFF87171)

/**
 * یک تم رنگی کامل اپ: لهجه‌ها + گرادیان کارت ویژه + کل پالت تیره‌ی برنامه.
 * با عوض شدن تم، پس‌زمینه، سطح‌ها و کارت‌های همه‌ی صفحه‌ها عوض می‌شوند.
 */
data class AppTheme(
    val id: String,
    val nameFa: String,
    val accent: Color,
    val accent2: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color
)

/** هفده تم ترکیب‌رنگی؛ اولی («نرخ‌چک») تم پیش‌فرض برنامه است */
val AppThemes = listOf(
    AppTheme(
        id = "nerkhcheck",
        nameFa = "نرخ‌چک",
        accent = Color(0xFFE8B048),
        accent2 = Color(0xFFF8D868),
        heroTop = Color(0xFF1A2C66),
        heroBottom = Color(0xFF071237),
        background = Color(0xFF071237),
        surface = Color(0xFF0D1B4A),
        surfaceVariant = Color(0xFF16265E)
    ),
    AppTheme(
        id = "aurora",
        nameFa = "شفق قطبی",
        accent = Color(0xFF2DD4BF),
        accent2 = Color(0xFFA78BFA),
        heroTop = Color(0xFF0B2E2A),
        heroBottom = Color(0xFF1E1B3D),
        background = Color(0xFF090F0E),
        surface = Color(0xFF101917),
        surfaceVariant = Color(0xFF182723)
    ),
    AppTheme(
        id = "volcano",
        nameFa = "آتشفشان",
        accent = Color(0xFFFB923C),
        accent2 = Color(0xFFF43F5E),
        heroTop = Color(0xFF3A1E0B),
        heroBottom = Color(0xFF3A0F1E),
        background = Color(0xFF100B07),
        surface = Color(0xFF1B130C),
        surfaceVariant = Color(0xFF291C12)
    ),
    AppTheme(
        id = "galaxy",
        nameFa = "کهکشان",
        accent = Color(0xFF818CF8),
        accent2 = Color(0xFFF472B6),
        heroTop = Color(0xFF1A1B3D),
        heroBottom = Color(0xFF2E1030),
        background = Color(0xFF0C0C15),
        surface = Color(0xFF151527),
        surfaceVariant = Color(0xFF20203A)
    ),
    AppTheme(
        id = "beach",
        nameFa = "ساحل",
        accent = Color(0xFF38BDF8),
        accent2 = Color(0xFFFBBF24),
        heroTop = Color(0xFF0B2740),
        heroBottom = Color(0xFF2E2410),
        background = Color(0xFF090E14),
        surface = Color(0xFF101A26),
        surfaceVariant = Color(0xFF182637)
    ),
    AppTheme(
        id = "jungle",
        nameFa = "جنگل",
        accent = Color(0xFF34D399),
        accent2 = Color(0xFFA3E635),
        heroTop = Color(0xFF0B2E1E),
        heroBottom = Color(0xFF232E0B),
        background = Color(0xFF090F0B),
        surface = Color(0xFF101D14),
        surfaceVariant = Color(0xFF182B1E)
    ),
    AppTheme(
        id = "neon",
        nameFa = "نئون",
        accent = Color(0xFF22D3EE),
        accent2 = Color(0xFFE879F9),
        heroTop = Color(0xFF0A2E38),
        heroBottom = Color(0xFF2E1040),
        background = Color(0xFF090E13),
        surface = Color(0xFF101B24),
        surfaceVariant = Color(0xFF172834)
    ),
    AppTheme(
        id = "gold",
        nameFa = "طلایی",
        accent = Color(0xFFFBBF24),
        accent2 = Color(0xFFD97706),
        heroTop = Color(0xFF2A1E08),
        heroBottom = Color(0xFF1A1206),
        background = Color(0xFF100D07),
        surface = Color(0xFF1C1710),
        surfaceVariant = Color(0xFF2A2317)
    ),
    AppTheme(
        id = "ocean",
        nameFa = "اقیانوس",
        accent = Color(0xFF3B82F6),
        accent2 = Color(0xFF06B6D4),
        heroTop = Color(0xFF0A1E3C),
        heroBottom = Color(0xFF082A38),
        background = Color(0xFF090D16),
        surface = Color(0xFF0F1830),
        surfaceVariant = Color(0xFF16233F)
    ),
    AppTheme(
        id = "crimson",
        nameFa = "زرشکی",
        accent = Color(0xFFE11D48),
        accent2 = Color(0xFFFB7185),
        heroTop = Color(0xFF380B14),
        heroBottom = Color(0xFF2A0B1E),
        background = Color(0xFF100909),
        surface = Color(0xFF1D1013),
        surfaceVariant = Color(0xFF2B171C)
    ),
    AppTheme(
        id = "royal",
        nameFa = "سلطنتی",
        accent = Color(0xFFA78BFA),
        accent2 = Color(0xFFFBBF24),
        heroTop = Color(0xFF1D1A3A),
        heroBottom = Color(0xFF2A2110),
        background = Color(0xFF0D0C14),
        surface = Color(0xFF171426),
        surfaceVariant = Color(0xFF231D3A)
    ),
    AppTheme(
        id = "sakura",
        nameFa = "ساکورا",
        accent = Color(0xFFF9A8D4),
        accent2 = Color(0xFFF472B6),
        heroTop = Color(0xFF331425),
        heroBottom = Color(0xFF2B1030),
        background = Color(0xFF100B0F),
        surface = Color(0xFF1D131B),
        surfaceVariant = Color(0xFF2C1D28)
    ),
    AppTheme(
        id = "graphite",
        nameFa = "گرافیتی",
        accent = Color(0xFFE2E8F0),
        accent2 = Color(0xFF94A3B8),
        heroTop = Color(0xFF1E293B),
        heroBottom = Color(0xFF0F172A),
        background = Color(0xFF0C0E12),
        surface = Color(0xFF151A22),
        surfaceVariant = Color(0xFF1F2733)
    ),
    AppTheme(
        id = "emerald",
        nameFa = "زمرد",
        accent = Color(0xFF10B981),
        accent2 = Color(0xFFF59E0B),
        heroTop = Color(0xFF064E3B),
        heroBottom = Color(0xFF1C1410),
        background = Color(0xFF07110D),
        surface = Color(0xFF0D1B14),
        surfaceVariant = Color(0xFF142A1E)
    ),
    AppTheme(
        id = "amethyst",
        nameFa = "آمیتیست",
        accent = Color(0xFFA78BFA),
        accent2 = Color(0xFFF472B6),
        heroTop = Color(0xFF4C1D95),
        heroBottom = Color(0xFF1A0B2E),
        background = Color(0xFF0F0A1A),
        surface = Color(0xFF181026),
        surfaceVariant = Color(0xFF241A38)
    ),
    AppTheme(
        id = "coral",
        nameFa = "مرجان",
        accent = Color(0xFFFF7B6B),
        accent2 = Color(0xFFFFB86B),
        heroTop = Color(0xFF5C1F16),
        heroBottom = Color(0xFF1A0E0A),
        background = Color(0xFF140B08),
        surface = Color(0xFF1E120D),
        surfaceVariant = Color(0xFF2C1A12)
    ),
    AppTheme(
        id = "copper",
        nameFa = "مسی",
        accent = Color(0xFFE08D57),
        accent2 = Color(0xFFF6C177),
        heroTop = Color(0xFF4A2410),
        heroBottom = Color(0xFF160D07),
        background = Color(0xFF100906),
        surface = Color(0xFF1A110A),
        surfaceVariant = Color(0xFF281812)
    )
)

fun appThemeById(id: String?): AppTheme =
    AppThemes.find { it.id == id } ?: AppThemes[0]

val LocalAppTheme = compositionLocalOf { AppThemes[0] }

@Composable
fun NerkhCheckTheme(appTheme: AppTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppTheme provides appTheme) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = appTheme.accent,
                onPrimary = Color(0xFF14100A),
                secondary = Color(0xFF8B93A7),
                background = appTheme.background,
                surface = appTheme.surface,
                surfaceVariant = appTheme.surfaceVariant,
                onBackground = Color(0xFFF4F6FB),
                onSurface = Color(0xFFF4F6FB),
                error = Color(0xFFF87171)
            ),
            typography = Typography(
                headlineMedium = TextStyle(fontSize = 22.sp),
                titleLarge = TextStyle(fontSize = 19.sp),
                bodyLarge = TextStyle(fontSize = 16.sp)
            ),
            content = content
        )
    }
}
