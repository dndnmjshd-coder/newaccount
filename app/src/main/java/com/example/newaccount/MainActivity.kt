package com.example.newaccount

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
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
    private var nextAmbient = 0L
    private var nextBtn = 0L
    private val myLoc = IntArray(2)
    private val loc = IntArray(2)
    private val d = resources.displayMetrics.density
    private val halfPi = (PI / 2).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pulse.shader = RadialGradient(
            w / 2f, h * 0.38f, h * 0.5f,
            intArrayOf(Color.argb(136, 255, 0, 0), Color.argb(0, 255, 0, 0)),
            null, Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val h = height.toFloat()

        pulse.alpha = (255 * (0.55f + 0.35f * sin(now / 700f))).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, pulse)

        drawEyes(canvas, w, h, now)

        if (now >= nextAmbient) {
            spawnAmbient(w, h, now)
            nextAmbient = now + Random.nextLong(450L, 1300L)
        }
        if (now >= nextBtn) {
            spawnButton(now)
            nextBtn = now + Random.nextLong(160L, 480L)
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
        postInvalidateOnAnimation()
    }

    private fun drawEyes(c: Canvas, w: Float, h: Float, now: Long) {
        val sc = max(w / 736f, h / 1311f)
        val offX = (w - 736f * sc) / 2f
        val offY = (h - 1311f * sc) / 2f
        val z = zoom()
        val flick = 0.65f + 0.35f * sin(now / 260f)
        for (fx in floatArrayOf(376f, 409f)) {
            var x = offX + fx * sc
            var y = offY + 320f * sc
            x = w / 2f + (x - w / 2f) * z
            y = h / 2f + (y - h / 2f) * z
            val r = 20f * d * z * (0.8f + 0.4f * flick)
            fill.color = Color.argb((60 * flick).toInt(), 255, 30, 30)
            c.drawCircle(x, y, r, fill)
            fill.color = Color.argb((110 * flick).toInt(), 255, 60, 60)
            c.drawCircle(x, y, r * 0.55f, fill)
            fill.color = Color.argb((230 * flick).toInt(), 255, 210, 210)
            c.drawCircle(x, y, r * 0.2f, fill)
        }
    }

    private fun drawBolt(c: Canvas, b: Bolt, a: Float) {
        glow.color = Color.argb((70 * a).toInt(), 255, 0, 0)
        glow.strokeWidth = 11f * d
        for (s in b.segs) c.drawLines(s, glow)
        glow.color = Color.argb((150 * a).toInt(), 255, 40, 40)
        glow.strokeWidth = 5f * d
        for (s in b.segs) c.drawLines(s, glow)
        glow.color = Color.argb((255 * a).toInt(), 255, 215, 215)
        glow.strokeWidth = 1.6f * d
        for (s in b.segs) c.drawLines(s, glow)
        fill.color = Color.argb((220 * a).toInt(), 255, 120, 120)
        c.drawCircle(b.sx, b.sy, 5f * d * (0.5f + a), fill)
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
        val len = h * (0.15f + Random.nextFloat() * 0.25f)
        val main = jag(x0, y0, ang, len, 12, 1.0f)
        val segs = ArrayList<FloatArray>()
        segs.add(segsOf(main))
        repeat(Random.nextInt(1, 3)) {
            val i = Random.nextInt(2, 10)
            val sign = if (Random.nextBoolean()) 1f else -1f
            val bAng = ang + sign * (0.5f + Random.nextFloat() * 0.6f)
            segs.add(segsOf(jag(main[i * 2], main[i * 2 + 1], bAng, len * 0.35f, 6, 1.2f)))
        }
        bolts.add(Bolt(segs, now, Random.nextLong(220L, 520L), x0, y0))
    }

    private fun spawnButton(now: Long) {
        val list = targets().filter { it.isShown && it.width > 0 }
        if (list.isEmpty()) return
        val v = list[Random.nextInt(list.size)]
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
        val len = d * (28f + Random.nextFloat() * 55f)
        val main = jag(x0, y0, ang, len, 6, 1.4f)
        val segs = ArrayList<FloatArray>()
        segs.add(segsOf(main))
        if (Random.nextFloat() < 0.5f) {
            val i = Random.nextInt(2, 5)
            val sign = if (Random.nextBoolean()) 1f else -1f
            segs.add(segsOf(jag(main[i * 2], main[i * 2 + 1], ang + sign * 0.8f, len * 0.5f, 4, 1.3f)))
        }
        bolts.add(Bolt(segs, now, Random.nextLong(140L, 340L), x0, y0))
    }
}

