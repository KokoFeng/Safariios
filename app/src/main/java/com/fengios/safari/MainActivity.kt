package com.fengios.safari

import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.RelativeSizeSpan
import android.util.Patterns
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : Activity() {

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val HOME = "https://www.google.com"   // mesin pencari: Google

    private val h = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var style = 27
    private lateinit var root: FrameLayout
    private lateinit var webHolder: FrameLayout
    private val tabs = mutableListOf<Tab>()
    private var cur = 0
    private var topInset = 0
    private var navInset = 0
    private var imeInset = 0
    private var bars: Bars? = null
    private var collapsed = false
    private var editing = false
    private var overlay: View? = null
    private var gridCol: LinearLayout? = null
    private var countLabel: TextView? = null
    private var sheetLayer: View? = null
    private var animUntil = 0L
    private var lastScrollY = -1
    private var lastRefresh = 0L
    private var fileCb: ValueCallback<Array<Uri>>? = null

    inner class Tab {
        lateinit var web: WebView
        var title = ""
        var url = ""
        var progress = 100
        var thumb: Bitmap? = null
        var desktop = false
        var zoom = 100
    }

    inner class Bars {
        val container = FrameLayout(this@MainActivity)
        lateinit var padView: View
        lateinit var pill: FrameLayout
        lateinit var host: TextView
        lateinit var edit: EditText
        lateinit var aa: TextView
        lateinit var reload: IconView
        lateinit var cancel: TextView
        lateinit var progress: View
        lateinit var addrMover: View
        lateinit var toolMover: View
        lateinit var toolHide: View
        val btn = mutableMapOf<String, IconView>()
    }

    private val dark: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val fg: Int get() = if (dark) Color.WHITE else Color.BLACK
    private val bg: Int get() = if (dark) Color.BLACK else Color.WHITE
    private val blue: Int get() = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
    private val fillCol: Int get() = if (dark) 0x3D767680 else 0x1F787880
    private val tab: Tab get() = tabs[cur]
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    // ------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("safari", MODE_PRIVATE)
        style = prefs.getInt("style", 27)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false

        root = FrameLayout(this)
        setContentView(root)
        webHolder = FrameLayout(this)
        root.addView(webHolder, FrameLayout.LayoutParams(MP, MP))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, ins ->
            topInset = ins.getInsets(WindowInsetsCompat.Type.systemBars()).top
            navInset = ins.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            imeInset = ins.getInsets(WindowInsetsCompat.Type.ime()).bottom
            applyInsets()
            ins
        }

        val saved = prefs.getString("tabs", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
        if (saved.isEmpty()) addTab(HOME) else saved.forEach { addTab(it) }
        cur = prefs.getInt("cur", 0).coerceIn(0, tabs.size - 1)
        buildUi()
        selectTab(cur)
        handleIntent(intent)

        ContextCompat.registerReceiver(
            this, dlReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(i: Intent?) { i?.data?.let { newTab(it.toString()) } }

    override fun onConfigurationChanged(c: Configuration) { super.onConfigurationChanged(c); buildUi() }

    override fun onResume() { super.onResume(); tab.web.onResume(); h.post(ticker) }
    override fun onPause() {
        super.onPause()
        h.removeCallbacks(ticker)
        tab.web.onPause()
        prefs.edit().putString("tabs", tabs.joinToString("\n") { it.web.url ?: it.url }).putInt("cur", cur).apply()
    }
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(dlReceiver) } catch (e: Exception) { } }

    override fun onBackPressed() {
        when {
            sheetLayer != null -> hideSheet()
            overlay != null -> hideOverview()
            editing -> endEdit()
            tab.web.canGoBack() -> tab.web.goBack()
            else -> super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 77) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
            fileCb = null
        } else super.onActivityResult(req, res, data)
    }

    // refresh blur backdrop saat scroll / animasi
    private val ticker = object : Runnable {
        override fun run() {
            val sy = tabs.getOrNull(cur)?.web?.scrollY ?: 0
            val now = SystemClock.uptimeMillis()
            if (sy != lastScrollY || now - lastRefresh > 700 || now < animUntil) {
                lastScrollY = sy; lastRefresh = now
                GlassView.all.toList().forEach { it.refresh() }
            }
            h.postDelayed(this, 66)
        }
    }

    // ------------------------------------------------------------ UI build
    private fun buildUi() {
        root.setBackgroundColor(bg)
        window.setBackgroundDrawable(ColorDrawable(bg))
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        bars?.let { root.removeView(it.container) }
        tabs.forEach { it.web.setBackgroundColor(bg) }
        val b = if (style == 27) build27() else build18()
        bars = b
        collapsed = false
        editing = false
        root.addView(b.container, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))
        applyInsets()
        updateUi()
    }

    private fun applyInsets() {
        (webHolder.layoutParams as FrameLayout.LayoutParams).topMargin = topInset
        webHolder.requestLayout()
        val b = bars ?: return
        val pad = if (imeInset > navInset) imeInset else navInset
        if (style == 27) b.padView.setPadding(0, 0, 0, pad + dp(8)) else b.padView.setPadding(0, dp(8), 0, pad)
    }

    private fun roundClip(v: View, r: Int) {
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) { o.setRoundRect(0, 0, view.width, view.height, r.toFloat()) }
        }
    }

    private fun fillPill(b: Bars, pill: FrameLayout) {
        b.pill = pill
        b.progress = View(this).apply { setBackgroundColor(blue); alpha = 0f }
        pill.addView(b.progress, FrameLayout.LayoutParams(0, dp(2), Gravity.BOTTOM or Gravity.START))

        b.aa = TextView(this).apply {
            val s = SpannableString("AA")
            s.setSpan(RelativeSizeSpan(0.7f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = s; textSize = 17f; setTextColor(fg); gravity = Gravity.CENTER
            setOnClickListener { if (collapsed) setCollapsed(false) else showPageMenu() }
        }
        pill.addView(b.aa, FrameLayout.LayoutParams(dp(46), MP, Gravity.START))

        b.host = TextView(this).apply {
            textSize = 16f; setTextColor(fg); gravity = Gravity.CENTER; maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { if (collapsed) setCollapsed(false) else beginEdit() }
        }
        pill.addView(b.host, FrameLayout.LayoutParams(MP, MP).apply { marginStart = dp(46); marginEnd = dp(46) })

        b.edit = EditText(this).apply {
            background = null; textSize = 16f; setTextColor(fg); setHintTextColor(0x88888888.toInt())
            hint = "Telusuri atau masukkan nama situs web"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    val s = text.toString()
                    endEdit()
                    tab.web.loadUrl(resolve(s))
                    true
                } else false
            }
        }
        pill.addView(b.edit, FrameLayout.LayoutParams(MP, MP).apply { marginStart = dp(16); marginEnd = dp(64) })

        b.reload = IconView(this, "reload", fg, 0.5f).apply {
            setOnClickListener {
                if (collapsed) setCollapsed(false)
                else if (tab.progress in 1..99) tab.web.stopLoading() else tab.web.reload()
            }
        }
        pill.addView(b.reload, FrameLayout.LayoutParams(dp(46), MP, Gravity.END))

        b.cancel = TextView(this).apply {
            text = "Batal"; textSize = 16f; setTextColor(blue); gravity = Gravity.CENTER
            visibility = View.GONE
            setOnClickListener { endEdit() }
        }
        pill.addView(b.cancel, FrameLayout.LayoutParams(dp(62), MP, Gravity.END))
    }

    private fun toolbarRow(b: Bars): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun add(kind: String, click: () -> Unit) {
            val v = IconView(this, kind, fg, if (kind == "more") 0.6f else 0.56f)
            v.setOnClickListener { click() }
            b.btn[kind] = v
            row.addView(v, LinearLayout.LayoutParams(0, MP, 1f))
        }
        add("back") { if (tab.web.canGoBack()) tab.web.goBack() }
        add("forward") { if (tab.web.canGoForward()) tab.web.goForward() }
        add("share") { shareUrl() }
        add("more") { showMenu() }
        add("tabs") { showOverview() }
        return row
    }

    /** iOS 18: satu bar blur selebar layar, pill alamat + toolbar. */
    private fun build18(): Bars {
        val b = Bars()
        val glass = GlassView(this, webHolder, 0f, 0, dark, bg)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pill = FrameLayout(this)
        pill.background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(fillCol) }
        roundClip(pill, dp(12))
        fillPill(b, pill)
        col.addView(pill, LinearLayout.LayoutParams(MP, dp(42)).apply { setMargins(dp(10), 0, dp(10), 0) })
        val row = toolbarRow(b)
        col.addView(row, LinearLayout.LayoutParams(MP, dp(46)))
        glass.addView(col, FrameLayout.LayoutParams(MP, WC))
        val line = View(this).apply { setBackgroundColor(if (dark) 0x33FFFFFF else 0x22000000) }
        glass.addView(line, FrameLayout.LayoutParams(MP, 1))
        b.container.addView(glass, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))
        b.padView = col; b.addrMover = pill; b.toolMover = glass; b.toolHide = row
        return b
    }

    /** iOS 27: dua kapsul liquid glass melayang (alamat + toolbar). */
    private fun build27(): Bars {
        val b = Bars()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val addr = GlassView(this, webHolder, dp(26).toFloat(), 1, dark, bg)
        val pill = FrameLayout(this)
        fillPill(b, pill)
        addr.addView(pill, FrameLayout.LayoutParams(MP, MP))
        col.addView(addr, LinearLayout.LayoutParams(MP, dp(52)).apply { setMargins(dp(16), 0, dp(16), dp(8)) })
        val tool = GlassView(this, webHolder, dp(28).toFloat(), 1, dark, bg)
        tool.addView(toolbarRow(b), FrameLayout.LayoutParams(MP, MP))
        col.addView(tool, LinearLayout.LayoutParams(MP, dp(56)).apply { setMargins(dp(16), 0, dp(16), 0) })
        b.container.addView(col, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))
        b.padView = col; b.addrMover = addr; b.toolMover = tool; b.toolHide = tool
        return b
    }

    // ------------------------------------------------------------ bar behaviour
    private fun setCollapsed(c: Boolean) {
        val b = bars ?: return
        if (c == collapsed || (c && editing)) return
        collapsed = c
        animUntil = SystemClock.uptimeMillis() + 700
        val ip: TimeInterpolator =
            if (style == 27 && !c) OvershootInterpolator(1.1f) else DecelerateInterpolator(2f)
        val d = if (style == 27) 420L else 300L
        if (style == 18) {
            b.toolMover.animate().translationY(if (c) dp(46).toFloat() else 0f).setDuration(d).setInterpolator(ip).start()
            b.addrMover.animate().scaleX(if (c) .86f else 1f).scaleY(if (c) .9f else 1f).setDuration(d).setInterpolator(ip).start()
        } else {
            b.addrMover.animate().translationY(if (c) dp(64).toFloat() else 0f)
                .scaleX(if (c) .8f else 1f).scaleY(if (c) .85f else 1f).setDuration(d).setInterpolator(ip).start()
            b.toolMover.animate().translationY(if (c) dp(120).toFloat() else 0f)
                .alpha(if (c) 0f else 1f).setDuration(d).setInterpolator(ip).start()
        }
        b.aa.animate().alpha(if (c) 0f else 1f).setDuration(200).start()
        b.reload.animate().alpha(if (c) 0f else 1f).setDuration(200).start()
    }

    private fun beginEdit() {
        val b = bars ?: return
        editing = true
        b.host.visibility = View.GONE; b.aa.visibility = View.GONE; b.reload.visibility = View.GONE
        b.edit.visibility = View.VISIBLE; b.cancel.visibility = View.VISIBLE
        b.edit.setText(tab.web.url ?: tab.url)
        b.edit.requestFocus(); b.edit.selectAll()
        imm().showSoftInput(b.edit, 0)
        b.toolHide.visibility = View.GONE
    }

    private fun endEdit() {
        val b = bars ?: return
        editing = false
        imm().hideSoftInputFromWindow(b.edit.windowToken, 0)
        b.edit.clearFocus()
        b.edit.visibility = View.GONE; b.cancel.visibility = View.GONE
        b.host.visibility = View.VISIBLE; b.aa.visibility = View.VISIBLE; b.reload.visibility = View.VISIBLE
        b.toolHide.visibility = View.VISIBLE
    }

    private fun updateUi() {
        val b = bars ?: return
        if (tabs.isEmpty()) return
        val t = tab
        val u = t.web.url ?: t.url
        b.host.text = if (u.isBlank() || u == "about:blank") "Telusuri atau masukkan nama situs web"
        else (Uri.parse(u).host?.removePrefix("www.") ?: u)
        b.btn["back"]?.alpha = if (t.web.canGoBack()) 1f else .3f
        b.btn["forward"]?.alpha = if (t.web.canGoForward()) 1f else .3f
        b.reload.set(if (t.progress in 1..99) "close" else "reload", fg)
        val w = b.pill.width * t.progress / 100
        b.progress.layoutParams = (b.progress.layoutParams as FrameLayout.LayoutParams).apply { width = w }
        if (t.progress in 1..99) b.progress.animate().cancel().also { b.progress.alpha = 1f }
        else b.progress.animate().alpha(0f).setDuration(350).start()
    }

    // ------------------------------------------------------------ tabs
    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(url: String?): Tab {
        val t = Tab()
        val w = WebView(this)
        t.web = w
        w.setBackgroundColor(bg)
        with(w.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        val isCur = { tabs.getOrNull(cur) === t }

        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                t.url = u ?: ""; t.progress = 5; if (isCur()) updateUi()
            }
            override fun onPageFinished(v: WebView?, u: String?) {
                t.url = u ?: t.url; t.progress = 100; t.title = v?.title ?: t.title
                v?.evaluateJavascript(
                    "(function(){var d=document.getElementById('__sf_sp');if(!d){d=document.createElement('div');" +
                        "d.id='__sf_sp';d.style.cssText='height:140px;width:1px;pointer-events:none';" +
                        "(document.body||document.documentElement).appendChild(d);}})()", null
                )
                if (isCur()) updateUi()
            }
            override fun doUpdateVisitedHistory(v: WebView?, u: String?, r: Boolean) {
                t.url = u ?: t.url; if (isCur()) updateUi()
            }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val u = r?.url ?: return false
                val s = u.scheme ?: ""
                if (s == "http" || s == "https" || s == "about" || s == "file" || s == "data" || s == "blob") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView?, p: Int) { t.progress = p; if (isCur()) updateUi() }
            override fun onReceivedTitle(v: WebView?, title: String?) { t.title = title ?: "" }
            override fun onCreateWindow(v: WebView?, d: Boolean, g: Boolean, m: Message?): Boolean {
                val nt = addTab(null)
                val tr = m?.obj as? WebView.WebViewTransport ?: return false
                tr.webView = nt.web
                m.sendToTarget()
                selectTab(tabs.size - 1)
                return true
            }
            override fun onShowFileChooser(v: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try { startActivityForResult(p!!.createIntent(), 77); true } catch (e: Exception) { fileCb = null; false }
            }
        }
        w.setDownloadListener { u, ua, cd, mime, _ -> download(u, ua, cd, mime) }
        w.setOnScrollChangeListener { _, _, y, _, oy ->
            if (isCur() && !editing) {
                val d = y - oy
                if (y <= 10) setCollapsed(false) else if (d > 14) setCollapsed(true) else if (d < -14) setCollapsed(false)
            }
        }
        tabs.add(t)
        if (url != null) w.loadUrl(resolve(url))
        return t
    }

    private fun newTab(url: String?) {
        addTab(url ?: HOME)
        selectTab(tabs.size - 1)
    }

    private fun selectTab(i: Int) {
        cur = i.coerceIn(0, tabs.size - 1)
        webHolder.removeAllViews()
        val t = tab
        (t.web.parent as? ViewGroup)?.removeView(t.web)
        webHolder.addView(t.web, FrameLayout.LayoutParams(MP, MP))
        lastScrollY = -1
        setCollapsed(false)
        updateUi()
    }

    private fun capture(t: Tab) {
        if (t.web.width == 0 || t.web.height == 0) return
        val s = 0.35f
        val bmp = Bitmap.createBitmap((t.web.width * s).toInt(), (t.web.height * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg); c.scale(s, s)
        try { t.web.draw(c) } catch (e: Throwable) { }
        t.thumb = bmp
    }

    private fun resolve(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return HOME
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://") || t.startsWith("about:")) return t
        val looksUrl = !t.contains(" ") && (t.contains(".") || t.startsWith("localhost")) &&
            Patterns.WEB_URL.matcher(t).matches()
        return if (looksUrl) "https://$t" else "https://www.google.com/search?q=" + Uri.encode(t)
    }

    private fun shareUrl() {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.web.url ?: tab.url)
        startActivity(Intent.createChooser(i, null))
    }

    // ------------------------------------------------------------ tab overview
    private fun showOverview() {
        if (overlay != null) return
        if (editing) endEdit()
        capture(tab)
        val is27 = style == 27
        val ovBg = if (dark) 0xFF1C1C1E.toInt() else 0xFFE5E5EA.toInt()
        val ov = FrameLayout(this)
        ov.setBackgroundColor(ovBg)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            setPadding(dp(14), topInset + dp(14), dp(14), dp(120) + navInset)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col, MP, WC)
        ov.addView(scroll, MP, MP)
        gridCol = col
        fillGrid(col)

        val bottom = GlassView(this, scroll, if (is27) dp(28).toFloat() else 0f, if (is27) 1 else 0, dark, ovBg)
        val rowv = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        rowv.addView(IconView(this, "plus", blue, 0.6f).apply {
            setOnClickListener { newTab(null); hideOverview() }
        }, LinearLayout.LayoutParams(dp(64), MP))
        val cnt = TextView(this).apply {
            text = "${tabs.size} Tab"; textSize = 15f; setTextColor(fg); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        countLabel = cnt
        rowv.addView(cnt, LinearLayout.LayoutParams(0, MP, 1f))
        rowv.addView(TextView(this).apply {
            text = "Selesai"; textSize = 17f; setTextColor(blue); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { hideOverview() }
        }, LinearLayout.LayoutParams(dp(84), MP))
        bottom.addView(rowv, FrameLayout.LayoutParams(MP, dp(if (is27) 56 else 50), Gravity.TOP))
        val bh = if (is27) dp(56) else dp(50) + navInset
        ov.addView(bottom, FrameLayout.LayoutParams(MP, bh, Gravity.BOTTOM).apply {
            if (is27) setMargins(dp(16), 0, dp(16), navInset + dp(8))
        })

        root.addView(ov, MP, MP)
        overlay = ov
        animUntil = SystemClock.uptimeMillis() + 700
        ov.alpha = 0f; ov.scaleX = 1.06f; ov.scaleY = 1.06f
        ov.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun hideOverview() {
        val ov = overlay ?: return
        overlay = null; gridCol = null; countLabel = null
        ov.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(240)
            .setInterpolator(DecelerateInterpolator(2f)).withEndAction { root.removeView(ov) }.start()
    }

    private fun fillGrid(col: LinearLayout) {
        col.removeAllViews()
        val w = (resources.displayMetrics.widthPixels - dp(28) - dp(12)) / 2
        var i = 0
        while (i < tabs.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in i..minOf(i + 1, tabs.size - 1)) {
                row.addView(card(j, w), LinearLayout.LayoutParams(w, WC).apply { if (j == i) marginEnd = dp(12) })
            }
            col.addView(row, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(14) })
            i += 2
        }
    }

    private fun card(j: Int, w: Int): View {
        val t = tabs[j]
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = FrameLayout(this)
        head.addView(TextView(this).apply {
            text = t.title.ifBlank { Uri.parse(t.url).host ?: "Tab Baru" }
            textSize = 13f; setTextColor(fg); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(26), 0)
        }, FrameLayout.LayoutParams(MP, WC, Gravity.CENTER_VERTICAL))
        head.addView(IconView(this, "close", if (dark) 0xFFAAAAAA.toInt() else 0xFF666666.toInt(), 0.7f).apply {
            setOnClickListener { closeTab(j, box) }
        }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END or Gravity.CENTER_VERTICAL))
        box.addView(head, LinearLayout.LayoutParams(MP, dp(28)))

        val frame = FrameLayout(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat(); setColor(Color.TRANSPARENT)
                if (j == cur) setStroke(dp(3), blue)
            }
            setOnClickListener { selectTab(j); hideOverview() }
        }
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(bg)
            t.thumb?.let { setImageBitmap(it) }
            roundClip(this, dp(16))
        }
        frame.addView(img, FrameLayout.LayoutParams(MP, ((w - dp(6)) * 1.3f).toInt()))
        box.addView(frame, LinearLayout.LayoutParams(MP, WC))
        return box
    }

    private fun closeTab(j: Int, v: View?) {
        fun doIt() {
            val t = tabs[j]
            (t.web.parent as? ViewGroup)?.removeView(t.web)
            t.web.destroy()
            tabs.removeAt(j)
            if (tabs.isEmpty()) addTab(HOME)
            if (j < cur) cur--
            cur = cur.coerceIn(0, tabs.size - 1)
            selectTab(cur)
            gridCol?.let { fillGrid(it) }
            countLabel?.text = "${tabs.size} Tab"
        }
        if (v != null) v.animate().scaleX(.8f).scaleY(.8f).alpha(0f).setDuration(180).withEndAction { doIt() }.start()
        else doIt()
    }

    // ------------------------------------------------------------ sheets / menus
    private fun showSheet(content: View) {
        hideSheet(false)
        val layer = FrameLayout(this)
        val scrim = View(this).apply {
            setBackgroundColor(0x55000000); alpha = 0f
            setOnClickListener { hideSheet() }
        }
        layer.addView(scrim, MP, MP)
        val sheet = GlassView(this, webHolder, dp(30).toFloat(), 2, dark, bg)
        sheet.addView(content, FrameLayout.LayoutParams(MP, WC))
        val bm = (if (imeInset > navInset) imeInset else navInset) + dp(10)
        layer.addView(sheet, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), bm) })
        root.addView(layer, MP, MP)
        sheetLayer = layer
        animUntil = SystemClock.uptimeMillis() + 800
        scrim.animate().alpha(1f).setDuration(250).start()
        sheet.alpha = 0f
        sheet.post {
            sheet.translationY = sheet.height.toFloat() + bm
            sheet.alpha = 1f
            sheet.animate().translationY(0f).setDuration(380)
                .setInterpolator(if (style == 27) OvershootInterpolator(0.7f) else DecelerateInterpolator(2.2f)).start()
        }
    }

    private fun hideSheet(animate: Boolean = true) {
        val layer = sheetLayer ?: return
        sheetLayer = null
        if (animate) layer.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(layer) }.start()
        else root.removeView(layer)
    }

    private fun sheetBase() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(10), dp(8), dp(8))
    }

    private fun sep() = View(this).apply {
        setBackgroundColor(if (dark) 0x33FFFFFF else 0x22000000)
    }.also { it.layoutParams = LinearLayout.LayoutParams(MP, 1).apply { setMargins(dp(16), 0, 0, 0) } }

    private fun row(label: String, trailing: View? = null, click: (() -> Unit)? = null): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0); minimumHeight = dp(52)
        }
        r.addView(TextView(this).apply { text = label; textSize = 17f; setTextColor(fg) }, LinearLayout.LayoutParams(0, WC, 1f))
        if (trailing != null) r.addView(trailing)
        if (click != null) r.setOnClickListener { click() }
        return r
    }

    private fun segmented(): View {
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dp(9).toFloat(); setColor(fillCol) }
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        for (v in intArrayOf(18, 27)) {
            seg.addView(TextView(this).apply {
                text = "iOS $v"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(fg)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                if (v == style) background = GradientDrawable().apply {
                    cornerRadius = dp(7).toFloat(); setColor(if (dark) 0xFF636366.toInt() else Color.WHITE)
                }
                setOnClickListener { if (v != style) setStyle(v) }
            })
        }
        return seg
    }

    private fun setStyle(v: Int) {
        style = v
        prefs.edit().putInt("style", v).apply()
        hideSheet(false)
        buildUi()
        bars?.container?.apply { alpha = 0f; animate().alpha(1f).setDuration(350).start() }
    }

    private fun showMenu() {
        val c = sheetBase()
        c.addView(row("Tab Baru") { hideSheet(); newTab(null) })
        c.addView(sep())
        c.addView(row("Unduhan") { hideSheet(); showDownloads() })
        c.addView(sep())
        c.addView(row("Tampilan Safari", segmented()))
        c.addView(sep())
        c.addView(row("Hapus Riwayat dan Data Situs") { clearData() })
        showSheet(c)
    }

    private fun showPageMenu() {
        val t = tab
        val c = sheetBase()
        val pct = TextView(this).apply { text = "${t.zoom}%"; textSize = 16f; setTextColor(fg); gravity = Gravity.CENTER }
        fun zoom(d: Int) {
            t.zoom = (t.zoom + d).coerceIn(50, 200)
            t.web.settings.textZoom = t.zoom
            pct.text = "${t.zoom}%"
        }
        val zr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        zr.addView(TextView(this).apply {
            text = "A"; textSize = 14f; setTextColor(fg); gravity = Gravity.CENTER; setOnClickListener { zoom(-10) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        zr.addView(pct, LinearLayout.LayoutParams(0, dp(52), 1f))
        zr.addView(TextView(this).apply {
            text = "A"; textSize = 24f; setTextColor(fg); gravity = Gravity.CENTER; setOnClickListener { zoom(10) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        c.addView(zr)
        c.addView(sep())
        val sw = Switch(this).apply {
            isChecked = t.desktop
            setOnCheckedChangeListener { _, on -> setDesktop(t, on) }
        }
        c.addView(row("Minta Situs Desktop", sw))
        c.addView(sep())
        c.addView(row("Muat Ulang") { hideSheet(); t.web.reload() })
        showSheet(c)
    }

    private fun setDesktop(t: Tab, on: Boolean) {
        t.desktop = on
        t.web.settings.userAgentString = if (on) {
            WebSettings.getDefaultUserAgent(this)
                .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
        } else null
        t.web.settings.useWideViewPort = on
        t.web.settings.loadWithOverviewMode = on
        t.web.reload()
    }

    private fun clearData() {
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        tabs.forEach { it.web.clearCache(true); it.web.clearHistory() }
        hideSheet()
        toast("Riwayat dan data situs dihapus")
    }

    private fun toast(msg: String) {
        val g = GlassView(this, webHolder, dp(22).toFloat(), if (style == 27) 1 else 2, dark, bg)
        g.addView(TextView(this).apply {
            text = msg; textSize = 14f; setTextColor(fg); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(18), dp(11), dp(18), dp(11))
        })
        root.addView(g, FrameLayout.LayoutParams(WC, WC, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = topInset + dp(10); leftMargin = dp(24); rightMargin = dp(24)
        })
        g.alpha = 0f; g.translationY = -dp(30).toFloat()
        g.animate().alpha(1f).translationY(0f).setDuration(300).start()
        animUntil = SystemClock.uptimeMillis() + 2700
        h.postDelayed({
            g.animate().alpha(0f).translationY(-dp(20).toFloat()).setDuration(250)
                .withEndAction { root.removeView(g) }.start()
        }, 2200)
    }

    // ------------------------------------------------------------ downloads
    private val dlReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { toast("Unduhan selesai • tersimpan di folder Unduhan") }
    }

    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val req = DownloadManager.Request(Uri.parse(url))
            if (mime != null) req.setMimeType(mime)
            if (ua != null) req.addRequestHeader("User-Agent", ua)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            req.setTitle(name)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            toast("Mengunduh “$name”")
        } catch (e: Exception) {
            toast("Unduhan gagal")
        }
    }

    private fun showDownloads() {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val c = sheetBase()
        c.addView(TextView(this).apply {
            text = "Unduhan"; textSize = 20f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(6), dp(16), dp(8))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var n = 0
        dm.query(DownloadManager.Query())?.use { cursor ->
            while (cursor.moveToNext() && n < 30) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "File"
                val st = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val sub = when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> Formatter.formatShortFileSize(this, total)
                    DownloadManager.STATUS_FAILED -> "Gagal"
                    else -> "Mengunduh… " + (if (total > 0) "${soFar * 100 / total}%" else "")
                }
                val r = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(9), dp(16), dp(9))
                    setOnClickListener { if (st == DownloadManager.STATUS_SUCCESSFUL) openDownload(dm, id) }
                }
                r.addView(TextView(this).apply {
                    text = title; textSize = 16f; setTextColor(fg); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                })
                r.addView(TextView(this).apply { text = sub; textSize = 13f; setTextColor(0xFF8E8E93.toInt()) })
                list.addView(r)
                n++
            }
        }
        if (n == 0) list.addView(TextView(this).apply {
            text = "Belum ada unduhan"; textSize = 15f; setTextColor(0xFF8E8E93.toInt()); setPadding(dp(16), dp(10), dp(16), dp(14))
        })
        val sv = ScrollView(this).apply { addView(list) }
        c.addView(sv, LinearLayout.LayoutParams(MP, if (n > 5) dp(300) else WC))
        c.addView(sep())
        c.addView(row("Buka Folder Unduhan") {
            hideSheet()
            try { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        })
        showSheet(c)
    }

    private fun openDownload(dm: DownloadManager, id: Long) {
        try {
            val uri = dm.getUriForDownloadedFile(id)
            val mime = dm.getMimeTypeForDownloadedFile(id)
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        }
    }
}
