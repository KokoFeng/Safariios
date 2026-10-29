package com.fengios.safari

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/** Ikon garis ala SF Symbols, digambar manual di grid 24x24. */
class IconView(ctx: Context, var kind: String, var tint: Int = Color.BLACK, var frac: Float = 0.55f) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun set(kind: String, tint: Int) {
        this.kind = kind; this.tint = tint; invalidate()
    }

    override fun onDraw(c: Canvas) {
        val u = min(width, height) * frac / 24f
        c.save()
        c.translate(width / 2f, height / 2f)
        c.scale(u, u)
        c.translate(-12f, -12f)
        p.color = tint
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.1f
        when (kind) {
            "back" -> {
                p.strokeWidth = 2.7f
                c.drawPath(Path().apply { moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f) }, p)
            }
            "forward" -> {
                p.strokeWidth = 2.7f
                c.drawPath(Path().apply { moveTo(9f, 4.5f); lineTo(16.5f, 12f); lineTo(9f, 19.5f) }, p)
            }
            "share" -> {
                c.drawPath(Path().apply {
                    moveTo(8.5f, 9.5f); lineTo(6.5f, 9.5f); lineTo(6.5f, 20f)
                    lineTo(17.5f, 20f); lineTo(17.5f, 9.5f); lineTo(15.5f, 9.5f)
                }, p)
                c.drawPath(Path().apply {
                    moveTo(12f, 15f); lineTo(12f, 3.5f)
                    moveTo(8.2f, 7f); lineTo(12f, 3.3f); lineTo(15.8f, 7f)
                }, p)
            }
            "tabs" -> {
                c.drawRoundRect(RectF(3.5f, 8.5f, 15.5f, 20.5f), 2.5f, 2.5f, p)
                c.drawPath(Path().apply {
                    moveTo(8f, 8.5f); lineTo(8f, 6.5f); quadTo(8f, 4.5f, 10f, 4.5f)
                    lineTo(18f, 4.5f); quadTo(20.5f, 4.5f, 20.5f, 6.5f)
                    lineTo(20.5f, 14.5f); quadTo(20.5f, 16.5f, 18.5f, 16.5f); lineTo(15.5f, 16.5f)
                }, p)
            }
            "more" -> {
                p.style = Paint.Style.FILL
                for (x in floatArrayOf(5f, 12f, 19f)) c.drawCircle(x, 12f, 1.9f, p)
            }
            "reload" -> {
                c.drawArc(RectF(5f, 5f, 19f, 19f), -40f, 290f, false, p)
                c.drawPath(Path().apply { moveTo(17.4f, 3.5f); lineTo(17.4f, 7.5f); lineTo(13.4f, 7.5f) }, p)
            }
            "plus" -> {
                p.strokeWidth = 2.4f
                c.drawLine(12f, 4.5f, 12f, 19.5f, p); c.drawLine(4.5f, 12f, 19.5f, 12f, p)
            }
            "fillarrow" -> {
                c.drawCircle(12f, 12f, 9f, p)
                c.drawPath(Path().apply {
                    moveTo(15.5f, 15.5f); lineTo(9f, 9f)
                    moveTo(9f, 13f); lineTo(9f, 9f); lineTo(13f, 9f)
                }, p)
            }
            "search" -> {
                c.drawCircle(10.5f, 10.5f, 6.5f, p); c.drawLine(15.4f, 15.4f, 20f, 20f, p)
            }
            "organize" -> {
                p.strokeWidth = 2.3f
                c.drawLine(4f, 7f, 20f, 7f, p); c.drawLine(7f, 12f, 17f, 12f, p); c.drawLine(10f, 17f, 14f, 17f, p)
            }
            "check" -> {
                p.strokeWidth = 2.8f
                c.drawPath(Path().apply { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }, p)
            }
            "pagemenu" -> {
                c.drawRoundRect(RectF(4f, 4f, 20f, 14f), 2.5f, 2.5f, p)
                c.drawLine(8f, 18f, 16f, 18f, p); c.drawLine(10f, 21.5f, 14f, 21.5f, p)
            }
            "star" -> {
                c.drawPath(Path().apply {
                    moveTo(12f, 2.5f); lineTo(14.35f, 8.76f); lineTo(21.03f, 9.06f); lineTo(15.8f, 13.24f)
                    lineTo(17.58f, 19.69f); lineTo(12f, 16f); lineTo(6.42f, 19.69f); lineTo(8.2f, 13.24f)
                    lineTo(2.97f, 9.06f); lineTo(9.65f, 8.76f); close()
                }, p)
            }
            "download" -> {
                c.drawPath(Path().apply {
                    moveTo(12f, 3.5f); lineTo(12f, 15f)
                    moveTo(7.5f, 10.8f); lineTo(12f, 15.3f); lineTo(16.5f, 10.8f)
                    moveTo(5f, 19.5f); lineTo(19f, 19.5f)
                }, p)
            }
            "photo" -> {
                c.drawRoundRect(RectF(3.5f, 5f, 20.5f, 19f), 2.5f, 2.5f, p)
                c.drawCircle(8.5f, 10f, 1.5f, p)
                c.drawPath(Path().apply { moveTo(4f, 17f); lineTo(9.5f, 12.5f); lineTo(13f, 15.5f); lineTo(16f, 13f); lineTo(20f, 17f) }, p)
            }
            "trash" -> {
                c.drawPath(Path().apply {
                    moveTo(4.5f, 7f); lineTo(19.5f, 7f)
                    moveTo(9.5f, 7f); lineTo(9.5f, 5f); lineTo(14.5f, 5f); lineTo(14.5f, 7f)
                    moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
                }, p)
            }
            "desktop" -> {
                c.drawRoundRect(RectF(3.5f, 5f, 20.5f, 16f), 2.2f, 2.2f, p)
                c.drawLine(12f, 16f, 12f, 20f, p); c.drawLine(8.5f, 20f, 15.5f, 20f, p)
            }
            "grid" -> {
                p.style = Paint.Style.FILL
                for (i in 0..2) for (j in 0..2) {
                    val x = 4f + i * 6.5f; val y = 4f + j * 6.5f
                    c.drawRoundRect(RectF(x, y, x + 4f, y + 4f), 1.1f, 1.1f, p)
                }
            }
            "book" -> {
                c.drawPath(Path().apply {
                    moveTo(12f, 6.5f); quadTo(8.5f, 4.5f, 3.5f, 5.5f); lineTo(3.5f, 18f)
                    quadTo(8.5f, 17f, 12f, 19.5f)
                    moveTo(12f, 6.5f); quadTo(15.5f, 4.5f, 20.5f, 5.5f); lineTo(20.5f, 18f)
                    quadTo(15.5f, 17f, 12f, 19.5f); lineTo(12f, 6.5f)
                }, p)
            }
            "glasses" -> {
                c.drawCircle(6.8f, 13f, 4.2f, p); c.drawCircle(17.2f, 13f, 4.2f, p)
                c.drawPath(Path().apply { moveTo(10.6f, 12f); quadTo(12f, 10.6f, 13.4f, 12f) }, p)
            }
            "clock" -> {
                c.drawCircle(12f, 12f, 8.5f, p)
                c.drawPath(Path().apply { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) }, p)
            }
            "close" -> {
                p.strokeWidth = 2.4f
                c.drawLine(6f, 6f, 18f, 18f, p); c.drawLine(18f, 6f, 6f, 18f, p)
            }
        }
        c.restore()
    }
}