class MainActivity : AppCompatActivity() {

    private val PKG = "com.bandainamcoent.opbrww"
    private val D = "/sdcard/Android/data"
    private val BACKUP = "/sdcard/EX25_Backup"

    private var useRoot = false
    @Volatile private var polling = false

    private lateinit var log: TextView
    private lateinit var modeGroup: LinearLayout
    private lateinit var menuGroup: LinearLayout
    private lateinit var stepsGroup: LinearLayout
    private lateinit var btnNew: Button
    private lateinit var btnBackup: Button
    private lateinit var btnRestore: Button
    private lateinit var btnMenuBack: Button
    private lateinit var btn1: Button
    private lateinit var btn2: Button
    private lateinit var btn3: Button
    private lateinit var btnStepsBack: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun btnParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(56)
    ).apply { topMargin = dp(14) }

    private fun enable(b: Button, on: Boolean) {
        b.isEnabled = on
        b.alpha = if (on) 1f else 0.5f
    }

    private fun newGroup() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
    }

    private fun fancyButton(label: String, secondary: Boolean = false): Button = Button(this).apply {
        text = label
        setAllCaps(false)
        setTextColor(Color.parseColor(if (secondary) "#E9BDBD" else "#FFE5E5"))
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        val colors = if (secondary) {
            intArrayOf(Color.parseColor("#2B0A0A"), Color.parseColor("#140404"))
        } else {
            intArrayOf(Color.parseColor("#D00000"), Color.parseColor("#6A040F"))
        }
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = dp(28).toFloat()
            setStroke(dp(2), Color.parseColor(if (secondary) "#7A2B2B" else "#FF4D4D"))
        }
        elevation = dp(6).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = TextView(this).apply {
            text = "EX25"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FF3B3B"))
            setShadowLayer(14f, 0f, 2f, Color.BLACK)
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "ACCOUNT TOOL"
            textSize = 12f
            setTextColor(Color.parseColor("#D9A5A5"))
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
        }
        val line = View(this).apply {
            setBackgroundColor(Color.parseColor("#FF2A2A"))
        }
        log = TextView(this).apply {
            text = "اختر الطريقة"
            textSize = 15f
            setTextColor(Color.parseColor("#FFE0E0"))
            setShadowLayer(10f, 0f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
        }

        val credit = TextView(this).apply {
            text = "By Yami  •  TikTok @yami_sokehiro"
            textSize = 12f
            setTextColor(Color.parseColor("#FF8A8A"))
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@yami_sokehiro")))
                } catch (t: Throwable) {
                }
            }
        }

        modeGroup = newGroup().apply {
            addView(fancyButton("Shizuku").apply { setOnClickListener { pickShizuku() } }, btnParams())
            addView(fancyButton("Root").apply { setOnClickListener { pickRoot() } }, btnParams())
        }

        btnNew = fancyButton("حساب جديد (خطوة خطوة)").apply {
            setOnClickListener { show(stepsGroup); refreshSteps() }
        }
        btnBackup = fancyButton("نسخة احتياطية").apply {
            setOnClickListener {
                confirm("ينسخ مجلد اللعبة (حوالي 4 جيجا) إلى:\n$BACKUP\nويستبدل النسخة القديمة إن وجدت. ياخذ كم دقيقة. تكمل؟") {
                    runLong(backupCmd(), "جاري النسخ الاحتياطي...", "$BACKUP.tmp", "تم النسخ الاحتياطي. الحجم:")
                }
            }
        }
        btnRestore = fancyButton("استرجاع النسخة الاحتياطية").apply {
            setOnClickListener {
                confirm("هذا يستبدل مجلد اللعبة الحالي بالنسخة المحفوظة في:\n$BACKUP\nياخذ كم دقيقة. تكمل؟") {
                    runLong(restoreCmd(), "جاري الاسترجاع...", "$D/${PKG}_restore", "تم الاسترجاع.")
                }
            }
        }
        btnMenuBack = fancyButton("رجوع", true).apply {
            setOnClickListener { goMode() }
        }
        menuGroup = newGroup().apply {
            addView(btnNew, btnParams())
            addView(btnBackup, btnParams())
            addView(btnRestore, btnParams())
            addView(btnMenuBack, btnParams())
        }

        btn1 = fancyButton("1) تغيير اسم المجلد").apply {
            setOnClickListener { runStep(step1(), "جاري تغيير الاسم...", this, btn2, "تم تغيير الاسم. الحين اضغط الزر 2.") }
        }
        btn2 = fancyButton("2) مسح بيانات اللعبة").apply {
            visibility = View.GONE
            setOnClickListener { runStep(step2(), "جاري مسح البيانات...", this, btn3, "تم المسح. الحين اضغط الزر 3.") }
        }
        btn3 = fancyButton("3) إرجاع الاسم القديم").apply {
            visibility = View.GONE
            setOnClickListener { runStep(step3(), "جاري إرجاع الاسم...", this, null, "خلصت. افتح اللعبة، لازم تفتح بحساب جديد.") }
        }
        btnStepsBack = fancyButton("رجوع", true).apply {
            setOnClickListener { goMenu() }
        }
        stepsGroup = newGroup().apply {
            addView(btn1, btnParams())
            addView(btn2, btnParams())
            addView(btn3, btnParams())
            addView(btnStepsBack, btnParams())
        }

        val bg = ImageView(this).apply {
            setImageResource(R.drawable.shanks_bg)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val shade = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#80000000"),
                    Color.parseColor("#00000000"),
                    Color.parseColor("#F2000000")
                )
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(44), dp(28), dp(28))
            addView(title, fullWidth())
            addView(subtitle, fullWidth())
            addView(line, LinearLayout.LayoutParams(dp(70), dp(2)).apply {
                topMargin = dp(14)
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(
                View(this@MainActivity),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(log, fullWidth().apply { bottomMargin = dp(10) })
            addView(modeGroup, fullWidth())
            addView(menuGroup, fullWidth())
            addView(stepsGroup, fullWidth())
            addView(credit, fullWidth().apply { topMargin = dp(10) })
        }
        val haki = HakiView(this).apply {
            targets = {
                val out = ArrayList<View>()
                for (g in listOf(modeGroup, menuGroup, stepsGroup)) {
                    for (i in 0 until g.childCount) out.add(g.getChildAt(i))
                }
                out
            }
            zoom = { bg.scaleX }
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(haki, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        setContentView(frame)

        for (prop in listOf(View.SCALE_X, View.SCALE_Y)) {
            ObjectAnimator.ofFloat(bg, prop, 1f, 1.12f).apply {
                duration = 10000L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
        show(modeGroup)

        Shizuku.addRequestPermissionResultListener { _, r ->
            if (r == PackageManager.PERMISSION_GRANTED) { useRoot = false; goMenu() }
            else log.text = "ما انعطى إذن Shizuku"
        }
    }

    private fun show(g: LinearLayout) {
        modeGroup.visibility = View.GONE
        menuGroup.visibility = View.GONE
        stepsGroup.visibility = View.GONE
        g.visibility = View.VISIBLE
    }

    private fun goMode() {
        show(modeGroup)
        log.text = "اختر الطريقة"
    }

    private fun goMenu() {
        show(menuGroup)
        log.text = "اختر العملية"
    }

    private fun pickShizuku() {
        if (!Shizuku.pingBinder()) { log.text = "شغّل Shizuku أول"; return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1); return
        }
        useRoot = false
        goMenu()
    }

    private fun pickRoot() {
        log.text = "جاري فحص الروت..."
        thread {
            val ok = execRoot("id").contains("uid=0")
            runOnUiThread {
                if (ok) { useRoot = true; goMenu() }
                else log.text = "ما لقيت صلاحية روت"
            }
        }
    }

    private fun confirm(msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("نعم") { _, _ -> onYes() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun setMenuEnabled(on: Boolean) {
        enable(btnNew, on)
        enable(btnBackup, on)
        enable(btnRestore, on)
        enable(btnMenuBack, on)
    }

    private fun startPoll(path: String, label: String) {
        polling = true
        thread {
            while (polling) {
                val s = exec("du -sm \"$path\" 2>/dev/null | cut -f1").trim()
                runOnUiThread {
                    if (polling && s.isNotEmpty()) log.text = "$label\nتم نسخ $s MB"
                }
                Thread.sleep(3000L)
            }
        }
    }

    private fun runLong(cmd: String, busy: String, pollPath: String, done: String) {
        setMenuEnabled(false)
        log.text = busy
        startPoll(pollPath, busy)
        thread {
            val out = exec(cmd)
            polling = false
            runOnUiThread {
                setMenuEnabled(true)
                if (out.contains("STEP_OK")) {
                    val extra = out.substringAfter("STEP_OK").trim()
                    log.text = if (extra.isEmpty()) done else "$done\n$extra"
                } else {
  
