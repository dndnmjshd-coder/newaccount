package com.example.newaccount

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply {
            setPadding(32, 32, 32, 32)
            text = "اختر الطريقة:"
        }
        btnShizuku = Button(this).apply {
            text = "Shizuku"
            setOnClickListener { pickShizuku() }
        }
        btnRoot = Button(this).apply {
            text = "Root"
            setOnClickListener { useRoot = true; startFlow() }
        }
        btn1 = Button(this).apply {
            text = "1) تغيير اسم المجلد"
            visibility = View.GONE
            setOnClickListener { runStep(step1(), "جاري تغيير الاسم...", this, btn2, "تم تغيير الاسم. الحين اضغط الزر 2.") }
        }
        btn2 = Button(this).apply {
            text = "2) مسح بيانات اللعبة"
            visibility = View.GONE
            setOnClickListener { runStep(step2(), "جاري مسح البيانات...", this, btn3, "تم المسح. الحين اضغط الزر 3.") }
        }
        btn3 = Button(this).apply {
            text = "3) إرجاع الاسم القديم"
            visibility = View.GONE
            setOnClickListener { runStep(step3(), "جاري إرجاع الاسم...", this, null, "خلصت. افتح اللعبة، لازم تفتح بحساب جديد.") }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(log)
            addView(btnShizuku); addView(btnRoot)
            addView(btn1); addView(btn2); addView(btn3)
        })
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
        btn1.isEnabled = true
        log.text = "اضغط الزر 1"
    }

    private fun runStep(cmd: String, busy: String, button: Button, next: Button?, done: String) {
        button.isEnabled = false
        log.text = busy
        thread {
            val out = if (useRoot) execRoot(cmd) else execShizuku(cmd)
            runOnUiThread {
                if (out.contains("STEP_OK")) {
                    button.visibility = View.GONE
                    if (next != null) { next.visibility = View.VISIBLE; next.isEnabled = true }
                    else { btnShizuku.visibility = View.VISIBLE; btnRoot.visibility = View.VISIBLE }
                    log.text = done
                } else {
                    button.isEnabled = true
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
