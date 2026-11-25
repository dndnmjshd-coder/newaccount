package com.example.newaccount

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // === عدّل هذي ===
    private val PKG = "com.bandainamcoent.opbrww"   // اسم حزمة اللعبة
    // الشي اللي بنحميه من المسح، نسبة لمجلد اللعبة.
    // "" = المجلد كله، "files/data" = ملف الداتا بس
    private val KEEP_REL = ""
    // =================

    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { setPadding(32, 32, 32, 32) }
        val shizukuBtn = Button(this).apply {
            text = "حساب جديد (Shizuku)"
            setOnClickListener { runShizuku() }
        }
        val rootBtn = Button(this).apply {
            text = "حساب جديد (Root)"
            setOnClickListener { runRoot() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(shizukuBtn); addView(rootBtn); addView(log)
        })
        Shizuku.addRequestPermissionResultListener { _, r ->
            if (r == PackageManager.PERMISSION_GRANTED) runShizuku()
            else log.text = "ما انعطى إذن Shizuku"
        }
    }

    private fun runShizuku() {
        if (!Shizuku.pingBinder()) { log.text = "شغّل Shizuku أول"; return }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(1); return
        }
        go(::execShizuku)
    }

    private fun runRoot() = go(::execRoot)

    private fun go(exec: (String) -> String) {
        log.text = "جاري التنفيذ..."
        thread {
            val out = exec(script())
            runOnUiThread { log.text = out.ifBlank { "تم. افتح اللعبة." } }
        }
    }

    private fun script(): String {
        val base = "/sdcard/Android/data/$PKG"
        val target = if (KEEP_REL.isEmpty()) base else "$base/$KEEP_REL"
        val hold = "/sdcard/.acc_hold"
        return """
            set -e
            rm -rf $hold; mkdir -p $hold
            am force-stop $PKG
            mv "$target" "$hold/item"
            pm clear $PKG
            mkdir -p "${'$'}(dirname "$target")"
            rm -rf "$target"
            mv "$hold/item" "$target"
        """.trimIndent()
    }

    // --- Shizuku: newProcess صار private، فنستدعيه بالـ reflection ---
    private fun execShizuku(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
        read(m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process)
    } catch (t: Throwable) { "خطأ: ${t.message}" }

    // --- Root: لازم تمنح التطبيق صلاحية من Magisk/KernelSU ---
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
