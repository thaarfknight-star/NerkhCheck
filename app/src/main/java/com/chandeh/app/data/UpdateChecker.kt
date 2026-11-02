package com.chandeh.app.data

import android.content.SharedPreferences
import com.chandeh.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * موتور آپدیت دوطبقه:
 *
 * ۱. «فایل آپدیت» (update.json روی گیت‌هاب): محتوای قابل‌تعویض مثل تم‌های
 *    جدید. خود برنامه دانلودش می‌کند و همان‌لحظه اعمال می‌کند —
 *    بدون دانلود APK و بدون نصب مجدد.
 * ۲. «آپدیت کد» (ریلیز گیت‌هاب): وقتی خود کد عوض شده باشد، چاره‌ای جز
 *    APK جدید نیست (محدودیت اندروید)؛ ولی دانلود و شروع نصب هم داخل
 *    خود برنامه انجام می‌شود، نه در مرورگر.
 *
 * بررسی با هر بار باز شدن برنامه انجام می‌شود (حداکثر ساعتی یک‌بار) و
 * دکمه‌ی «بررسی آپدیت» در تنظیمات هم چک فوری می‌کند؛ دیگر نیازی به
 * پاک کردن داده‌های برنامه نیست.
 *
 * نسخه‌ی جدید اول از فید استاتیک update-feed.json خوانده می‌شود (روی
 * raw.githubusercontent و jsdelivr — بدون سقف نرخ API گیت‌هاب) و فقط
 * اگر فید در دسترس نباشد، API گیت‌هاب پشتیبان است. فید را ورک‌فلوی
 * انتشار موقع هر تگ به‌روز می‌کند. اگر هیچ‌کدام جواب ندهند، نتیجه
 * CheckFailed است تا «به‌روز است» با «چک انجام نشد» قاطی نشود.
 */
object UpdateChecker {
    private const val RELEASES_URL =
        "https://api.github.com/repos/thaarfknight-star/NerkhCheck/releases/latest"
    private const val FALLBACK_URL =
        "https://github.com/thaarfknight-star/NerkhCheck/releases"
    private const val PREF_LAST_CHECK_MS = "update_last_check_ms"
    private const val PREF_DISMISSED = "update_dismissed_version"

    /** حداکثر فاصله‌ی بین دو چک خودکار */
    private const val CHECK_THROTTLE_MS = 60 * 60 * 1000L

    /** فایل آپدیت محتوا؛ دومی جایگزین اولی است اگر در دسترس نباشد */
    private val CONTENT_URLS = listOf(
        "https://raw.githubusercontent.com/thaarfknight-star/NerkhCheck/main/update.json",
        "https://cdn.jsdelivr.net/gh/thaarfknight-star/NerkhCheck@main/update.json"
    )

    /** فید استاتیک نسخه؛ دومی جایگزین اولی است اگر در دسترس نباشد */
    private val FEED_URLS = listOf(
        "https://raw.githubusercontent.com/thaarfknight-star/NerkhCheck/main/update-feed.json",
        "https://cdn.jsdelivr.net/gh/thaarfknight-star/NerkhCheck@main/update-feed.json"
    )

    data class ApkUpdateInfo(
        val version: String,
        val pageUrl: String,
        /** اگر ریلیز فایل APK داشته باشد، لینک مستقیم دانلودش */
        val apkUrl: String?
    )

    /**
     * نتیجه‌ی بررسی نسخه‌ی جدید:
     * - Available: نسخه‌ی تازه‌تر (و ردنشده) پیدا شد
     * - UpToDate: نسخه‌ی تازه‌ای نیست (یا چک داخل بازه‌ی ساعتی است)
     * - CheckFailed: خودِ چک انجام نشد (نه اینکه برنامه به‌روز باشد)
     */
    sealed interface ApkCheckResult {
        data class Available(val info: ApkUpdateInfo) : ApkCheckResult
        data object UpToDate : ApkCheckResult
        data object CheckFailed : ApkCheckResult
    }