/**
 * Material kaca. kind 0 = blur bar iOS 18, kind 1 = liquid glass iOS 27, kind 2 = sheet/menu.
 * Blur backdrop: isi [source] di-snapshot kecil lalu di-blur (RenderEffect di Android 12+).
 */
class GlassView(
    ctx: Context, private val source: View, var corner: Float,
    private val kind: Int, dark: Boolean, var bgColor: Int,
    private val once: Boolean = false,
    private val idleParam: Boolean = false
) : FrameLayout(ctx) {

    companion object {
        val all = mutableListOf<GlassView>()
        var blur = false   // blur latar belakang (berat) - default mati
    }

    private val img = FlatImage(ctx).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val tint = FlatView(ctx)
    private var bmp: Bitmap? = null
    private val cv = Canvas()
    private val loc = IntArray(2)
    private val sloc = IntArray(2)
    private val density = resources.displayMetrics.density
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * density }
    private val sheenP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    /** Mode idle: blur diambil sekali saat layar diam (ringan), disembunyikan saat scroll. */
    val idle: Boolean get() = idleParam && !blur && !once
    private val live: Boolean get() = blur || once || idle
    private val fallback = FlatView(ctx)

    init {
        if (idle) addView(fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(img, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(tint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // isian semi-solid (dipakai saat tanpa blur, dan sebagai cadangan saat scroll di mode idle)
        val solid: Drawable = when (kind) {
            1 -> GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (dark) intArrayOf(0xE62F2F31.toInt(), 0xD91C1C1E.toInt())
                else intArrayOf(0xEBFFFFFF.toInt(), 0xD9F0F0F4.toInt())
            )
            0 -> ColorDrawable(if (dark) 0xF21C1C1E.toInt() else 0xF2F6F6F8.toInt())
            else -> ColorDrawable(if (dark) 0xF51C1C1E.toInt() else 0xF5F2F2F7.toInt())
        }
        if (live) {
            tint.setBackgroundColor(
                when (if (idle) 3 else kind) {
                    0 -> if (dark) 0xCC1C1C1E.toInt() else 0xCCF6F6F8.toInt()
                    1 -> if (dark) 0x33383838 else 0x40FFFFFF
                    3 -> if (dark) 0x8C2A2A2C.toInt() else 0x99FFFFFF.toInt()
                    else -> if (dark) 0xE61C1C1E.toInt() else 0xE6F2F2F7.toInt()
                }
            )
        } else {
            tint.background = solid
        }
        if (idle) { fallback.background = solid; img.alpha = 0f }
        img.visibility = if (live) View.VISIBLE else View.GONE
        if (live && (kind == 1 || kind == 3)) img.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.8f) })
        if (live && Build.VERSION.SDK_INT >= 31) {
            val r = when (if (idle) 3 else kind) { 0 -> 22f; 1 -> 14f; 3 -> 20f; else -> 28f }
            img.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, corner)
            }
        }
        setWillNotDraw(false)
    }

    // posisi tanpa memperhitungkan animasi (translasi/skala)
    private fun origin(v: View, out: IntArray) {
        var x = 0
        var y = 0
        var cur: View? = v
        while (cur != null) {
            x += cur.left; y += cur.top
            val p = cur.parent
            if (p is View) { x -= p.scrollX; y -= p.scrollY; cur = p } else cur = null
        }
        out[0] = x; out[1] = y
    }

    init {
        // mode "once": ambil snapshot latar sekali setiap ukuran berubah, bukan tiap frame
        if (once) addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View?, l: Int, t: Int, r: Int, b: Int, oldL: Int, oldT: Int, oldR: Int, oldB: Int
            ) {
                if (r - l > 0 && b - t > 0 && (r - l != oldR - oldL || b - t != oldB - oldT)) post { refresh() }
            }
        })
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); all.add(this) }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); all.remove(this) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        strokeP.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xDDFFFFFF.toInt(), 0x22FFFFFF, 0x22FFFFFF, 0x77FFFFFF),
            floatArrayOf(0f, 0.42f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        sheenP.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        invalidateOutline()
    }

    override fun dispatchDraw(c: Canvas) {
        super.dispatchDraw(c)
        if (kind == 1 || kind == 3) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawRoundRect(rect, corner, corner, sheenP)
            val i = strokeP.strokeWidth / 2
            rect.set(i, i, width - i, height - i)
            c.drawRoundRect(rect, corner - i, corner - i, strokeP)
        }
    }

    fun setBlurShown(show: Boolean) {
        img.animate().cancel()
        img.animate().alpha(if (show) 1f else 0f).setDuration(if (show) 220L else 90L).start()
    }

    fun refresh() {
        if (!live || width <= 0 || height <= 0 || !isShown) return
        val s = if (kind == 2) 0.07f else 0.1f
        val bw = max(2, (width * s).toInt())
        val bh = max(2, (height * s).toInt())
        var b = bmp
        if (b == null || b.width != bw || b.height != bh) {
            b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bmp = b
            img.setImageBitmap(b)
        }
        if (once || idle) { origin(this, loc); origin(source, sloc) }
        else { getLocationInWindow(loc); source.getLocationInWindow(sloc) }
        cv.setBitmap(b)
        cv.drawColor(bgColor)
        cv.save()
        cv.scale(s, s)
        cv.translate((sloc[0] - loc[0]).toFloat(), (sloc[1] - loc[1]).toFloat())
        try { source.draw(cv) } catch (e: Throwable) { }
        cv.restore()
        img.invalidate()
    }
}

