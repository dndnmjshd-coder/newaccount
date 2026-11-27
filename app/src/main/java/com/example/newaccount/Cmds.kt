package com.example.newaccount

import rikka.shizuku.Shizuku

const val PKG = "com.bandainamcoent.opbrww"
const val D = "/sdcard/Android/data"
const val BACKUP = "/sdcard/EX25_Backup"

fun step1() = """
    [ -d "$D/$PKG" ] || { echo "مجلد اللعبة غير موجود"; exit 1; }
    [ ! -e "$D/${PKG}_hold" ] || { echo "المجلد _hold موجود من قبل"; exit 1; }
    am force-stop $PKG
    mv "$D/$PKG" "$D/${PKG}_hold" || exit 1
    echo STEP_OK
""".trimIndent()

fun step2() = """
    pm clear $PKG || { echo "فشل مسح البيانات"; exit 1; }
    echo STEP_OK
""".trimIndent()

fun step3() = """
    [ -e "$D/${PKG}_hold" ] || { echo "ما في مجلد _hold"; exit 1; }
    rm -rf "$D/$PKG"
    mv "$D/${PKG}_hold" "$D/$PKG" || exit 1
    echo STEP_OK
""".trimIndent()

fun backupCmd() = """
    [ -d "$D/$PKG" ] || { echo "مجلد اللعبة غير موجود"; exit 1; }
    rm -rf "$BACKUP.tmp"
    mkdir -p "$BACKUP.tmp" || exit 1
    cp -a "$D/$PKG/." "$BACKUP.tmp/" || { echo "فشل النسخ (ممكن المساحة ما تكفي)"; exit 1; }
    rm -rf "$BACKUP"
    mv "$BACKUP.tmp" "$BACKUP" || exit 1
    echo STEP_OK
    du -sh "$BACKUP" | cut -f1
""".trimIndent()

fun restoreCmd() = """
    [ -d "$BACKUP" ] || { echo "ما في نسخة احتياطية"; exit 1; }
    rm -rf "$D/${PKG}_restore"
    cp -a "$BACKUP" "$D/${PKG}_restore" || { echo "فشل النسخ (ممكن المساحة ما تكفي)"; rm -rf "$D/${PKG}_restore"; exit 1; }
    am force-stop $PKG
    rm -rf "$D/$PKG"
    mv "$D/${PKG}_restore" "$D/$PKG" || exit 1
    echo STEP_OK
""".trimIndent()

fun execShizuku(cmd: String): String = try {
    val m = Shizuku::class.java.getDeclaredMethod(
        "newProcess",
        Array<String>::class.java, Array<String>::class.java, String::class.java
    ).apply { isAccessible = true }
    read(m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process)
} catch (t: Throwable) { "خطأ: ${t.message}" }

fun execRoot(cmd: String): String = try {
    read(Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)))
} catch (t: Throwable) { "ما لقيت root: ${t.message}" }

fun read(p: Process): String {
    val o = p.inputStream.bufferedReader().readText()
    val e = p.errorStream.bufferedReader().readText()
    p.waitFor()
    return o + e
}
