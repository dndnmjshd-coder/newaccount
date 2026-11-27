package com.example.newaccount

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class HakiView(context: Context) : View(context) {

    var targets: () -> List<View> = { emptyList() }
    var zoom: () -> Float = { 1f }

    private class Bolt(
        val segs: List<FloatArray>,
        val born: Long,
        val life: Long,
        val sx: Float,
        val sy: Float
    )

    private val bolts = ArrayList<Bolt>()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pulse = Paint()
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint()
    private val rect = RectF()
    private var nextAmbient = 0L
    private var nextBtn = 0L
    private var flashStart = -10000L
    private var flashPower = 0f
    private val myLoc = IntArray(2)
    private val loc = IntArray(2)
    private val d = resources.displayMetrics.density
    private val halfPi = (PI / 2).toFloat()
    private val eyeShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(255, 255, 235, 235),
            Color.argb(200, 255, 40, 40),
            Color.argb(0, 255, 0, 0)
        ),
        floatArrayOf(0f, 0.35f, 1f),
        Shader.TileMode.CLAMP
    )

    init {
        eyePaint.shader = eyeShader
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pulse.shader = RadialGradient(
            w / 2f, h * 0.38f, h * 0.5f,
            intArrayOf(Color.argb(136, 255, 0, 0), Color.argb(0, 255, 0, 0)),
            null, Shader.TileMode.CLAMP
        )
    }

    fun burst(v: View) {
        val now = SystemClock.uptimeMillis()
        repeat(7) { boltFrom(v, now) }
        flashStart = now
        flashPower = 1f
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()

        pulse.alpha = (255 * (0.55f + 0.35f * sin(now / 700f))).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, pulse)

        drawEyes(canvas, w, h, now)
        drawBorders(canvas, now)

        if (now >= nextAmbient) {
            spawnAmbient(w, h, now)
            nextAmbient = now + Random.nextLong(350L, 900L)
        }
        if (now >= nextBtn) {
            val list = targets().filter { it.isShown && it.width > 0 }
            if (list.isNotEmpty()) boltFrom(list[Random.nextInt(list.size)], now)
            nextBtn = now + Random.nextLong(120L, 350L)
        }

        val iter = bolts.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            val age = now - b.born
            if (age >= b.life) {
                iter.remove()
                continue
            }
            val t = age / b.life.toFloat()
            var a = (1f - t) * (1f - t)
            if (t > 0.2f && Random.nextFloat() < 0.2f) a *= 0.35f
            drawBolt(canvas, b, a)
        }

        val fa = 1f - (now - flashStart) / 180f
        if (fa > 0f) {
            flashPaint.color = Color.argb((flashPower * 90f * fa).toInt(), 255, 20, 20)
            canvas.drawRect(0f, 0f, w, h, flashPaint)
        }
        postInvalidateOnAnimation()
    }

    private fun drawEyes(c: Canvas, w: Float, h: Float, now: Long) {
        val sc = max(w / 736f, h / 1311f)
        val offX = (w - 736f * sc) / 2f
        val offY = (h - 1311f * sc) / 2f
        val z = zoom()
        var p = 0.6f + 0.25f * sin(now / 420f)
        if (Random.nextFloat() < 0.04f) p = 1f
        for (fx in floatArrayOf(376f, 409f)) {
            var x = offX + fx * sc
            var y = offY + 320f * sc
            x = w / 2f + (x - w / 2f) * z
            y = h / 2f + (y - h / 2f) * z
            val k = sc * z
            eyePaint.alpha = (150 * p).toInt().coerceIn(0, 255)
            c.save()
            c.translate(x, y)
            c.scale(20f * k, 12f * k)
            c.drawCircle(0f, 0f, 1f, eyePaint)
            c.restore()
            eyePaint.alpha = (255 * p).toInt().coerceIn(0, 255)
            c.save()
            c.translate(x, y)
            c.scale(9f * k, 5f * k)
            c.drawCircle(0f, 0f, 1f, eyePaint)
            c.restore()
        }
    }

    private fun drawBorders(c: Canvas, now: Long) {
        val list = targets().filter { it.isShown && it.width > 0 }
        if (list.isEmpty()) return
        getLocationOnScreen(myLoc)
        var idx = 0
        for (v in list) {
            v.getLocationOnScreen(loc)
            val l = (loc[0] - myLoc[0]).toFloat()
            val t = (loc[1] - myLoc[1]).toFloat()
            rect.set(l, t, l + v.width, t + v.height)
            val p = 0.55f + 0.45f * sin(now / 500f + idx * 1.3f)
            val rad = 28f * d
            glow.color = Color.argb((70 * p).toInt(), 255, 30, 30)
            glow.strokeWidth = 10f * d
            c.drawRoundRect(rect, rad, rad, glow)
            glow.color = Color.argb((140 * p).toInt(), 255, 70, 70)
            glow.strokeWidth = 4f * d
            c.drawRoundRect(rect, rad, rad, glow)
            idx++
        }
    }

    private fun drawBolt(c: Canvas, b: Bolt, a: Float) {
        glow.color = Color.argb((80 * a).toInt(), 255, 0, 0)
        glow.strokeWidth = 14f * d
        for (s in b.segs) c.drawLines(s, glow)
        glow.color = Color.argb((170 * a).toInt(), 255, 40, 40)
        glow.strokeWidth = 6f * d
        for (s in b.segs) c.drawLines(s, glow)
        glow.color = Color.argb((255 * a).toInt(), 255, 225, 225)
        glow.strokeWidth = 2f * d
        for (s in b.segs) c.drawLines(s, glow)
        fill.color = Color.argb((255 * a).toInt(), 255, 235, 235)
        c.drawCircle(b.sx, b.sy, 2.2f * d * (0.5f + a), fill)
    }

    private fun jag(x0: Float, y0: Float, angle: Float, len: Float, n: Int, jit: Float): FloatArray {
        val pts = FloatArray((n + 1) * 2)
        pts[0] = x0
        pts[1] = y0
        var x = x0
        var y = y0
        var a = angle
        val step = len / n
        for (i in 1..n) {
            a += (Random.nextFloat() - 0.5f) * jit
            x += cos(a) * step
            y += sin(a) * step
            pts[i * 2] = x
            pts[i * 2 + 1] = y
        }
        return pts
    }

    private fun segsOf(p: FloatArray): FloatArray {
        val n = p.size / 2 - 1
        val s = FloatArray(n * 4)
        for (i in 0 until n) {
            s[i * 4] = p[i * 2]
            s[i * 4 + 1] = p[i * 2 + 1]
            s[i * 4 + 2] = p[i * 2 + 2]
            s[i * 4 + 3] = p[i * 2 + 3]
        }
        return s
    }

    private fun spawnAmbient(w: Float, h: Float, now: Long) {
        val left = Random.nextBoolean()
        val x0 = if (left) w * (0.03f + Random.nextFloat() * 0.3f) else w * (0.67f + Random.nextFloat() * 0.3f)
        val y0 = h * Random.nextFloat() * 0.45f
        val ang = halfPi + (Random.nextFloat() - 0.5f) * 0.9f
        val len = h * (0.18f + Random.nextFloat() * 0.3f)
        val main = jag(x0, y0, ang, len, 14, 1.0f)
        val segs = ArrayList<FloatArray>()
        segs.add(segsOf(main))
        repeat(Random.nextInt(2, 5)) {
            val i = Random.nextInt(2, 12)
            val sign = if (Random.nextBoolean()) 1f else -1f
            val bAng = ang + sign * (0.5f + Random.nextFloat() * 0.6f)
            val br = jag(main[i * 2], main[i * 2 + 1], bAng, len * 0.35f, 6, 1.2f)
            segs.add(segsOf(br))
            if (Random.nextFloat() < 0.5f) {
                val j = Random.nextInt(2, 5)
                segs.add(segsOf(jag(br[j * 2], br[j * 2 + 1], bAng - sign * 0.9f, len * 0.15f, 4, 1.3f)))
            }
        }
        bolts.add(Bolt(segs, now, Random.nextLong(260L, 600L), x0, y0))
        if (Random.nextFloat() < 0.35f) {
            flashStart = now
            flashPower = 0.6f
        }
    }

    private fun boltFrom(v: View, now: Long) {
        getLocationOnScreen(myLoc)
        v.getLocationOnScreen(loc)
        val l = (loc[0] - myLoc[0]).toFloat()
        val t = (loc[1] - myLoc[1]).toFloat()
        val r = l + v.width
        val bt = t + v.height
        val inner = (v.width - 40f * d).coerceAtLeast(1f)
        val side = Random.nextInt(4)
        val x0: Float
        val y0: Float
        val base: Float
        when (side) {
            0 -> { x0 = l + 20f * d + inner * Random.nextFloat(); y0 = t; base = -halfPi }
            1 -> { x0 = l + 20f * d + inner * Random.nextFloat(); y0 = bt; base = halfPi }
            2 -> { x0 = l; y0 = t + v.height * Random.nextFloat(); base = PI.toFloat() }
            else -> { x0 = r; y0 = t + v.height * Random.nextFloat(); base = 0f }
        }
        val ang = base + (Random.nextFloat() - 0.5f) * 1.2f
        val len = d * (34f + Random.nextFloat() * 70f)
        val main = jag(x0, y0, ang, len, 7, 1.4f)
        val segs = ArrayList<FloatArray>()
        segs.add(segsOf(main))
        repeat(Random.nextInt(1, 3)) {
            val i = Random.nextInt(2, 6)
            val sign = if (Random.nextBoolean()) 1f else -1f
            segs.add(segsOf(jag(main[i * 2], main[i * 2 + 1], ang + sign * 0.8f, len * 0.5f, 4, 1.3f)))
        }
        bolts.add(Bolt(segs, now, Random.nextLong(160L, 380L), x0, y0))
    }
}
