package com.pocoboost

import android.content.Context
import android.provider.Settings

object Optimizer {

    const val PKG = "com.pocoboost"

    /** Exige WRITE_SECURE_SETTINGS (concedida uma vez via ADB). */
    fun setAnimations(ctx: Context, scale: Float): Boolean = try {
        val cr = ctx.contentResolver
        Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, scale)
        Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, scale)
        Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        true
    } catch (e: Exception) {
        false
    }

    val GRANT_CMDS = """
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS
adb shell appops set $PKG GET_USAGE_STATS allow
""".trim()

    /** Título -> comandos (todos reversíveis e seguros, sem root). */
    val ADB: List<Pair<String, String>> = listOf(
        "1) Dar permissões ao app (só uma vez)" to GRANT_CMDS,
        "2) Recompilar todos os apps para abrirem mais rápido (leva alguns minutos)" to
            "adb shell cmd package compile -m speed-profile -a",
        "3) Rodar a otimização de segundo plano do sistema" to
            "adb shell cmd package bg-dexopt-job",
        "4) Limpar o cache de todos os apps" to
            "adb shell pm trim-caches 999G",
        "5) Animações mais rápidas (o botão do app faz o mesmo)" to
            "adb shell settings put global window_animation_scale 0.5\n" +
            "adb shell settings put global transition_animation_scale 0.5\n" +
            "adb shell settings put global animator_duration_scale 0.5"
    )
}
