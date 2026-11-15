package com.pocoboost

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuRunner {

    fun running(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission(code: Int) {
        try {
            Shizuku.requestPermission(code)
        } catch (_: Throwable) {
        }
    }

    /** Roda um comando com a permissão de shell (uid 2000) via Shizuku. Chamar fora da thread principal. */
    fun run(cmd: String): String = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        p.waitFor()
        (out + err).trim()
    } catch (e: Throwable) {
        "erro: " + (e.cause?.message ?: e.message)
    }
}
