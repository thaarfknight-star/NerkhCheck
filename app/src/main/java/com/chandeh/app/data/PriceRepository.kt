package com.chandeh.app.data

import android.content.SharedPreferences
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

/**
 * دریافت قیمت‌ها از چند منبع به‌صورت موازی، با ادغام بر اساس اولویت
 * (برای هر نماد، اولین منبعی که آن را داشته باشد استفاده می‌شود —
 *  منابع داخلی اول تا روی اینترنت ملی هم کار کند):
 *
 *  ۱. BRS API (فقط اگر کلید در تنظیمات ثبت شده باشد) — سرور ایران، ارز و طلا
 *  ۲. نوبیتکس (بدون کلید) — سرور ایران، ارز دیجیتال (بیت‌کوین/اتریوم/تتر)
 *  ۳. tala.ir (بدون کلید) — سرور ایران، طلا و بخشی از سکه‌ها
 *  ۴. صفحه‌ی اصلی TGJU — هاست خارجی، همه‌ی نمادها + بیت‌کوین و تتر
 *     (مس هم از همین‌جاست، با تبدیل دلار به تومان)
 *  ۵. وب‌سرویس اسنیپت TGJU — هاست خارجی، ارز و طلا و سکه و نقره
 *     (مس ندارد: قیمت اعشاری دلاری‌اش در این وب‌سرویس قابل خواندن نیست)
 *  ۶. کوین‌گکو (بدون کلید) — هاست خارجی، ارز دیجیتال؛ فقط برای کریپتوهای
 *     جامانده و با تبدیل دلار به تومان
 *
 * نمادهای بدون داده‌ی زنده از کش پر می‌شوند (isStale=true) تا اپ هیچ‌وقت
 * خالی نماند.
 */