/** ScrollView dengan tinggi maksimum. */
class MaxHeightScrollView(ctx: Context, private val maxH: Int) : ScrollView(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
    }
}

/*
 * Latar kaca tidak boleh ikut menentukan ukuran WRAP_CONTENT (View biasa akan mengambil
 * setinggi layar). Ukuran baru diisi saat induk sudah menentukan ukuran pastinya.
 */
private fun exactOrZero(spec: Int): Int =
    if (View.MeasureSpec.getMode(spec) == View.MeasureSpec.EXACTLY) View.MeasureSpec.getSize(spec) else 0

private class FlatView(ctx: Context) : View(ctx) {
    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(exactOrZero(w), exactOrZero(h)) }
}

private class FlatImage(ctx: Context) : ImageView(ctx) {
    override fun onMeasure(w: Int, h: Int) { setMeasuredDimension(exactOrZero(w), exactOrZero(h)) }
}

/** Ikon unduhan di bar alamat: lingkaran biru berpanah bawah, dengan cincin progres saat mengunduh. */
class DownloadIcon(ctx: Context) : View(ctx) {
    private var active = false
    private var progress = 0f
    private val d = resources.displayMetrics.density
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A84FF.toInt() }
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFF0A84FF.toInt()
    }
    private val arrowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE
    }

    fun update(active: Boolean, progress: Float) {
        this.active = active; this.progress = progress; invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = 9f * d
        c.drawCircle(cx, cy, r, fillP)
        val u = r / 9f
        arrowP.strokeWidth = 1.9f * u
        c.drawLine(cx, cy - 4.5f * u, cx, cy + 3f * u, arrowP)
        c.drawLine(cx - 3.3f * u, cy + 0.2f * u, cx, cy + 3.6f * u, arrowP)
        c.drawLine(cx + 3.3f * u, cy + 0.2f * u, cx, cy + 3.6f * u, arrowP)
        if (active) {
            ringP.strokeWidth = 2f * d
            val rr = r + 3.5f * d
            c.drawArc(RectF(cx - rr, cy - rr, cx + rr, cy + rr), -90f, maxOf(progress, 0.06f) * 360f, false, ringP)
        }
    }
}

