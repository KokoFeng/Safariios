package com.fengios.safari

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
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
    private val kind: Int, dark: Boolean, var bgColor: Int
) : FrameLayout(ctx) {

    companion object { val all = mutableListOf<GlassView>() }

    private val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val tint = View(ctx)
    private var bmp: Bitmap? = null
    private val cv = Canvas()
    private val loc = IntArray(2)
    private val sloc = IntArray(2)
    private val density = resources.displayMetrics.density
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * density }
    private val sheenP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        addView(img, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(tint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        tint.setBackgroundColor(
            when (kind) {
                0 -> if (dark) 0xCC1C1C1E.toInt() else 0xCCF6F6F8.toInt()
                1 -> if (dark) 0x33383838 else 0x40FFFFFF
                else -> if (dark) 0xE61C1C1E.toInt() else 0xE6F2F2F7.toInt()
            }
        )
        if (kind == 1) img.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.8f) })
        if (Build.VERSION.SDK_INT >= 31) {
            val r = when (kind) { 0 -> 22f; 1 -> 14f; else -> 28f }
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
        if (kind == 1) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            c.drawRoundRect(rect, corner, corner, sheenP)
            val i = strokeP.strokeWidth / 2
            rect.set(i, i, width - i, height - i)
            c.drawRoundRect(rect, corner - i, corner - i, strokeP)
        }
    }

    fun refresh() {
        if (width <= 0 || height <= 0 || !isShown) return
        val s = if (kind == 2) 0.07f else 0.1f
        val bw = max(2, (width * s).toInt())
        val bh = max(2, (height * s).toInt())
        var b = bmp
        if (b == null || b.width != bw || b.height != bh) {
            b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            bmp = b
            img.setImageBitmap(b)
        }
        getLocationInWindow(loc)
        source.getLocationInWindow(sloc)
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