class PriceRepository(
    private val brsApiKey: String? = null,
    prefs: SharedPreferences? = null
) {
    private val cache: PriceCache? = prefs?.let { PriceCache(it) }

    suspend fun fetchPrices(): Result<List<PriceItem>> = runCatching {
        // همه‌ی شبکه و کش روی IO؛ فراخوانی از نخ اصلی (مثل viewModelScope)
        // وگرنه NetworkOnMainThreadException می‌دهد و همه‌ی منبع‌ها یک‌جا می‌میرند
        withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, PriceItem>()
        val failures = mutableListOf<String>()
        supervisorScope {
            // هر جاب نام منبعش را هم حمل می‌کند تا اگر همه شکست خوردند،
            // علت خرابی هر منبع در پیام خطا بیاید (تشخیص سریع مشکل)
            val jobs = mutableListOf<Deferred<Pair<String, Result<Map<String, PriceItem>>>>>()
            val key = brsApiKey?.trim().orEmpty()
            if (key.isNotEmpty()) {
                jobs += async {
                    "BRS" to runCatching {
                        parseBrs(HttpClient.get(BRS_API_URL + key)).associateBy { it.code }
                    }
                }
            }
            // نوبیتکس — سرور ایران، بدون نیاز به کلید (۳ درخواست موازی)
            for ((src, appCode) in NOBITEX_MAP) {
                jobs += async {
                    "نوبیتکس" to runCatching {
                        val body = HttpClient.post(
                            NOBITEX_URL,
                            """{"srcCurrency":"$src","dstCurrency":"rls"}"""
                        )
                        val item = parseNobitex(body, appCode)
                        if (item != null) mapOf(item.code to item) else emptyMap()
                    }
                }
            }
            jobs += async {
                "tala.ir" to runCatching {
                    // اعتبارسنجی در برابر کش: فید tala.ir گاهی مقادیر خراب می‌دهد
                    val known = cache?.load().orEmpty()
                    parseTala(HttpClient.get(TALA_PRICE_URL))
                        .filter { talaPlausible(it, known) }
                        .associateBy { it.code }
                }
            }
            jobs += async {
                "TGJU" to runCatching {
                    // پارامتر ضدکش تا همیشه تازه‌ترین صفحه گرفته شود
                    val url = TgjuService.HOMEPAGE_URL + "?_=" + System.currentTimeMillis()
                    parseHomepage(HttpClient.get(url)).associateBy { it.code }
                }
            }
            jobs += async {
                "اسنیپت TGJU" to runCatching {
                    // اسنیپت فقط نمادهای غیرکریپتو را می‌شناسد و نگاشتش
                    // موقعیتی است؛ پس کریپتوها از درخواست حذف می‌شوند.
                    // مس هم حذف می‌شود: قیمت اعشاریِ دلاری‌اش با regex
                    // عدد صحیحِ اسنیپت خوانده نمی‌شود (سطرش جا می‌افتد)؛
                    // مس فقط از صفحه‌ی اصلی و کش می‌آید.
                    val defs = SYMBOLS.filter {
                        it.category != Category.CRYPTO && it.code != "basemetal-copper"
                    }
                    val itemsParam = defs.joinToString(",") { it.code }
                    parseSnippet(HttpClient.get(TgjuService.SNIPPET_URL + itemsParam), defs)
                        .associateBy { it.code }
                }
            }
            for ((name, r) in jobs.awaitAll()) {
                val m = r.getOrNull()
                if (m == null) {
                    failures += "$name: ${shortReason(r.exceptionOrNull())}"
                    continue
                }
                for ((code, item) in m) merged.putIfAbsent(code, item)
            }
        }

        // فاز دوم: کوین‌گکو (خارجی) فقط برای کریپتوهای جامانده؛
        // به نرخ دلار نیاز دارد پس بعد از ادغام فاز اول اجرا می‌شود
        val dollarToman = merged["price_dollar_rl"]?.priceToman
            ?: cache?.load()?.get("price_dollar_rl")?.priceToman
        val missingCrypto = SYMBOLS.any { it.category == Category.CRYPTO && !merged.containsKey(it.code) }
        if (missingCrypto && dollarToman != null && dollarToman > 0) {
            val cgResult = runCatching { parseCoinGecko(HttpClient.get(COINGECKO_URL), dollarToman) }
            cgResult.getOrNull()?.forEach { (code, item) -> merged.putIfAbsent(code, item) }
            if (cgResult.isFailure) failures += "کوین‌گکو: ${shortReason(cgResult.exceptionOrNull())}"
        }

        val cached = cache?.load().orEmpty()
        if (merged.isNotEmpty()) cache?.save(merged.values.toList())

        // ترتیب نهایی همیشه همان ترتیب SYMBOLS
        val final = SYMBOLS.mapNotNull { def ->
            merged[def.code] ?: cached[def.code]
        }
        if (final.isEmpty()) throw allFailedError(failures)
        final
        } // withContext(IO)
    }

    private fun allFailedError(failures: List<String> = emptyList()): Throwable {
        val detail = if (failures.isNotEmpty())
            " (علت‌ها: ${failures.joinToString("؛ ")})" else ""
        return IllegalStateException(
            "دریافت قیمت‌ها ممکن نشد$detail؛ اتصال اینترنت را بررسی کنید و دوباره تلاش کنید"
        )
    }

    /**
     * خلاصه‌ی فارسی علت شکست یک منبع، برای نمایش در پیام خطا
     * (تا مشکل شبکه/سرور سریع تشخیص داده شود)
     */
    private fun shortReason(e: Throwable?): String {
        val cls = e?.javaClass?.simpleName.orEmpty()
        val cl = cls.lowercase()
        val msg = e?.message.orEmpty().lowercase()
        val fa = when {
            "timed out" in msg || "timeout" in msg || "sockettimeout" in cl -> "تایم‌اوت"
            "unable to resolve host" in msg || "unknownhost" in cl -> "عدم دسترسی به سرور"
            "failed to connect" in msg || "connectexception" in cl -> "قطع اتصال به سرور"
            "connection reset" in msg || "econnreset" in msg -> "اتصال ریست شد"
            "network is unreachable" in msg || "enetunreach" in msg -> "قطع اتصال"
            "handshake" in msg || "ssl" in msg || "certificate" in msg || "certpath" in cl -> "خطای امنیتی اتصال"
            "networkonmainthread" in cl -> "باگ داخلی برنامه"
            "http 404" in msg -> "خطای ۴۰۴"
            "http 403" in msg -> "خطای ۴۰۳"
            "http 429" in msg -> "محدودیت نرخ"
            "http 5" in msg -> "خطای سرور"
            else -> "خطا"
        }
        // نام کلاس اکسپشن هم می‌آید تا علت دقیق در گزارش بعدی معلوم باشد
        return if (cls.isNotEmpty()) "$fa ($cls)" else fa
    }

    /**
     * تور ایمنی tala.ir: مقدار باید مثبت، داخل کرانه‌ی مطلق، و (اگر کشی از
     * همین نماد هست) حداکثر ۵۰٪ با آخرین قیمت موفق اختلاف داشته باشد.
     */
    private fun talaPlausible(item: PriceItem, cached: Map<String, PriceItem>): Boolean {
        if (item.priceToman <= 0) return false
        val (lo, hi) = TALA_BOUNDS[item.code] ?: return true
        if (item.priceToman !in lo..hi) return false
        val c = cached[item.code]?.priceToman ?: return true
        if (c <= 0) return true
        return abs(item.priceToman - c).toDouble() / c <= 0.5
    }

    // ------------------------------------------------------------------
    // TGJU — صفحه‌ی اصلی
    // ------------------------------------------------------------------

    /**
     * پارس صفحه‌ی اصلی tgju.org — هر سطر:
     * <tr data-market-nameslug="price_dollar_rl" ...>
     *   <td class="nf">2,442,150</td>
     *   <td class="nf"><span class="high">(3.92%) 92,150</span></td>
     *   ...
     *   <td>۱۴:۱۰:۳۱</td>
     * مبالغ به ریال‌اند -> تقسیم بر ۱۰ برای تومان. جهت تغییر از کلاس high/low.
     */
    fun parseHomepage(html: String): List<PriceItem> {
        // مس جداگانه handled می‌شود (دلار اعشاری) و از مسیر فیات
        // (ریال صحیح) کنار گذاشته شده: آن مسیر برای قیمت اعشاری
        // دلاری هیچ مقداری نمی‌دهد و رفتارش به چیدمان سطر وابسته
        // است؛ handler اختصاصی پایین قطعی است.
        val fiatDefs = SYMBOLS.filter {
            it.category != Category.CRYPTO && it.code != "basemetal-copper"
        }
        val items = fiatDefs.mapNotNull { def ->
            val row = Regex(
                """<tr[^>]*data-market-nameslug="${def.code}".*?</tr>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).find(html)?.value ?: return@mapNotNull null

            val priceRial = Regex("""<td[^>]*>([\d,]+)</td>""")
                .find(row)?.groupValues?.get(1)
                ?.replace(",", "")?.toLongOrNull() ?: return@mapNotNull null

            val changeMatch = Regex(
                """<span class="(high|low)">\(([-\d.]+)%\)\s*(-?[\d,]+)</span>"""
            ).find(row)
            val dir = changeMatch?.groupValues?.get(1)
            val pct = changeMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
            val changeRial = changeMatch?.groupValues?.get(3)
                ?.replace(",", "")?.toLongOrNull() ?: 0L
            val sign = if (dir == "low") -1 else 1

            val time = Regex("""<td[^>]*>([\d۰-۹]{1,2}:[\d۰-۹]{2}(?::[\d۰-۹]{2})?)</td>""")
                .find(row)?.groupValues?.get(1).orEmpty()

            PriceItem(
                code = def.code,
                titleFa = def.titleFa,
                category = def.category,
                priceToman = priceRial / 10,
                changeToman = sign * (changeRial / 10),
                changePercent = sign * abs(pct),
                updatedAt = time
            )
        }
        if (items.size < fiatDefs.size / 2)
            throw IllegalStateException("پاسخ سرور ناقص بود")

        // کریپتوهای زنده‌ی صفحه‌ی اصلی (سطرهای فشرده با data-price)؛
        // بیت‌کوین به دلار است و با نرخ دلار به تومان تبدیل می‌شود، تتر به ریال
        val dollarToman = items.firstOrNull { it.code == "price_dollar_rl" }?.priceToman ?: 0L

        // مس — سطرش به دلار است (هر پوند، مثل 6.476) و قیمتش اعشاری
        // است؛ پس نه در مسیر فیات می‌گنجد نه در اسنیپت. جداگانه خوانده
        // و با نرخ دلار همان صفحه به تومان تبدیل می‌شود؛ واحد وزنی
        // همان پوندِ منبع می‌ماند، فقط ارز عوض می‌شود. هر دو شکل سطر
        // پشتیبانی می‌شود: فشرده (data-price) و کامل (td اعشاری).
        val copperItems = listOfNotNull(
            SYMBOLS.firstOrNull { it.code == "basemetal-copper" }
        ).mapNotNull { def ->
            if (dollarToman <= 0) return@mapNotNull null
            val row = Regex(
                """<tr[^>]*data-market-nameslug="basemetal-copper".*?</tr>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).find(html)?.value ?: return@mapNotNull null
            val priceUsd = Regex("""data-price="([\d.,]+)"""").find(row)
                ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                ?: Regex("""<td[^>]*>([\d,]+\.[\d]+)</td>""").find(row)
                    ?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (priceUsd <= 0) return@mapNotNull null

            val changeMatch = Regex(
                """<span class="(high|low)">\(([-\d.]+)%\)\s*(-?[\d,]+(?:\.[\d]+)?)</span>"""
            ).find(row)
            val dir = changeMatch?.groupValues?.get(1)
            val pct = changeMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
            val changeUsd = changeMatch?.groupValues?.get(3)
                ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            val sign = if (dir == "low") -1 else 1

            val time = Regex("""<td[^>]*>([\d۰-۹]{1,2}:[\d۰-۹]{2}(?::[\d۰-۹]{2})?)</td>""")
                .find(row)?.groupValues?.get(1).orEmpty()

            PriceItem(
                code = def.code,
                titleFa = def.titleFa,
                category = def.category,
                priceToman = (priceUsd * dollarToman).toLong(),
                changeToman = (sign * changeUsd * dollarToman).toLong(),
                changePercent = sign * abs(pct),
                updatedAt = time
            )
        }

        val cryptoItems = CRYPTO_TGJU.mapNotNull { (tgjuSlug, appCode, inUsd) ->
            val def = SYMBOLS.firstOrNull { it.code == appCode } ?: return@mapNotNull null
            val row = Regex(
                """<tr[^>]*data-market-nameslug="$tgjuSlug".*?</tr>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).find(html)?.value ?: return@mapNotNull null
            val priceRaw = Regex("""data-price="([\d.,]+)"""").find(row)
                ?.groupValues?.get(1)?.replace(",", "")
                ?.toDoubleOrNull() ?: return@mapNotNull null

            val changeMatch = Regex(
                """<span class="(high|low)">\(([-\d.]+)%\)\s*(-?[\d,]+)</span>"""
            ).find(row)
            val dir = changeMatch?.groupValues?.get(1)
            val pct = changeMatch?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
            val changeRaw = changeMatch?.groupValues?.get(3)
                ?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            val sign = if (dir == "low") -1 else 1

            val time = Regex("""<td[^>]*>([\d۰-۹]{1,2}:[\d۰-۹]{2}(?::[\d۰-۹]{2})?)</td>""")
                .find(row)?.groupValues?.get(1).orEmpty()

            val (priceToman, changeToman) = if (inUsd) {
                if (dollarToman <= 0) return@mapNotNull null
                (priceRaw * dollarToman).toLong() to (sign * changeRaw * dollarToman).toLong()
            } else {
                // ریال -> تومان
                (priceRaw / 10).toLong() to (sign * changeRaw / 10).toLong()
            }
            PriceItem(
                code = def.code,
                titleFa = def.titleFa,
                category = def.category,
                priceToman = priceToman,
                changeToman = changeToman,
                changePercent = sign * abs(pct),
                updatedAt = time
            )
        }
        return items + copperItems + cryptoItems
    }

    // ------------------------------------------------------------------
    // TGJU — وب‌سرویس اسنیپت
    // ------------------------------------------------------------------

    /**
     * پارس وب‌سرویس اسنیپت (جایگزین) — نمونه سطر:
     *   | سکه امامی | 2,465,050,000 | (2.49%) 60,000,000 | ۱۴:۱۰:۴۹ |
     * نگاشت موقعیتی است: ترتیب سطرها همان ترتیب defs درخواستی است.
     */
    fun parseSnippet(body: String, defs: List<SymbolDef>): List<PriceItem> {
        val rowRegex = Regex(
            """\|\s*([^|]+?)\s*\|\s*([\d,]+)\s*\|\s*\(?\s*(-?[\d.]+)\s*%\s*\)?\s*(-?[\d,]+)\s*\|\s*([^|]*?)\s*\|"""
        )
        val rows = rowRegex.findAll(body).toList()
        if (rows.isEmpty()) throw IllegalStateException("پاسخ سرور قابل خواندن نبود")
        return rows.take(defs.size).mapIndexed { index, m ->
            val def = defs[index]
            val priceRial = m.groupValues[2].replace(",", "").toLong()
            val pct = m.groupValues[3].toDouble()
            val changeRial = m.groupValues[4].replace(",", "").toLong()
            val signedPct = if (changeRial < 0) -abs(pct) else abs(pct)
            PriceItem(
                code = def.code,
                titleFa = def.titleFa,
                category = def.category,
                priceToman = priceRial / 10,
                changeToman = changeRial / 10,
                changePercent = signedPct,
                updatedAt = m.groupValues[5].trim()
            )
        }
    }

    // ------------------------------------------------------------------
    // tala.ir — سرور ایران (طلا و سکه)
    // ------------------------------------------------------------------

    /**
     * پارس خروجی https://www.tala.ir/ajax/price — نمونه:
     *   "gold_18k": {"v": "24,988,900", "d": "9,234 (0.04%)",
     *                "jdate": "14:59 1405/07/07", ...}
     *
     * نکته‌های مهم (بررسی‌شده با داده‌ی واقعی):
     *  - مقادیر v همین‌جا به تومان‌اند (تقسیم لازم نیست).
     *  - گاهی علامت منفیِ تغییر روزانه به اولِ v چسبیده (مثل "-176,696,700")؛
     *    مقدار واقعی قدرمطلق است.
     *  - فید سکه‌ی امامی/بهار (sekke-jad/gad) خراب است (خود سایت هم «-» نشان
     *    می‌دهد و مقدارش ~۳۰٪ با بازار اختلاف دارد)؛ پس نگاشت نمی‌شوند و
     *    از TGJU یا کش می‌آیند.
     */
    fun parseTala(json: String): List<PriceItem> {
        val root = JSONObject(json)
        val defs = SYMBOLS.associateBy { it.code }
        val out = mutableListOf<PriceItem>()
        for ((talaKey, appCode) in TALA_CODE_MAP) {
            val def = defs[appCode] ?: continue
            val section = if (talaKey.startsWith("gold_")) "gold" else "sekke"
            val obj = root.optJSONObject(section)?.optJSONObject(talaKey) ?: continue
            val priceToman = obj.optString("v", "").replace(",", "")
                .toLongOrNull()?.let { abs(it) } ?: continue
            if (priceToman <= 0) continue
            // d نمونه: "-100,000 (0.05%)"
            val d = obj.optString("d", "")
            val dm = Regex("""(-?[\d,]+)\s*\(([-\d.]+)%\)""").find(d)
            val changeToman = dm?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L
            val pct = dm?.groupValues?.get(2)?.toDoubleOrNull() ?: 0.0
            val sign = if (changeToman < 0 || pct < 0) -1 else 1
            val time = obj.optString("jdate", "").split(" ").firstOrNull().orEmpty()
            out.add(
                PriceItem(
                    code = def.code,
                    titleFa = def.titleFa,
                    category = def.category,
                    priceToman = priceToman,
                    changeToman = changeToman,
                    changePercent = sign * abs(pct),
                    updatedAt = time
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------------
    // BRS API — سرور ایران (همه‌ی نمادها، نیازمند کلید)
    // ------------------------------------------------------------------

    /**
     * پارس خروجی https://api.brsapi.ir/Market/Gold_Currency.php?key=...
     * مبالغ همین‌جا به تومان‌اند (تبدیل لازم نیست).
     */
    fun parseBrs(json: String): List<PriceItem> {
        val root = JSONObject(json)
        if (!root.optBoolean("successful", true))
            throw IllegalStateException(
                root.optString("message_error", "خطا در وب‌سرویس BRS").ifEmpty { "خطا در وب‌سرویس BRS" }
            )
        val defs = SYMBOLS.associateBy { it.code }
        val bySymbol = LinkedHashMap<String, JSONObject>()
        for (arrName in listOf("gold", "currency")) {
            val arr = root.optJSONArray(arrName) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                bySymbol.putIfAbsent(o.optString("symbol"), o)
            }
        }
        if (bySymbol.isEmpty()) throw IllegalStateException("پاسخ BRS خالی بود")
        return BRS_CODE_MAP.mapNotNull { (brsSym, appCode) ->
            val o = bySymbol[brsSym] ?: return@mapNotNull null
            val def = defs[appCode] ?: return@mapNotNull null
            val price = o.optMoney("price").takeIf { it > 0 } ?: return@mapNotNull null
            PriceItem(
                code = def.code,
                titleFa = def.titleFa,
                category = def.category,
                priceToman = price,
                changeToman = o.optMoney("change_value"),
                changePercent = o.optDoubleFlexible("change_percent"),
                updatedAt = o.optString("time", "")
            )
        }
    }

    /** خواندن عدد که ممکن است Number یا رشته‌ی «1,234» باشد */
    private fun JSONObject.optMoney(name: String): Long = when (val v = opt(name)) {
        is Number -> v.toLong()
        is String -> v.replace(",", "").toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun JSONObject.optDoubleFlexible(name: String): Double = when (val v = opt(name)) {
        is Number -> v.toDouble()
        is String -> v.replace(",", "").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    // ------------------------------------------------------------------
    // نوبیتکس — سرور ایران، بدون نیاز به کلید (ارز دیجیتال)
    // ------------------------------------------------------------------

    /**
     * پارس پاسخ POST https://apiv2.nobitex.ir/market/stats
     * نمونه: {"status":"ok","stats":{"btc-rls":{"latest":"212300000000",
     *          "dayChange":"0.55", ...}}}
     * مبالغ به ریال‌اند -> تقسیم بر ۱۰ برای تومان.
     */
    fun parseNobitex(json: String, appCode: String): PriceItem? {
        val root = JSONObject(json)
        if (root.optString("status") != "ok") return null
        val stats = root.optJSONObject("stats") ?: return null
        val keys = stats.keys()
        if (!keys.hasNext()) return null
        val s = stats.optJSONObject(keys.next()) ?: return null
        val latestRial = s.optString("latest").toDoubleOrNull() ?: return null
        if (latestRial <= 0) return null
        val def = SYMBOLS.firstOrNull { it.code == appCode } ?: return null
        val pct = s.optString("dayChange").toDoubleOrNull() ?: 0.0
        val priceToman = (latestRial / 10).toLong()
        return PriceItem(
            code = def.code,
            titleFa = def.titleFa,
            category = def.category,
            priceToman = priceToman,
            changeToman = (priceToman * pct / 100).toLong(),
            changePercent = pct,
            updatedAt = ""
        )
    }

    // ------------------------------------------------------------------
    // کوین‌گکو — هاست خارجی، بدون نیاز به کلید (ارز دیجیتال)
    // ------------------------------------------------------------------

    /**
     * پارس پاسخ CoinGecko — قیمت‌ها به دلارند و با نرخ دلار به تومان
     * تبدیل می‌شوند. فقط برای کریپتوهای جامانده از منابع دیگر صدا زده می‌شود.
     */
    fun parseCoinGecko(json: String, dollarToman: Long): Map<String, PriceItem> {
        if (dollarToman <= 0) return emptyMap()
        val root = JSONObject(json)
        val defs = SYMBOLS.associateBy { it.code }
        val out = LinkedHashMap<String, PriceItem>()
        for ((cgId, appCode) in COINGECKO_MAP) {
            val o = root.optJSONObject(cgId) ?: continue
            val usd = o.optDouble("usd", 0.0)
            if (usd <= 0) continue
            val def = defs[appCode] ?: continue
            val pct = o.optDouble("usd_24h_change", 0.0)
            val priceToman = (usd * dollarToman).toLong()
            out[appCode] = PriceItem(
                code = def.code,
                titleFa = def.titleFa,
                category = def.category,
                priceToman = priceToman,
                changeToman = (priceToman * pct / 100).toLong(),
                changePercent = pct,
                updatedAt = ""
            )
        }
        return out
    }

    companion object {
        /** سایت طلا (اتحادیه) — سرور ایران، بدون نیاز به کلید */
        const val TALA_PRICE_URL = "https://www.tala.ir/ajax/price"

        /** وب‌سرویس BRS — سرور ایران، نیازمند کلید رایگان */
        const val BRS_API_URL = "https://api.brsapi.ir/Market/Gold_Currency.php?key="

        /** نوبیتکس — سرور ایران، بدون نیاز به کلید (ارز دیجیتال) */
        const val NOBITEX_URL = "https://apiv2.nobitex.ir/market/stats"

        /** کوین‌گکو — هاست خارجی، بدون نیاز به کلید (ارز دیجیتال، به دلار) */
        const val COINGECKO_URL =
            "https://api.coingecko.com/api/v3/simple/price" +
                "?ids=bitcoin,ethereum,tether&vs_currencies=usd&include_24hr_change=true"

        /** نگاشت ارز نوبیتکس (srcCurrency) به کد نماد برنامه */
        private val NOBITEX_MAP = listOf(
            "btc" to "btc",
            "eth" to "eth",
            "usdt" to "usdt"
        )

        /**
         * کریپتوهای زنده‌ی صفحه‌ی اصلی TGJU: (نام‌اسلاگ، کد برنامه، آیا به دلار است؟)
         * - بیت‌کوین به دلار است و با نرخ دلار به تومان تبدیل می‌شود
         * - تتر به ریال است (مثل فیات، تقسیم بر ۱۰)
         */
        private val CRYPTO_TGJU = listOf(
            Triple("crypto-bitcoin", "btc", true),
            Triple("crypto-tether", "usdt", false)
        )

        /** نگاشت شناسه‌ی کوین‌گکو به کد نماد برنامه */
        private val COINGECKO_MAP = mapOf(
            "bitcoin" to "btc",
            "ethereum" to "eth",
            "tether" to "usdt"
        )

        /** نگاشت کلیدهای tala.ir به کد نمادهای برنامه.
         *  سکه‌ی امامی/بهار عمداً نیست: فیدشان خراب است (خود tala.ir هم «-»
         *  نشان می‌دهد) و مقدارشان ~۳۰٪ با بازار اختلاف دارد. */
        private val TALA_CODE_MAP = mapOf(
            "gold_18k" to "geram18",
            "gold_24k" to "geram24",
            "gold_bazartehran" to "mesghal",
            "sekke-nim" to "nim",
            "sekke-rob" to "rob",
            "sekke-grm" to "gerami"
        )

        /** کرانه‌ی قابل‌قبول قیمت tala.ir به تومان — تور ایمنی در برابر
         *  باگ‌های فیدشان (مثل چسبیدن علامت یا واحد اشتباه) */
        private val TALA_BOUNDS = mapOf(
            "geram18" to (5_000_000L to 120_000_000L),
            "geram24" to (7_000_000L to 160_000_000L),
            "mesghal" to (20_000_000L to 520_000_000L),
            "nim" to (30_000_000L to 600_000_000L),
            "rob" to (15_000_000L to 300_000_000L),
            "gerami" to (8_000_000L to 180_000_000L)
        )

        /** نگاشت نمادهای BRS به کد نمادهای برنامه */
        private val BRS_CODE_MAP = mapOf(
            "USD" to "price_dollar_rl",
            "EUR" to "price_eur",
            "GBP" to "price_gbp",
            "AED" to "price_aed",
            "TRY" to "price_try",
            "CHF" to "price_chf",
            "CNY" to "price_cny",
            "JPY" to "price_jpy",
            "CAD" to "price_cad",
            "AUD" to "price_aud",
            "IR_GOLD_18K" to "geram18",
            "IR_GOLD_24K" to "geram24",
            "IR_GOLD_MELTED" to "mesghal",
            "IR_COIN_EMAMI" to "sekee",
            "IR_COIN_BAHAR" to "sekeb",
            "IR_COIN_HALF" to "nim",
            "IR_COIN_QUARTER" to "rob",
            "IR_COIN_1G" to "gerami"
        )
    }
}
