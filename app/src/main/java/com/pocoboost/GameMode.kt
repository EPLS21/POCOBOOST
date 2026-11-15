package com.pocoboost

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

data class LaunchApp(val label: String, val pkg: String)

object GameMode {

    fun launchables(ctx: Context): List<LaunchApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { LaunchApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /** Fecha processos em segundo plano de todos os outros apps. Retorna quantos foram tentados. */
    fun freeMemory(ctx: Context, gamePkg: String): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        for (app in launchables(ctx)) {
            if (app.pkg == gamePkg) continue
            try {
                am.killBackgroundProcesses(app.pkg)
                n++
            } catch (_: Exception) {
            }
        }
        return n
    }
}
