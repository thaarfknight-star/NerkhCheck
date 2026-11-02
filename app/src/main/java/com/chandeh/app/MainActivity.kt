package com.chandeh.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chandeh.app.data.Prefs
import com.chandeh.app.data.ThemeStore
import com.chandeh.app.data.UpdateChecker
import com.chandeh.app.ui.PricesViewModel
import com.chandeh.app.ui.screens.ConverterScreen
import com.chandeh.app.ui.screens.PriceListScreen
import com.chandeh.app.ui.screens.SettingsScreen
import com.chandeh.app.ui.theme.AppTheme
import com.chandeh.app.ui.theme.NerkhCheckTheme
import com.chandeh.app.util.toFaDigits
import com.chandeh.app.widget.WidgetUpdateWorker
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: PricesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // به‌روزرسانی دوره‌ای ویجت در پس‌زمینه
        WidgetUpdateWorker.schedule(this)
        // تمام‌صفحه: محتوا زیر نوار وضعیت و ناوبری کشیده می‌شود
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            val prefs = remember { getSharedPreferences(Prefs.NAME, MODE_PRIVATE) }
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            var allThemes by remember { mutableStateOf(ThemeStore.mergedThemes(prefs)) }
            var theme by remember {
                mutableStateOf(ThemeStore.themeById(prefs, prefs.getString(Prefs.KEY_THEME, null)))
            }
            var brsKey by remember {
                mutableStateOf(prefs.getString(Prefs.KEY_BRS_API, "").orEmpty())
            }
            var updateInfo by remember { mutableStateOf<UpdateChecker.ApkUpdateInfo?>(null) }
            var manualStatus by remember { mutableStateOf<String?>(null) }
            var checkingUpdates by remember { mutableStateOf(false) }

            fun refreshThemes() {
                allThemes = ThemeStore.mergedThemes(prefs)
                theme = ThemeStore.themeById(prefs, prefs.getString(Prefs.KEY_THEME, null))
            }

            // موتور آپدیت دوطبقه:
            // ۱. فایل آپدیت محتوا (تم‌های جدید و...) — خود برنامه می‌گیرد و
            //    همان‌لحظه اعمال می‌کند؛ بدون دانلود APK و بدون نصب مجدد
            // ۲. آپدیت کد — نسخه‌ی جدید ریلیز؛ دانلود و نصب هم داخل خود برنامه
            LaunchedEffect(Unit) {
                if (UpdateChecker.checkContentUpdate(prefs) > 0) refreshThemes()
                when (val apkResult = UpdateChecker.checkApkUpdate(prefs)) {
                    is UpdateChecker.ApkCheckResult.Available -> updateInfo = apkResult.info
                    else -> {}
                }
            }

            /** بررسی دستی از دکمه‌ی «بررسی آپدیت» در تنظیمات */
            fun manualCheck() {
                if (checkingUpdates) return
                checkingUpdates = true
                manualStatus = null
                scope.launch {
                    val newCount = UpdateChecker.checkContentUpdate(prefs)
                    val apkResult = UpdateChecker.checkApkUpdate(prefs, force = true)
                    if (newCount > 0) refreshThemes()
                    manualStatus = buildString {
                        if (newCount > 0) append("✓ $newCount مورد جدید از فایل آپدیت اعمال شد. ")
                        when (apkResult) {
                            is UpdateChecker.ApkCheckResult.Available -> {
                                append("نسخه‌ی ${apkResult.info.version} منتشر شده است.")
                                updateInfo = apkResult.info
                            }
                            is UpdateChecker.ApkCheckResult.CheckFailed ->
                                append("بررسی آپدیت انجام نشد؛ اتصال اینترنت را بررسی کنید و دوباره امتحان کنید.")
                            is UpdateChecker.ApkCheckResult.UpToDate ->
                                append("برنامه به‌روز است.")
                        }
                    }.toFaDigits()
                    checkingUpdates = false
                }
            }

            NerkhCheckTheme(appTheme = theme) {
                // کل رابط راست‌چین و فارسی
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // Surface در ریشه تا رنگ محتوای پیش‌فرض (onSurface) به همه‌ی
                    // متن‌ها برسد؛ وگرنه متن‌های بدون رنگ صریح مشکی می‌مانند
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(
                            vm = vm,
                            theme = theme,
                            allThemes = allThemes,
                            onThemeChange = {
                                theme = it
                                prefs.edit().putString(Prefs.KEY_THEME, it.id).apply()
                            },
                            brsKey = brsKey,
                            onBrsKeyChange = {
                                brsKey = it
                                prefs.edit().putString(Prefs.KEY_BRS_API, it).apply()
                                vm.refresh()
                            },
                            updateStatus = manualStatus,
                            checkingUpdate = checkingUpdates,
                            onCheckUpdate = ::manualCheck
                        )
                    }
                    val info = updateInfo
                    if (info != null) {
                        var progress by remember(info) { mutableStateOf<Int?>(null) }
                        var failed by remember(info) { mutableStateOf(false) }
                        fun dismiss() {
                            UpdateChecker.dismiss(prefs, info.version)
                            updateInfo = null
                        }
                        fun downloadAndInstall(apkUrl: String) {
                            scope.launch {
                                val dest = File(ctx.cacheDir, "nerkhcheck-update.apk")
                                val ok = UpdateChecker.downloadFile(apkUrl, dest) { pct ->
                                    progress = pct
                                }
                                if (ok) {
                                    dismiss()
                                    installApk(ctx, dest)
                                } else {
                                    progress = null
                                    failed = true
                                }
                            }
                        }
                        // برگشت از صفحه‌ی اجازه‌ی «نصب برنامه‌های ناشناس» —
                        // اگر اجازه داده شده، دانلود و نصب خودکار ادامه پیدا می‌کند
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.StartActivityForResult()
                        ) {
                            if (hasInstallPermission(ctx)) {
                                val url = info.apkUrl
                                if (url != null) downloadAndInstall(url)
                            } else {
                                Toast.makeText(
                                    ctx,
                                    "برای نصب آپدیت، اجازه‌ی «نصب برنامه‌های ناشناس» لازم است",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        AlertDialog(
                            onDismissRequest = { dismiss() },
                            title = { Text("نسخه‌ی جدید منتشر شد") },
                            text = {
                                Column {
                                    Text("نسخه‌ی ${info.version.toFaDigits()} از NerkhCheck منتشر شده است.")
                                    when {
                                        progress != null -> {
                                            Spacer(Modifier.height(12.dp))
                                            LinearProgressIndicator(
                                                progress = { (progress ?: 0) / 100f },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("در حال دانلود... ٪${progress.toString().toFaDigits()}")
                                        }
                                        failed -> {
                                            Spacer(Modifier.height(8.dp))
                                            Text("دانلود ناموفق بود؛ از صفحه‌ی ریلیز دریافت کنید.")
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val apkUrl = info.apkUrl
                                        if (apkUrl != null && progress == null && !failed) {
                                            // اول اجازه‌ی نصب، بعد دانلود و نصب
                                            if (needsInstallPermission(ctx)) {
                                                permissionLauncher.launch(unknownSourcesIntent(ctx))
                                            } else {
                                                downloadAndInstall(apkUrl)
                                            }
                                        } else {
                                            dismiss()
                                            openReleasePage(info.pageUrl, ctx)
                                        }
                                    },
                                    enabled = progress == null
                                ) {
                                    Text(if (info.apkUrl != null && !failed) "دانلود و نصب" else "مشاهده")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { dismiss() }) { Text("بعداً") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    vm: PricesViewModel,
    theme: AppTheme,
    allThemes: List<AppTheme>,
    onThemeChange: (AppTheme) -> Unit,
    brsKey: String,
    onBrsKeyChange: (String) -> Unit,
    updateStatus: String?,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("قیمت‌ها") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.CurrencyExchange, contentDescription = null) },
                    label = { Text("تبدیل") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("تنظیمات") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> PriceListScreen(
                    items = state.items,
                    isLoading = state.isLoading,
                    error = state.error,
                    lastFetchAt = state.lastFetchAt,
                    hasStale = state.hasStale,
                    onRefresh = vm::refresh
                )
                1 -> ConverterScreen(items = state.items)
                else -> SettingsScreen(
                    theme = theme,
                    allThemes = allThemes,
                    onThemeChange = onThemeChange,
                    brsKey = brsKey,
                    onBrsKeyChange = onBrsKeyChange,
                    updateStatus = updateStatus,
                    checkingUpdate = checkingUpdate,
                    onCheckUpdate = onCheckUpdate
                )
            }
        }
    }
}

/** شروع نصب APK دانلودشده، داخل خود برنامه */
/** آیا برای نصب APK اجازه‌ی «نصب برنامه‌های ناشناس» لازم است؟ (اندروید ۸ به بالا) */
private fun needsInstallPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !ctx.packageManager.canRequestPackageInstalls()

private fun hasInstallPermission(ctx: Context): Boolean = !needsInstallPermission(ctx)

/** اینتنت صفحه‌ی اجازه‌ی «نصب برنامه‌های ناشناس» برای همین برنامه */
private fun unknownSourcesIntent(ctx: Context): Intent =
    Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${ctx.packageName}")
    )

private fun installApk(ctx: Context, apkFile: File) {
    if (needsInstallPermission(ctx)) {
        Toast.makeText(
            ctx,
            "برای نصب، اجازه‌ی «نصب برنامه‌های ناشناس» را بدهید",
            Toast.LENGTH_LONG
        ).show()
        ctx.startActivity(
            unknownSourcesIntent(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(intent)
}

/** باز کردن صفحه‌ی ریلیز گیت‌هاب در مرورگر (وقتی ریلیز APK ندارد) */
private fun openReleasePage(url: String, ctx: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(intent)
}