/** Kotak ikon situs (favicon, atau huruf awal bila belum ada). */
fun siteTile(ctx: Context, store: Store, url: String, size: Int, radius: Int): View {
    val host = store.host(url)
    val bmp = store.icon(host)
    val box = FrameLayout(ctx)
    if (bmp != null) {
        box.background = GradientDrawable().apply { cornerRadius = radius.toFloat(); setColor(Color.WHITE) }
        box.addView(ImageView(ctx).apply {
            setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(size / 6, size / 6, size / 6, size / 6)
        }, FrameLayout.LayoutParams(-1, -1))
    } else {
        val pal = intArrayOf(
            0xFF8E8E93.toInt(), 0xFF5E7CE2.toInt(), 0xFFE2745E.toInt(),
            0xFF4CAF7D.toInt(), 0xFFB07CE0.toInt(), 0xFFE0B24C.toInt()
        )
        box.background = GradientDrawable().apply {
            cornerRadius = radius.toFloat(); setColor(pal[(host.hashCode() and 0x7fffffff) % pal.size])
        }
        box.addView(TextView(ctx).apply {
            text = host.take(1).uppercase(); setTextColor(Color.WHITE)
            textSize = size / ctx.resources.displayMetrics.density * 0.45f
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }, FrameLayout.LayoutParams(-1, -1))
    }
    return box
}
