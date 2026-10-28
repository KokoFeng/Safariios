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
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
    private lateinit var store: Store
    private var startView: View? = null
    private var startTop = -1
    private var startIdx = 0
    private var organizeAuto = true
    private var tabQuery = ""
    private var tabSearchOn = false
    private var tabSearchHolder: FrameLayout? = null

    inner class Tab {
        lateinit var web: WebView
        var title = ""
        var url = ""
        var progress = 100
        var thumb: Bitmap? = null
        var desktop = false
        var zoom = 100
        var start = false
        var fromStart = false
    }

    inner class Bars {
        val container = FrameLayout(this@MainActivity)
        lateinit var padView: View
        lateinit var pill: FrameLayout
        lateinit var host: TextView
        lateinit var edit: EditText
        lateinit var aa: View
        lateinit var reload: IconView
        lateinit var cancel: TextView
        lateinit var progress: View
        lateinit var addrMover: View
        var toolMover: View? = null
        val sides = mutableListOf<View>()
        val hideOnEdit = mutableListOf<View>()
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
        store = Store(this)
        style = prefs.getInt("style", 27)
        organizeAuto = prefs.getBoolean("organize", true)
        GlassView.blur = prefs.getBoolean("blur", false)
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
        if (saved.isEmpty()) addTab(null) else saved.forEach { addTab(if (it == "start:") null else it) }
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
        prefs.edit().putString("tabs", tabs.joinToString("\n") { if (it.start) "start:" else (it.web.url ?: it.url) }).putInt("cur", cur).apply()
    }
    override fun onDestroy() { super.onDestroy(); try { unregisterReceiver(dlReceiver) } catch (e: Exception) { } }

    override fun onBackPressed() {
        when {
            sheetLayer != null -> hideSheet()
            overlay != null -> hideOverview()
            editing -> endEdit()
            tab.web.canGoBack() -> tab.web.goBack()
            !tab.start && tab.fromStart -> { tab.start = true; syncStart(); updateUi() }
            else -> super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 77) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
            fileCb = null
        } else if (req == 78) {
            val uri = data?.data
            if (res == RESULT_OK && uri != null) {
                if (store.saveBg(uri)) { syncStart(); toast("Latar belakang diperbarui") }
                else toast("Gagal memuat foto")
            }
        } else super.onActivityResult(req, res, data)
    }

    // refresh blur backdrop saat scroll / animasi
    private val ticker = object : Runnable {
        override fun run() {
            val sy = tabs.getOrNull(cur)?.web?.scrollY ?: 0
            val now = SystemClock.uptimeMillis()
            if (GlassView.blur && (sy != lastScrollY || now - lastRefresh > 700 || now < animUntil)) {
                lastScrollY = sy; lastRefresh = now
                GlassView.all.toList().forEach { it.refresh() }
            }
            h.postDelayed(this, if (GlassView.blur) 100L else 500L)
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
        syncStart()
        updateUi()
    }

    private fun applyInsets() {
        if (startView != null && startTop != topInset) { syncStart(); return }
        val startOn = tabs.isNotEmpty() && tab.start
        (webHolder.layoutParams as FrameLayout.LayoutParams).topMargin = if (startOn) 0 else topInset
        webHolder.requestLayout()
        val b = bars ?: return
        val pad = if (imeInset > navInset) imeInset else navInset
        if (style == 27) b.padView.setPadding(dp(14), 0, dp(14), pad + dp(8)) else b.padView.setPadding(0, dp(8), 0, pad)
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

        val aaClick = View.OnClickListener { if (collapsed) setCollapsed(false) else showPageMenu() }
        b.aa = if (style == 27) IconView(this, "pagemenu", fg, 0.5f).apply { setOnClickListener(aaClick) }
        else TextView(this).apply {
            val s = SpannableString("AA")
            s.setSpan(RelativeSizeSpan(0.7f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = s; textSize = 17f; setTextColor(fg); gravity = Gravity.CENTER
            setOnClickListener(aaClick)
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
                    go(s)
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
        add("back") {
            if (tab.web.canGoBack()) tab.web.goBack()
            else if (!tab.start && tab.fromStart) { tab.start = true; syncStart(); updateUi() }
        }
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
        b.padView = col; b.addrMover = pill; b.toolMover = glass; b.hideOnEdit.add(row)
        return b
    }

    private fun glassBox(content: View, corner: Float, src: View = webHolder): FrameLayout {
        val c: FrameLayout = if (style == 27) GlassView(this, src, corner, 1, dark, bg)
        else FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = corner; setColor(if (dark) 0xFF2C2C2E.toInt() else Color.WHITE)
            }
        }
        c.addView(content, FrameLayout.LayoutParams(MP, MP))
        return c
    }

    private fun glassCircle(content: View, size: Int, src: View = webHolder) = glassBox(content, size / 2f, src)

    private fun flatCircle(content: View, size: Int, color: Int): FrameLayout {
        val c = FrameLayout(this)
        c.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        c.addView(content, FrameLayout.LayoutParams(MP, MP))
        return c
    }

    /** iOS 27: layout compact ala iOS 26 - tombol back, kapsul alamat, tombol ... (menu lengkap). */
    private fun build27(): Bars {
        val b = Bars()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val backIc = IconView(this, "back", fg, 0.5f)
        b.btn["back"] = backIc
        val back = glassCircle(backIc, dp(52))
        back.setOnClickListener {
            if (collapsed) setCollapsed(false)
            else if (tab.web.canGoBack()) tab.web.goBack()
            else if (!tab.start && tab.fromStart) { tab.start = true; syncStart(); updateUi() }
        }
        val addr = GlassView(this, webHolder, dp(26).toFloat(), 1, dark, bg)
        val pill = FrameLayout(this)
        fillPill(b, pill)
        addr.addView(pill, FrameLayout.LayoutParams(MP, MP))
        val more = glassCircle(IconView(this, "more", fg, 0.6f), dp(52))
        more.setOnClickListener { if (collapsed) setCollapsed(false) else showMenu() }
        row.addView(back, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(addr, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
        row.addView(more, LinearLayout.LayoutParams(dp(52), dp(52)))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(row, LinearLayout.LayoutParams(MP, WC))
        b.container.addView(col, FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM))
        b.padView = col; b.addrMover = addr
        b.sides.add(back); b.sides.add(more)
        b.hideOnEdit.add(back); b.hideOnEdit.add(more)
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
        val d = if (style == 27) 380L else 300L
        if (style == 18) {
            b.toolMover?.animate()?.translationY(if (c) dp(46).toFloat() else 0f)?.setDuration(d)?.setInterpolator(ip)?.start()
            b.addrMover.animate().scaleX(if (c) .86f else 1f).scaleY(if (c) .9f else 1f)
                .setDuration(d).setInterpolator(ip).start()
        } else {
            b.addrMover.animate().scaleX(if (c) .7f else 1f).scaleY(if (c) .8f else 1f)
                .translationY(if (c) dp(6).toFloat() else 0f).setDuration(d).setInterpolator(ip).start()
            for (v in b.sides) {
                v.animate().alpha(if (c) 0f else 1f).scaleX(if (c) .5f else 1f).scaleY(if (c) .5f else 1f)
                    .setDuration(d).setInterpolator(ip).start()
            }
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
        b.hideOnEdit.forEach { it.visibility = View.GONE }
    }

    private fun endEdit() {
        val b = bars ?: return
        editing = false
        imm().hideSoftInputFromWindow(b.edit.windowToken, 0)
        b.edit.clearFocus()
        b.edit.visibility = View.GONE; b.cancel.visibility = View.GONE
        b.host.visibility = View.VISIBLE; b.aa.visibility = View.VISIBLE; b.reload.visibility = View.VISIBLE
        b.hideOnEdit.forEach { it.visibility = View.VISIBLE }
    }

    private fun updateUi() {
        val b = bars ?: return
        if (tabs.isEmpty()) return
        val t = tab
        val u = t.web.url ?: t.url
        b.host.text = if (u.isBlank() || u == "about:blank") "Telusuri atau masukkan nama situs web"
        else (Uri.parse(u).host?.removePrefix("www.") ?: u)
        b.btn["back"]?.alpha = if (t.web.canGoBack() || (!t.start && t.fromStart)) 1f else .3f
        b.btn["forward"]?.alpha = if (t.web.canGoForward()) 1f else .3f
        b.reload.set(if (t.progress in 1..99) "close" else "reload", fg)
        val w = b.pill.width * t.progress / 100
        b.progress.layoutParams = (b.progress.layoutParams as FrameLayout.LayoutParams).apply { width = w }
        if (t.progress in 1..99) b.progress.animate().cancel().also { b.progress.alpha = 1f }
        else b.progress.animate().alpha(0f).setDuration(350).start()
    }

    // ------------------------------------------------------------ halaman awal
    private fun syncStart() {
        startView?.let { webHolder.removeView(it) }
        startView = null
        startTop = topInset
        val on = tabs.isNotEmpty() && tab.start
        if (on) {
            val v = StartPage(this, style, dark, store, topInset, { go(it) }, { pickBackground() }, { store.clearBg(); syncStart() }, startIdx).build()
            webHolder.addView(v, FrameLayout.LayoutParams(MP, MP))
            startView = v
            startIdx = 0
        }
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars =
            !(dark || (on && (style == 27 || store.hasBg())))
        applyInsets()
    }

    private fun pickBackground() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try { startActivityForResult(Intent.createChooser(i, "Pilih foto latar belakang"), 78) }
        catch (e: Exception) { toast("Tidak ada aplikasi galeri") }
    }

    private fun openStartIn(idx: Int) {
        startIdx = idx
        newTab(null)
    }

    private fun go(raw: String) {
        val t = tab
        if (t.start) { t.fromStart = true; t.start = false; syncStart() }
        t.web.loadUrl(resolve(raw))
    }

    private fun addTo(kind: String) {
        hideSheet()
        val t = tab
        if (t.start) { toast("Buka sebuah halaman dulu"); return }
        val u = t.web.url ?: t.url
        val ti = t.web.title ?: t.title
        when (kind) {
            "fav" -> store.pin(u, ti)
            "bm" -> store.add("bm", u, ti)
            else -> store.add("rl", u, ti)
        }
        toast(when (kind) {
            "fav" -> "Ditambahkan ke Favorit"
            "bm" -> "Bookmark ditambahkan"
            else -> "Ditambahkan ke Daftar Bacaan"
        })
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
                t.url = u ?: ""; t.progress = 5
                if (t.start) { t.start = false; if (isCur()) syncStart() }
                if (isCur()) updateUi()
            }
            override fun onPageFinished(v: WebView?, u: String?) {
                t.url = u ?: t.url; t.progress = 100; t.title = v?.title ?: t.title
                if (u != null && u.startsWith("http")) store.addHistory(u, t.title)
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
            override fun onReceivedIcon(v: WebView?, icon: Bitmap?) {
                val u = v?.url
                if (icon != null && u != null && u.startsWith("http")) store.saveIcon(store.host(u), icon)
            }
            override fun onCreateWindow(v: WebView?, d: Boolean, g: Boolean, m: Message?): Boolean {
                val nt = addTab(null)
                nt.start = false
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
        if (url != null) w.loadUrl(resolve(url)) else t.start = true
        return t
    }

    private fun newTab(url: String?) {
        addTab(url)
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
        syncStart()
        updateUi()
    }

    private fun capture(t: Tab) {
        val src: View = if (t.start && startView != null) startView!! else t.web
        if (src.width == 0 || src.height == 0) return
        val s = 0.35f
        val bmp = Bitmap.createBitmap((src.width * s).toInt(), (src.height * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg); c.scale(s, s)
        try { src.draw(c) } catch (e: Throwable) { }
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
        tabQuery = ""; tabSearchOn = false
        val ovBg = if (dark) 0xFF1C1C1E.toInt() else 0xFFE5E5EA.toInt()
        val ov = FrameLayout(this)
        ov.setBackgroundColor(ovBg)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false; clipToPadding = false
            setPadding(dp(14), topInset + dp(70), dp(14), dp(120) + navInset)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sh = FrameLayout(this)
        tabSearchHolder = sh
        col.addView(sh, LinearLayout.LayoutParams(MP, WC))
        val gh = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(gh, LinearLayout.LayoutParams(MP, WC))
        scroll.addView(col, MP, WC)
        ov.addView(scroll, MP, MP)
        gridCol = gh
        fillGrid(gh)

        // bar atas: cari tab, atur tab, menu
        val top = FrameLayout(this)
        val searchBtn = glassCircle(IconView(this, "search", fg, 0.5f), dp(44), scroll)
        searchBtn.setOnClickListener { toggleTabSearch() }
        val orgBtn = glassCircle(IconView(this, "organize", fg, 0.5f), dp(44), scroll)
        orgBtn.setOnClickListener { showOrganizePopup(ov) }
        val moreBtn = glassCircle(IconView(this, "more", fg, 0.6f), dp(44), scroll)
        moreBtn.setOnClickListener { showOverviewMore(ov) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        left.addView(searchBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        left.addView(orgBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(10) })
        top.addView(left, FrameLayout.LayoutParams(WC, WC, Gravity.START))
        top.addView(moreBtn, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END))
        ov.addView(top, FrameLayout.LayoutParams(MP, dp(44), Gravity.TOP).apply {
            setMargins(dp(16), topInset + dp(10), dp(16), 0)
        })

        // bar bawah: + , jumlah tab, centang
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val plus = glassCircle(IconView(this, "plus", fg, 0.55f), dp(52), scroll)
        plus.setOnClickListener { newTab(null); hideOverview() }
        val cnt = TextView(this).apply {
            text = "${tabs.size} Tab"; textSize = 15f; setTextColor(fg); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        countLabel = cnt
        val cntBox = glassBox(cnt, dp(26).toFloat(), scroll)
        val ok = flatCircle(IconView(this, "check", Color.WHITE, 0.55f), dp(52), blue)
        ok.setOnClickListener { hideOverview() }
        bottom.addView(plus, LinearLayout.LayoutParams(dp(52), dp(52)))
        bottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottom.addView(cntBox, LinearLayout.LayoutParams(dp(150), dp(52)))
        bottom.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottom.addView(ok, LinearLayout.LayoutParams(dp(52), dp(52)))
        ov.addView(bottom, FrameLayout.LayoutParams(MP, dp(52), Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), navInset + dp(14))
        })

        root.addView(ov, MP, MP)
        overlay = ov
        animUntil = SystemClock.uptimeMillis() + 700
        ov.alpha = 0f; ov.scaleX = 1.06f; ov.scaleY = 1.06f
        ov.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(DecelerateInterpolator(2f)).start()
    }

    private fun hideOverview() {
        val ov = overlay ?: return
        imm().hideSoftInputFromWindow(ov.windowToken, 0)
        overlay = null; gridCol = null; countLabel = null; tabSearchHolder = null
        ov.animate().alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(240)
            .setInterpolator(DecelerateInterpolator(2f)).withEndAction { root.removeView(ov) }.start()
    }

    private fun toggleTabSearch() {
        val hold = tabSearchHolder ?: return
        tabSearchOn = !tabSearchOn
        hold.removeAllViews()
        if (tabSearchOn) {
            val et = EditText(this).apply {
                hint = "Cari Tab"; textSize = 16f; setTextColor(fg); setHintTextColor(0x88888888.toInt())
                setSingleLine(true)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat(); setColor(if (dark) 0xFF2C2C2E.toInt() else Color.WHITE)
                }
            }
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    tabQuery = s?.toString() ?: ""
                    gridCol?.let { fillGrid(it) }
                }
            })
            hold.setPadding(0, 0, 0, dp(12))
            hold.addView(et, FrameLayout.LayoutParams(MP, WC))
            et.requestFocus()
            imm().showSoftInput(et, 0)
        } else {
            hold.setPadding(0, 0, 0, 0)
            tabQuery = ""
            imm().hideSoftInputFromWindow(hold.windowToken, 0)
            gridCol?.let { fillGrid(it) }
        }
    }

    private fun groupHeader(title: String, n: Int) = TextView(this).apply {
        text = title.replaceFirstChar { it.uppercase() } + " · $n tab"
        textSize = 15f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(6), 0, dp(8))
    }

    private fun addRows(col: LinearLayout, list: List<Int>, w: Int) {
        var i = 0
        while (i < list.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in i..minOf(i + 1, list.size - 1)) {
                row.addView(card(list[k], w), LinearLayout.LayoutParams(w, WC).apply { if (k == i) marginEnd = dp(12) })
            }
            col.addView(row, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(14) })
            i += 2
        }
    }

    /** Atur Tab: kalau "otomatis", tab dari situs yang sama dikelompokkan jadi satu topik. */
    private fun fillGrid(col: LinearLayout) {
        col.removeAllViews()
        val w = (resources.displayMetrics.widthPixels - dp(28) - dp(12)) / 2
        val q = tabQuery.trim()
        val idx = tabs.indices.filter {
            q.isEmpty() || tabs[it].title.contains(q, true) || tabs[it].url.contains(q, true)
        }
        if (idx.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Tidak ada tab"; textSize = 15f; setTextColor(0xFF8E8E93.toInt()); setPadding(dp(4), dp(20), 0, 0)
            })
            return
        }
        val groups = LinkedHashMap<String, MutableList<Int>>()
        val singles = mutableListOf<Int>()
        if (organizeAuto) {
            val byHost = LinkedHashMap<String, MutableList<Int>>()
            for (i in idx) {
                val t = tabs[i]
                val u = t.web.url ?: t.url
                val hostKey = if (t.start || u.isBlank()) "" else store.host(u)
                if (hostKey.isEmpty()) singles.add(i) else byHost.getOrPut(hostKey) { mutableListOf() }.add(i)
            }
            for ((hostKey, l) in byHost) {
                if (l.size >= 2) { groups[hostKey] = l } else { singles.addAll(l) }
            }
            singles.sort()
        } else {
            singles.addAll(idx)
        }
        for ((hostKey, l) in groups) {
            col.addView(groupHeader(hostKey, l.size))
            addRows(col, l, w)
        }
        if (groups.isNotEmpty() && singles.isNotEmpty()) col.addView(groupHeader("Tab Lainnya", singles.size))
        addRows(col, singles, w)
    }

    private fun showPopup(ov: FrameLayout, gravity: Int, content: View) {
        val layer = FrameLayout(this)
        layer.setOnClickListener { ov.removeView(layer) }
        val ovBg = if (dark) 0xFF1C1C1E.toInt() else 0xFFE5E5EA.toInt()
        val card = GlassView(this, ov, dp(22).toFloat(), 3, dark, ovBg, true)
        card.isClickable = true
        card.addView(content, FrameLayout.LayoutParams(MP, WC))
        layer.addView(card, FrameLayout.LayoutParams(dp(280), WC, gravity or Gravity.TOP).apply {
            setMargins(dp(16), topInset + dp(60), dp(16), 0)
        })
        ov.addView(layer, MP, MP)
        card.alpha = 0f; card.scaleX = .92f; card.scaleY = .92f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun showOrganizePopup(ov: FrameLayout) {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(8)) }
        c.addView(TextView(this).apply {
            text = "Atur Tab"; textSize = 16f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
        })
        c.addView(TextView(this).apply {
            text = if (organizeAuto) "Otomatis Buat Topik" else "Jangan Pernah"
            textSize = 13f; setTextColor(0xFF8E8E93.toInt()); setPadding(0, dp(2), 0, dp(10))
        })
        fun opt(title: String, desc: String?, sel: Boolean, pick: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }
            r.addView(
                if (sel) IconView(this, "check", fg, 0.7f) else View(this),
                LinearLayout.LayoutParams(dp(26), dp(26))
            )
            val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
            tx.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(fg) })
            if (desc != null) tx.addView(TextView(this).apply { text = desc; textSize = 12f; setTextColor(0xFF8E8E93.toInt()) })
            r.addView(tx, LinearLayout.LayoutParams(0, WC, 1f))
            r.setOnClickListener { pick() }
            c.addView(r)
        }
        lateinit var popupLayer: View
        fun choose(auto: Boolean) {
            organizeAuto = auto
            prefs.edit().putBoolean("organize", auto).apply()
            (popupLayer.parent as? ViewGroup)?.removeView(popupLayer)
            gridCol?.let { fillGrid(it) }
        }
        opt("Buat Topik Otomatis", "Tab dari situs yang sama dikelompokkan otomatis menjadi satu topik.", organizeAuto) { choose(true) }
        opt("Jangan Pernah", null, !organizeAuto) { choose(false) }
        showPopup(ov, Gravity.START, c)
        popupLayer = ov.getChildAt(ov.childCount - 1)
    }

    private fun showOverviewMore(ov: FrameLayout) {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(6), dp(18), dp(6)) }
        lateinit var popupLayer: View
        fun item(label: String, color: Int, act: () -> Unit) {
            c.addView(TextView(this).apply {
                text = label; textSize = 16f; setTextColor(color); setPadding(0, dp(13), 0, dp(13))
                setOnClickListener { (popupLayer.parent as? ViewGroup)?.removeView(popupLayer); act() }
            })
        }
        item("Tab Baru", fg) { newTab(null); hideOverview() }
        item("Tutup Semua Tab", 0xFFFF453A.toInt()) {
            tabs.forEach { (it.web.parent as? ViewGroup)?.removeView(it.web); it.web.destroy() }
            tabs.clear()
            addTab(null)
            cur = 0
            selectTab(0)
            gridCol?.let { fillGrid(it) }
            countLabel?.text = "1 Tab"
        }
        showPopup(ov, Gravity.END, c)
        popupLayer = ov.getChildAt(ov.childCount - 1)
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
            if (tabs.isEmpty()) addTab(null)
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

    // ---- popup kompak ala iOS 26 (kartu kaca di dekat tombol)
    private fun pmRow(
        icon: String?, label: String, chevron: Boolean = false, trailing: View? = null, click: (() -> Unit)? = null
    ): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(14), 0); minimumHeight = dp(50)
        }
        if (icon != null) r.addView(
            IconView(this, icon, blue, 0.6f),
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(14) }
        )
        r.addView(TextView(this).apply {
            text = label; textSize = 16f; setTextColor(fg); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, WC, 1f))
        if (trailing != null) r.addView(trailing)
        if (chevron) r.addView(
            IconView(this, "forward", 0xFF8E8E93.toInt(), 0.4f),
            LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(6) }
        )
        if (click != null) r.setOnClickListener { click() }
        return r
    }

    private fun pmSep() = View(this).apply {
        setBackgroundColor(if (dark) 0x22FFFFFF else 0x22000000)
    }.also { it.layoutParams = LinearLayout.LayoutParams(MP, 1).apply { marginStart = dp(58) } }

    private fun pmList(h: FrameLayout, title: String?, onBack: (() -> Unit)?, rows: List<View>) {
        h.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        if (onBack != null) {
            col.addView(pmRow("back", title ?: "Kembali") { onBack() })
            col.addView(pmSep())
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) col.addView(pmSep())
            col.addView(r)
        }
        val sv = MaxHeightScrollView(this, (resources.displayMetrics.heightPixels * 0.55f).toInt()).apply {
            isVerticalScrollBarEnabled = false
            addView(col)
        }
        h.addView(sv, FrameLayout.LayoutParams(MP, WC))
    }

    private fun showPopupCard(content: View, align: Int, width: Int = dp(268)) {
        hideSheet(false)
        val layer = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(0x1A000000); setOnClickListener { hideSheet() } }
        layer.addView(scrim, MP, MP)
        val card = GlassView(this, webHolder, dp(26).toFloat(), 3, dark, bg, true)
        card.addView(content, FrameLayout.LayoutParams(MP, WC))
        val barH = bars?.container?.height?.takeIf { it > 0 } ?: dp(90)
        layer.addView(card, FrameLayout.LayoutParams(width, WC, Gravity.BOTTOM or align).apply {
            setMargins(dp(14), 0, dp(14), barH + dp(8))
        })
        root.addView(layer, MP, MP)
        sheetLayer = layer
        card.alpha = 0f; card.scaleX = .85f; card.scaleY = .85f
        card.post {
            card.pivotX = when (align) {
                Gravity.END -> card.width.toFloat()
                Gravity.START -> 0f
                else -> card.width / 2f
            }
            card.pivotY = card.height.toFloat()
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(OvershootInterpolator(0.9f)).start()
        }
    }

    private fun showMenu() {
        val h = FrameLayout(this)
        menuTop(h)
        showPopupCard(h, Gravity.END)
    }

    private fun menuTop(h: FrameLayout) {
        val fwRow = pmRow("forward", "Maju") { hideSheet(); if (tab.web.canGoForward()) tab.web.goForward() }
        fwRow.alpha = if (tab.web.canGoForward()) 1f else .4f
        pmList(h, null, null, listOf(
            pmRow("share", "Bagikan Halaman") { hideSheet(); shareUrl() },
            pmRow("star", "Tambah ke…", true) { menuAdd(h) },
            pmRow("tabs", "Tab", true) { menuTabs(h) },
            fwRow,
            pmRow("book", "Bookmark & Riwayat", true) { menuLib(h) },
            pmRow("organize", "Pengaturan", true) { menuSettings(h) }
        ))
    }

    private fun menuAdd(h: FrameLayout) {
        pmList(h, "Tambah ke…", { menuTop(h) }, listOf(
            pmRow("star", "Favorit") { addTo("fav") },
            pmRow("book", "Bookmark") { addTo("bm") },
            pmRow("glasses", "Daftar Bacaan") { addTo("rl") }
        ))
    }

    private fun menuTabs(h: FrameLayout) {
        pmList(h, "Tab", { menuTop(h) }, listOf(
            pmRow("plus", "Tab Baru") { hideSheet(); newTab(null) },
            pmRow("tabs", "Lihat Semua Tab") { hideSheet(); showOverview() },
            pmRow("close", "Tutup Tab Ini") { hideSheet(); closeTab(cur, null) }
        ))
    }

    private fun menuLib(h: FrameLayout) {
        pmList(h, "Bookmark & Riwayat", { menuTop(h) }, listOf(
            pmRow("book", "Bookmark") { hideSheet(); openStartIn(1) },
            pmRow("glasses", "Daftar Bacaan") { hideSheet(); openStartIn(2) },
            pmRow("clock", "Riwayat") { hideSheet(); openStartIn(3) },
            pmRow("download", "Unduhan") { hideSheet(); showDownloads() }
        ))
    }

    private fun menuSettings(h: FrameLayout) {
        val t = tab
        val sw = Switch(this).apply {
            isChecked = t.desktop
            setOnCheckedChangeListener { _, on -> setDesktop(t, on) }
        }
        val bs = Switch(this).apply {
            isChecked = GlassView.blur
            setOnCheckedChangeListener { _, on -> setBlur(on) }
        }
        pmList(h, "Pengaturan", { menuTop(h) }, listOf(
            pmRow("desktop", "Situs Desktop", false, sw),
            pmRow(null, "Tampilan", false, segmented()),
            pmRow(null, "Blur Latar (berat)", false, bs),
            pmRow("photo", "Ubah Latar Halaman Awal") { hideSheet(); pickBackground() },
            pmRow("trash", "Hapus Riwayat & Data") { clearData() }
        ))
    }

    private fun setBlur(on: Boolean) {
        GlassView.blur = on
        prefs.edit().putBoolean("blur", on).apply()
        hideSheet(false)
        buildUi()
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
        showPopupCard(c, Gravity.CENTER_HORIZONTAL, dp(280))
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
        store.clearHistory()
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
        showPopupCard(c, Gravity.CENTER_HORIZONTAL, dp(320))
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