    /**
     * بررسی نسخه‌ی جدید کد. throttle ساعتی دارد مگر با force=true
     * (دکمه‌ی «بررسی آپدیت» در تنظیمات).
     */
    suspend fun checkApkUpdate(
        prefs: SharedPreferences,
        force: Boolean = false
    ): ApkCheckResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(PREF_LAST_CHECK_MS, 0L) < CHECK_THROTTLE_MS) {
            return@withContext ApkCheckResult.UpToDate
        }
        // ۱. فید استاتیک (بدون سقف نرخ API)
        val feed = fetchFeed()
        if (feed != null) {
            prefs.edit().putLong(PREF_LAST_CHECK_MS, now).apply()
            return@withContext evaluateRemote(feed.version, feed.apkUrl, feed.pageUrl, prefs)
        }
        // ۲. پشتیبان: API گیت‌هاب (بدون احراز هویت؛ ممکن است سقف نرخ بخورد)
        try {
            val body = HttpClient.get(RELEASES_URL)
            val json = JSONObject(body)
            val remote = json.optString("tag_name", "")
                .trim().removePrefix("v").removePrefix("V")
            if (remote.isEmpty()) return@withContext ApkCheckResult.CheckFailed

            prefs.edit().putLong(PREF_LAST_CHECK_MS, now).apply()

            val pageUrl = json.optString("html_url", "").ifEmpty { FALLBACK_URL }
            val apkUrl = json.optJSONArray("assets")?.let { arr ->
                (0 until arr.length())
                    .map { arr.getJSONObject(it).optString("browser_download_url", "") }
                    .firstOrNull { it.endsWith(".apk", ignoreCase = true) }
                    ?.ifEmpty { null }
            }
            evaluateRemote(remote, apkUrl, pageUrl, prefs)
        } catch (_: Exception) {
            ApkCheckResult.CheckFailed
        }
    }

    /** مقایسه با نسخه‌ی نصب‌شده و احترام به «بعداً»ی کاربر */
    private fun evaluateRemote(
        remote: String,
        apkUrl: String?,
        pageUrl: String,
        prefs: SharedPreferences
    ): ApkCheckResult {
        val local = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")
        val dismissed = prefs.getString(PREF_DISMISSED, null)
        return if (isNewer(remote, local) && dismissed != remote) {
            ApkCheckResult.Available(ApkUpdateInfo(remote, pageUrl, apkUrl))
        } else {
            ApkCheckResult.UpToDate
        }
    }

    /** خواندن فید استاتیک؛ null یعنی فید در دسترس نیست */
    private fun fetchFeed(): FeedInfo? {
        for (u in FEED_URLS) {
            val body = runCatching { HttpClient.get(u) }.getOrNull() ?: continue
            val info = runCatching {
                val json = JSONObject(body)
                val version = json.optString("version", "")
                    .trim().removePrefix("v").removePrefix("V")
                if (version.isEmpty()) return@runCatching null
                FeedInfo(
                    version,
                    json.optString("apkUrl", "").takeIf { it.isNotEmpty() },
                    json.optString("pageUrl", "").ifEmpty { FALLBACK_URL }
                )
            }.getOrNull() ?: continue
            return info
        }
        return null
    }

    private data class FeedInfo(
        val version: String,
        val apkUrl: String?,
        val pageUrl: String
    )

    /**
     * فایل آپدیت محتوا را می‌گیرد و همان‌لحظه اعمال می‌کند.
     * برمی‌گرداند چند مورد «واقعاً جدید» اضافه شد (۰ = چیزی تازه نبود).
     */
    suspend fun checkContentUpdate(prefs: SharedPreferences): Int = withContext(Dispatchers.IO) {
        try {
            var body: String? = null
            for (u in CONTENT_URLS) {
                body = runCatching { HttpClient.get(u) }.getOrNull()
                if (body != null) break
            }
            body ?: return@withContext 0
            ThemeStore.applyUpdate(prefs, body)
        } catch (_: Exception) {
            0
        }
    }

    /** کاربر «بعداً» را زده؛ تا ریلیز بعدی دیگر آلارم نده */
    fun dismiss(prefs: SharedPreferences, version: String) {
        prefs.edit().putString(PREF_DISMISSED, version).apply()
    }

    /**
     * دانلود فایل (APK) با گزارش پیشرفت ۰ تا ۱۰۰.
     * false یعنی دانلود ناقص/ناموفق بود.
     */
    suspend fun downloadFile(
        url: String,
        dest: File,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            var lastPct = -1
            HttpClient.download(url, dest) { done, total ->
                if (total > 0) {
                    val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                    if (pct != lastPct) {
                        lastPct = pct
                        // کال‌بک روی نخ اصلی تا به‌روزرسانی UI امن باشد
                        withContext(Dispatchers.Main) { onProgress(pct) }
                    }
                }
            }
            dest.exists() && dest.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /** مقایسه‌ی معنایی نسخه‌ها: 1.0.2 بزرگ‌تر از 1.0.1 است */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".")
        val l = local.split(".")
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val b = l.getOrNull(i)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            if (a != b) return a > b
        }
        return false
    }
}
