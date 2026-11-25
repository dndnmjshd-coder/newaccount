package com.example.newaccount

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val PKG = "com.bandainamcoent.opbrww"
    private val D = "/sdcard/Android/data"

    private var useRoot = false
    private lateinit var log: TextView
    private lateinit var btnShizuku: Button
    private lateinit var btnRoot: Button
    private lateinit var btn1: Button
    private lateinit var btn2: Button
    private lateinit var btn3: Button

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

    private fun fancyButton(label: String): Button = Button(this).apply {
        text = label
        setAllCaps(false)
        setTextColor(Color.parseColor("#FFE9A8"))
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#7B3FE4"), Color.parseColor("#4A1FA8"))
        ).apply {
            cornerRadius = dp(28).toFloat()
            setStroke(dp(2), Color.parseColor("#F2C14E"))
        }
        elevation = dp(6).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#1E0F45")
        window.navigationBarColor = Color.parseColor("#05030D")

        val title = TextView(this).apply {
            text = "EX25"
            textSize = 46f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#FFE08A"))
            letterSpacing = 0.25f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "ACCOUNT TOOL"
            textSize = 12f
            setTextColor(Color.parseColor("#9C8BC9"))
            letterSpacing = 0.4f
            gravity = Gravity.CENTER
        }
        val line = View(this).apply {
            setBackgroundColor(Color.parseColor("#C8901F"))
        }
        log = TextView(this).apply {
            text = "اختر الطريقة"
            textSize = 15f
            setTextColor(Color.parseColor("#E6DDFB"))
            gravity = Gravity.CENTER
        }

        btnShizuku = fancyButton("Shizuku").apply {
            setOnClickListener { pickShizuku() }
        }
        btnRoot = fancyButton("Root").apply {
            setOnClickListener { useRoot = true; startFlow() }
        }
        btn1 = fancyButton("1) تغيير اسم المجلد").apply {
            visibility = View.GONE
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(72), dp(28), dp(28))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1E0F45"), Color.parseColor("#05030D"))
            )
            addView(title, fullWidth())
            addView(subtitle, fullWidth())
            addView(line, LinearLayout.LayoutParams(dp(70), dp(2)).apply {
                topMargin = dp(18)
                bottomMargin = dp(18)
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(log, fullWidth().apply { bottomMargin = dp(22) })
            addView(btnShizuku, btnParams())
            addView(btnRoot, btnParams())
            addView(btn1, btnParams())
            addView(btn2, btnParams())
            addView(btn3, btnParams())
        }
        setContentView(root)

        Shizuku.addRequestPermissionResultListener { _, r ->
            if (r == PackageManager.PERMISSION_GRANTED) { useRoot = false; startFlow() }
            else log.text = "ما انعطى إذن Shizuku"
        }
    }

    private fun pickShizuku() {
        if (!Shizuku.pingBinder()) { log.text = "شغّل Shizuku أول"; return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1); return
        }
        useRoot = false
        startFlow()
    }

    private fun startFlow() {
        btnShizuku.visibility = View.GONE
        btnRoot.visibility = View.GONE
        btn1.visibility = View.VISIBLE
        enable(btn1, true)
        log.text = "اضغط الزر 1"
    }

    private fun runStep(cmd: String, busy: String, button: Button, next: Button?, done: String) {
        enable(button, false)
        log.text = busy
        thread {
            val out = if (useRoot) execRoot(cmd) else execShizuku(cmd)
            runOnUiThread {
                if (out.contains("STEP_OK")) {
                    button.visibility = View.GONE
                    if (next != null) { next.visibility = View.VISIBLE; enable(next, true) }
                    else { btnShizuku.visibility = View.VISIBLE; btnRoot.visibility = View.VISIBLE }
                    log.text = done
                } else {
                    enable(button, true)
                    log.text = "فشلت الخطوة:\n$out"
                }
            }
        }
    }

    private fun step1() = """
        set -e
        [ -d "$D/$PKG" ]
        [ ! -e "$D/${PKG}_hold" ]
        am force-stop $PKG
        mv "$D/$PKG" "$D/${PKG}_hold"
        echo STEP_OK
    """.trimIndent()

    private fun step2() = """
        set -e
        pm clear $PKG
        echo STEP_OK
    """.trimIndent()

    private fun step3() = """
        set -e
        rm -rf "$D/$PKG"
        mv "$D/${PKG}_hold" "$D/$PKG"
        echo STEP_OK
    """.trimIndent()

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
