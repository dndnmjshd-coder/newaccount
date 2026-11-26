package com.example.newaccount

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
        }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(bg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(shade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
                    log.text = "فشلت العملية:\n${out.ifBlank { "بدون تفاصيل" }}"
                }
            }
        }
    }

    private fun refreshSteps() {
        btn1.visibility = View.VISIBLE
        enable(btn1, true)
        btn2.visibility = View.GONE
        btn3.visibility = View.GONE
        log.text = "اضغط الزر 1"
        thread {
            val out = exec("[ -e \"$D/${PKG}_hold\" ] && echo HOLD")
            runOnUiThread {
                if (out.contains("HOLD")) {
                    btn1.visibility = View.GONE
                    btn2.visibility = View.VISIBLE
                    enable(btn2, true)
                    btn3.visibility = View.VISIBLE
                    enable(btn3, true)
                    log.text = "مجلد اللعبة معاد تسميته من قبل. كمّل من الزر 2، أو اضغط 3 لإرجاعه."
                }
            }
        }
    }

    private fun runStep(cmd: String, busy: String, button: Button, next: Button?, done: String) {
        enable(button, false)
        enable(btnStepsBack, false)
        log.text = busy
        thread {
            val out = exec(cmd)
            runOnUiThread {
                enable(btnStepsBack, true)
                if (out.contains("STEP_OK")) {
                    button.visibility = View.GONE
                    if (next != null) { next.visibility = View.VISIBLE; enable(next, true) }
                    else goMenu()
                    log.text = done
                } else {
                    enable(button, true)
                    log.text = "فشلت الخطوة:\n${out.ifBlank { "بدون تفاصيل" }}"
                }
            }
        }
    }

    private fun step1() = """
        [ -d "$D/$PKG" ] || { echo "مجلد اللعبة غير موجود"; exit 1; }
        [ ! -e "$D/${PKG}_hold" ] || { echo "المجلد _hold موجود من قبل"; exit 1; }
        am force-stop $PKG
        mv "$D/$PKG" "$D/${PKG}_hold" || exit 1
        echo STEP_OK
    """.trimIndent()

    private fun step2() = """
        pm clear $PKG || { echo "فشل مسح البيانات"; exit 1; }
        echo STEP_OK
    """.trimIndent()

    private fun step3() = """
        [ -e "$D/${PKG}_hold" ] || { echo "ما في مجلد _hold"; exit 1; }
        rm -rf "$D/$PKG"
        mv "$D/${PKG}_hold" "$D/$PKG" || exit 1
        echo STEP_OK
    """.trimIndent()

    private fun backupCmd() = """
        [ -d "$D/$PKG" ] || { echo "مجلد اللعبة غير موجود"; exit 1; }
        rm -rf "$BACKUP.tmp"
        mkdir -p "$BACKUP.tmp" || exit 1
        cp -a "$D/$PKG/." "$BACKUP.tmp/" || { echo "فشل النسخ (ممكن المساحة ما تكفي)"; exit 1; }
        rm -rf "$BACKUP"
        mv "$BACKUP.tmp" "$BACKUP" || exit 1
        echo STEP_OK
        du -sh "$BACKUP" | cut -f1
    """.trimIndent()

    private fun restoreCmd() = """
        [ -d "$BACKUP" ] || { echo "ما في نسخة احتياطية"; exit 1; }
        rm -rf "$D/${PKG}_restore"
        cp -a "$BACKUP" "$D/${PKG}_restore" || { echo "فشل النسخ (ممكن المساحة ما تكفي)"; rm -rf "$D/${PKG}_restore"; exit 1; }
        am force-stop $PKG
        rm -rf "$D/$PKG"
        mv "$D/${PKG}_restore" "$D/$PKG" || exit 1
        echo STEP_OK
    """.trimIndent()

    private fun exec(cmd: String): String = if (useRoot) execRoot(cmd) else execShizuku(cmd)

    private fun execShizuku(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        read(m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process)
    } catch (t: Throwable) { "خطأ: ${t.message}" }

    private fun execRoot(cmd: String): String = try {
        read(Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)))
    } catch (t: Throwable) { "ما لقيت root: ${t.message}" }

    private fun read(p: Process): String {
        val o = p.inputStream.bufferedReader().readText()
        val e = p.errorStream.bufferedReader().readText()
        p.waitFor()
        return o + e
    }
}
