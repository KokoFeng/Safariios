package com.fengios.safari

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Halaman awal Safari: Favorit, Bookmark, Daftar Bacaan, Riwayat. */
class StartPage(
    private val ctx: Context,
    private val style: Int,
    private val dark: Boolean,
    private val store: Store,
    private val topInset: Int,
    private val onOpen: (String) -> Unit,
    private val onPickBg: () -> Unit,
    private val onResetBg: () -> Unit,
    private val initialTab: Int = 0
) {
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val den = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * den).toInt()

    private val bgBmp = store.loadBg()
    private val glass = style == 27
    private val photo = style == 27 || bgBmp != null
    private val fg = if (photo || dark) Color.WHITE else Color.BLACK
    private val sub = if (photo) 0xCCFFFFFF.toInt() else 0xFF8E8E93.toInt()
    private val blue = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
    private val fill = if (dark) 0x3D767680 else 0x1F787880
    private val cardBg = when {
        photo -> 0x38FFFFFF
        dark -> 0xFF1C1C1E.toInt()
        else -> Color.WHITE
    }
    private val listBg = if (photo) 0x59000000 else cardBg
    private val lineCol = if (photo || dark) 0x22FFFFFF else 0x22000000

    private var tabIdx = initialTab
    private var showAll = false
    private var query = ""
    private lateinit var bgLayer: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var col: LinearLayout
    private lateinit var seg: LinearLayout
    private var histHolder: LinearLayout? = null

    fun build(): View {
        val root = FrameLayout(ctx)
        bgLayer = FrameLayout(ctx)
        if (bgBmp != null) {
            bgLayer.addView(ImageView(ctx).apply {
                setImageBitmap(bgBmp); scaleType = ImageView.ScaleType.CENTER_CROP
            }, FrameLayout.LayoutParams(MP, MP))
            bgLayer.addView(View(ctx).apply { setBackgroundColor(0x40000000) }, FrameLayout.LayoutParams(MP, MP))
        } else {
            bgLayer.background = if (photo)
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF3E7358.toInt(), 0xFF24503B.toInt(), 0xFF12281E.toInt())
                )
            else ColorDrawable(if (dark) Color.BLACK else 0xFFF2F2F7.toInt())
        }
        scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(16), topInset + dp(76), dp(16), dp(180))
        }
        col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(col, MP, WC)
        bgLayer.addView(scroll, MP, MP)
        root.addView(bgLayer, MP, MP)

        seg = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val kinds = arrayOf("grid", "book", "glasses", "clock")
        for (i in kinds.indices) {
            val cell = FrameLayout(ctx)
            cell.addView(IconView(ctx, kinds[i], fg, 0.5f), FrameLayout.LayoutParams(MP, MP))
            cell.setOnClickListener { tabIdx = i; render() }
            seg.addView(cell, LinearLayout.LayoutParams(0, MP, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        val barView: View
        if (glass) {
            val g = GlassView(ctx, bgLayer, dp(24).toFloat(), 1, dark, 0xFF24503B.toInt())
            g.addView(seg, FrameLayout.LayoutParams(MP, MP))
            barView = g
        } else {
            seg.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(fill) }
            barView = seg
        }
        root.addView(barView, FrameLayout.LayoutParams(MP, dp(48), Gravity.TOP).apply {
            setMargins(dp(16), topInset + dp(10), dp(16), 0)
        })
        render()
        return root
    }

    private fun highlight() = GradientDrawable().apply {
        cornerRadius = dp(21).toFloat()
        setColor(if (photo) 0x33000000 else if (dark) 0xFF3A3A3C.toInt() else Color.WHITE)
    }

    private fun render() {
        col.removeAllViews()
        histHolder = null
        when (tabIdx) {
            0 -> favPage()
            1 -> listPage("Bookmark", "bm")
            2 -> listPage("Daftar Bacaan", "rl")
            else -> historyPage()
        }
        for (i in 0 until seg.childCount) seg.getChildAt(i).background = if (i == tabIdx) highlight() else null
        scroll.scrollTo(0, 0)
    }

    // ---------------------------------------------------------- helpers
    private fun header(title: String, action: String? = null, onAction: (() -> Unit)? = null): View {
        val r = FrameLayout(ctx)
        r.addView(TextView(ctx).apply {
            text = title; textSize = 22f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
        }, FrameLayout.LayoutParams(WC, WC, Gravity.START or Gravity.CENTER_VERTICAL))
        if (action != null) r.addView(TextView(ctx).apply {
            text = action; textSize = 15f; setTextColor(if (photo) fg else blue)
            setPadding(dp(8), dp(8), 0, dp(8))
            setOnClickListener { onAction?.invoke() }
        }, FrameLayout.LayoutParams(WC, WC, Gravity.END or Gravity.CENTER_VERTICAL))
        r.setPadding(dp(4), dp(14), dp(4), dp(10))
        return r
    }

    private fun hint(t: String) = TextView(ctx).apply {
        text = t; textSize = 15f; setTextColor(sub); setPadding(dp(4), dp(6), dp(4), dp(12))
    }

    private fun confirm(title: String, msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(ctx).setTitle(title).setMessage(msg)
            .setPositiveButton("Hapus") { _, _ -> onYes() }
            .setNegativeButton("Batal", null).show()
    }

    private fun iconBox(e: Entry, size: Int, radius: Int): View {
        val host = store.host(e.u)
        val bmp = store.icon(host)
        val box = FrameLayout(ctx)
        if (bmp != null) {
            box.background = GradientDrawable().apply { cornerRadius = radius.toFloat(); setColor(Color.WHITE) }
            box.addView(ImageView(ctx).apply {
                setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(size / 6, size / 6, size / 6, size / 6)
            }, FrameLayout.LayoutParams(MP, MP))
        } else {
            val pal = intArrayOf(
                0xFF8E8E93.toInt(), 0xFF5E7CE2.toInt(), 0xFFE2745E.toInt(),
                0xFF4CAF7D.toInt(), 0xFFB07CE0.toInt(), 0xFFE0B24C.toInt()
            )
            box.background = GradientDrawable().apply {
                cornerRadius = radius.toFloat()
                setColor(pal[(host.hashCode() and 0x7fffffff) % pal.size])
            }
            box.addView(TextView(ctx).apply {
                text = host.take(1).uppercase(); setTextColor(Color.WHITE)
                textSize = size / den * 0.45f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
            }, FrameLayout.LayoutParams(MP, MP))
        }
        return box
    }

    private fun pill(t: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = t; textSize = 14f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(18), dp(10), dp(18), dp(10))
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat(); setColor(if (photo) 0x33FFFFFF else fill)
        }
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------- halaman Favorit
    private fun favPage() {
        val favs = store.favorites()
        col.addView(header("Favorit", if (favs.size > 8) (if (showAll) "Sembunyikan" else "Tampilkan Semua ›") else null) {
            showAll = !showAll; render()
        })
        if (favs.isEmpty()) {
            col.addView(hint("Situs yang sering Anda buka akan muncul di sini."))
        } else {
            val shown = if (showAll) favs else favs.take(8)
            for (chunk in shown.chunked(4)) {
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for (k in 0 until 4) {
                    if (k < chunk.size) row.addView(tile(chunk[k]), LinearLayout.LayoutParams(0, WC, 1f))
                    else row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                }
                col.addView(row, LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(6) })
            }
        }
        val rl = store.list("rl")
        col.addView(header("Daftar Bacaan", if (rl.size > 3) "Tampilkan Semua ›" else null) { tabIdx = 2; render() })
        if (rl.isEmpty()) col.addView(hint("Tambahkan halaman lewat menu ⋯ → Tambah ke Daftar Bacaan."))
        for (e in rl.take(3)) {
            col.addView(readCard(e), LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(10) })
        }
        val br = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }
        br.addView(pill("Ubah Latar Belakang") { onPickBg() })
        if (store.hasBg()) br.addView(pill("Reset") { onResetBg() }, LinearLayout.LayoutParams(WC, WC).apply { marginStart = dp(10) })
        col.addView(br, LinearLayout.LayoutParams(MP, WC))
    }

    private fun tile(e: Entry): View {
        val v = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }
        v.addView(iconBox(e, dp(62), dp(16)), LinearLayout.LayoutParams(dp(62), dp(62)))
        v.addView(TextView(ctx).apply {
            text = e.t.ifBlank { store.host(e.u) }; textSize = 11f; setTextColor(fg)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(MP, WC))
        v.setOnClickListener { onOpen(e.u) }
        v.setOnLongClickListener {
            confirm("Hapus dari Favorit?", e.t.ifBlank { store.host(e.u) }) { store.removeFavorite(e.u); render() }
            true
        }
        return v
    }

    private fun readCard(e: Entry): View {
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(cardBg) }
        }
        r.addView(iconBox(e, dp(56), dp(14)), LinearLayout.LayoutParams(dp(56), dp(56)))
        val tx = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        tx.addView(TextView(ctx).apply {
            text = e.t.ifBlank { store.host(e.u) }; textSize = 16f; setTextColor(fg)
            typeface = Typeface.DEFAULT_BOLD; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        tx.addView(TextView(ctx).apply {
            text = store.host(e.u); textSize = 13f; setTextColor(sub); maxLines = 1
        })
        r.addView(tx, LinearLayout.LayoutParams(0, WC, 1f))
        r.setOnClickListener { onOpen(e.u) }
        r.setOnLongClickListener {
            confirm("Hapus dari Daftar Bacaan?", e.t) { store.remove("rl", e.u); render() }
            true
        }
        return r
    }

    // ---------------------------------------------------------- daftar umum
    private fun rowItem(e: Entry, onRemove: (Entry) -> Unit): View {
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
        r.addView(iconBox(e, dp(30), dp(8)), LinearLayout.LayoutParams(dp(30), dp(30)))
        val tx = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        tx.addView(TextView(ctx).apply {
            text = e.t.ifBlank { store.host(e.u) }; textSize = 16f; setTextColor(fg)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        tx.addView(TextView(ctx).apply {
            text = e.u; textSize = 13f; setTextColor(sub); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        r.addView(tx, LinearLayout.LayoutParams(0, WC, 1f))
        r.setOnClickListener { onOpen(e.u) }
        r.setOnLongClickListener {
            confirm("Hapus item ini?", e.t.ifBlank { e.u }) { onRemove(e) }
            true
        }
        return r
    }

    private fun listBox(items: List<Entry>, onRemove: (Entry) -> Unit): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(listBg) }
        }
        items.forEachIndexed { i, e ->
            if (i > 0) box.addView(
                View(ctx).apply { setBackgroundColor(lineCol) },
                LinearLayout.LayoutParams(MP, 1).apply { marginStart = dp(56) }
            )
            box.addView(rowItem(e, onRemove))
        }
        return box
    }

    private fun listPage(title: String, key: String) {
        col.addView(header(title))
        val items = store.list(key)
        if (items.isEmpty()) {
            col.addView(hint("Belum ada item. Tambahkan lewat menu ⋯ di toolbar."))
        } else {
            col.addView(listBox(items) { store.remove(key, it.u); render() })
        }
    }

    // ---------------------------------------------------------- riwayat
    private fun historyPage() {
        col.addView(header("Riwayat", "Hapus…") {
            confirm("Hapus semua riwayat?", "Seluruh riwayat penelusuran akan dihapus.") {
                store.clearHistory(); render()
            }
        })
        val et = EditText(ctx).apply {
            hint = "Cari Riwayat"; setHintTextColor(sub); setTextColor(fg); textSize = 16f
            setSingleLine(true)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(if (photo) 0x33FFFFFF else fill)
            }
            setText(query)
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; fillHistory() }
        })
        col.addView(et, LinearLayout.LayoutParams(MP, WC))
        histHolder = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(histHolder, LinearLayout.LayoutParams(MP, WC))
        fillHistory()
    }

    private fun fillHistory() {
        val h = histHolder ?: return
        h.removeAllViews()
        val all = store.history()
            .filter { query.isBlank() || it.t.contains(query, true) || it.u.contains(query, true) }
            .take(150)
        if (all.isEmpty()) {
            h.addView(hint(if (query.isBlank()) "Belum ada riwayat." else "Tidak ada hasil."))
            return
        }
        val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val labelFmt = SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID"))
        val today = dayFmt.format(Date())
        val yest = dayFmt.format(Date(System.currentTimeMillis() - 86400000L))
        val groups = LinkedHashMap<String, MutableList<Entry>>()
        for (e in all) groups.getOrPut(dayFmt.format(Date(e.ts))) { mutableListOf() }.add(e)
        for ((k, items) in groups) {
            val label = when (k) {
                today -> "Hari Ini"
                yest -> "Kemarin"
                else -> labelFmt.format(Date(items[0].ts))
            }
            h.addView(TextView(ctx).apply {
                text = label; textSize = 20f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(16), 0, dp(8))
            })
            h.addView(listBox(items) { store.removeHistory(it.ts); fillHistory() })
        }
    }
}
