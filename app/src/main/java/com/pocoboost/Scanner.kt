package com.pocoboost

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.view.Display
import java.io.File

enum class Level { OK, WARN, BAD, INFO }

data class Finding(
    val level: Level,
    val title: String,
    val detail: String,
    val fix: String = ""
)

data class ScanResult(val score: Int, val findings: List<Finding>)

object Scanner {

    fun batteryIntent(ctx: Context): Intent? =
        ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun batteryTempC(i: Intent?): Float =
        (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasSecureSettings(ctx: Context): Boolean =
        ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hottestZone(): Pair<String, Float>? {
        var best: Pair<String, Float>? = null
        val zones = File("/sys/class/thermal")
            .listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        for (z in zones) {
            try {
                var v = File(z, "temp").readText().trim().toFloat()
                if (v > 1000f) v /= 1000f
                val name = File(z, "type").readText().trim()
                val cur = best
                if (v in 10f..130f && (cur == null || v > cur.second)) best = name to v
            } catch (_: Exception) {
                // sensor não legível sem root: ignora
            }
        }
        return best
    }

    fun scan(ctx: Context): ScanResult {
        val f = mutableListOf<Finding>()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager

        // ---- Bateria / temperatura ----
        val bi = batteryIntent(ctx)
        val temp = batteryTempC(bi)
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val plugged = (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val health = bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0) ?: 0

        val tempFix = if (plugged)
            "Carregando aquece muito: tire a capa, não jogue carregando e evite carregar sob sol."
        else
            "Feche apps pesados, baixe brilho e use 60/90 Hz até esfriar."
        val tempLevel = when {
            temp >= 45f -> Level.BAD
            temp >= 40f -> Level.WARN
            else -> Level.OK
        }
        f += Finding(
            tempLevel,
            "Temperatura da bateria: %.1f °C".format(temp),
            "Bateria em $pct%" + if (plugged) " (carregando)." else ".",
            if (tempLevel == Level.OK) "" else tempFix
        )

        if (health != 0 && health != BatteryManager.BATTERY_HEALTH_GOOD &&
            health != BatteryManager.BATTERY_HEALTH_UNKNOWN
        ) {
            f += Finding(
                Level.BAD, "Saúde da bateria anormal",
                "O sistema reporta a bateria fora do estado normal (código $health).",
                "Se persistir, leve à assistência: bateria ruim causa calor e desligamentos."
            )
        }

        // ---- Throttling térmico do sistema ----
        val ts = pm.currentThermalStatus
        val tsName = listOf("Normal", "Leve", "Moderado", "Severo", "Crítico", "Emergência", "Desligamento")
            .getOrElse(ts) { "Desconhecido" }
        f += Finding(
            when {
                ts >= 2 -> Level.BAD
                ts == 1 -> Level.WARN
                else -> Level.OK
            },
            "Estado térmico do sistema: $tsName",
            if (ts >= 1) "O Android já está reduzindo CPU/GPU para esfriar (throttling). Isso causa travadas."
            else "Sem limitação térmica agora.",
            if (ts >= 1) "Deixe o aparelho descansar, feche jogos/câmera/hotspot e tire a capa." else ""
        )

        if (Build.VERSION.SDK_INT >= 30) {
            val h = pm.getThermalHeadroom(10)
            if (!h.isNaN()) {
                f += Finding(
                    if (h >= 1f) Level.BAD else if (h >= 0.85f) Level.WARN else Level.INFO,
                    "Margem térmica: %.0f%% do limite".format(h * 100),
                    "Em 100% o sistema começa a limitar o desempenho."
                )
            }
        }

        hottestZone()?.let { (name, v) ->
            f += Finding(Level.INFO, "Sensor mais quente: %.1f °C".format(v), "Zona: $name")
        }

        // ---- RAM ----
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val usedPct = (100 - (mi.availMem * 100 / mi.totalMem)).toInt()
        val availGb = mi.availMem / 1024.0 / 1024.0 / 1024.0
        val ramLevel = when {
            mi.lowMemory || usedPct >= 90 -> Level.BAD
            usedPct >= 80 -> Level.WARN
            else -> Level.OK
        }
        f += Finding(
            ramLevel, "RAM em uso: $usedPct%",
            "Livre: %.1f GB.".format(availGb),
            if (ramLevel == Level.OK) "" else
                "Pouca RAM livre faz o sistema matar e recarregar apps o tempo todo (travadas + calor). Reinicie o celular e limite apps em segundo plano."
        )

        // ---- Armazenamento ----
        val st = StatFs(Environment.getDataDirectory().path)
        val freePct = (st.availableBytes * 100 / st.totalBytes).toInt()
        val freeGb = st.availableBytes / 1024.0 / 1024.0 / 1024.0
        val stLevel = when {
            freePct < 8 -> Level.BAD
            freePct < 15 -> Level.WARN
            else -> Level.OK
        }
        f += Finding(
            stLevel, "Armazenamento livre: $freePct%",
            "%.1f GB livres.".format(freeGb),
            if (stLevel == Level.OK) "" else
                "Armazenamento quase cheio deixa o UFS lento e o sistema trava. Libere pelo menos 15%."
        )

        // ---- Economia de bateria ----
        if (pm.isPowerSaveMode) {
            f += Finding(
                Level.WARN, "Economia de bateria ligada",
                "Limita CPU e atividade em segundo plano.",
                "Desligue para ter desempenho máximo."
            )
        }

        // ---- Animações ----
        val cr = ctx.contentResolver
        val anim = Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        f += Finding(
            if (anim > 1f) Level.WARN else Level.INFO,
            "Escala de animação: ${anim}x",
            "Valores menores deixam a interface com sensação de mais rápida.",
            if (anim > 0.5f) "Use o botão 'Animações rápidas' abaixo." else ""
        )

        // ---- Taxa de atualização ----
        try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val hz = dm.getDisplay(Display.DEFAULT_DISPLAY).refreshRate
            f += Finding(
                Level.INFO, "Tela rodando a %.0f Hz".format(hz),
                "Taxas altas (120 Hz) consomem mais e esquentam mais.",
                if (hz > 100f && temp >= 40f) "Se estiver quente, troque para 60/90 Hz em Tela > Taxa de atualização." else ""
            )
        } catch (_: Exception) {
        }

        // ---- Quantidade de apps ----
        try {
            val apps = ctx.packageManager.getInstalledApplications(0)
                .count { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            f += Finding(
                if (apps > 120) Level.WARN else Level.INFO,
                "$apps apps instalados",
                "Muitos apps rodando serviços próprios sobrecarregam o sistema.",
                if (apps > 120) "Desinstale o que você não usa." else ""
            )
        } catch (_: Exception) {
        }

        // ---- Apps mais usados (causa comum de calor) ----
        if (hasUsageAccess(ctx)) {
            try {
                val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 24L * 3600 * 1000, now
                ) ?: emptyList()
                val top = stats
                    .filter { it.totalTimeInForeground > 0 && it.packageName != ctx.packageName }
                    .sortedByDescending { it.totalTimeInForeground }
                    .take(5)
                if (top.isNotEmpty()) {
                    val pmgr = ctx.packageManager
                    val lines = top.joinToString("\n") {
                        val label = try {
                            pmgr.getApplicationInfo(it.packageName, 0).loadLabel(pmgr).toString()
                        } catch (_: Exception) {
                            it.packageName
                        }
                        "• $label: ${it.totalTimeInForeground / 60000} min"
                    }
                    val heavy = top.first().totalTimeInForeground / 60000 >= 120
                    f += Finding(
                        if (heavy) Level.WARN else Level.INFO,
                        "Apps mais usados nas últimas 24h",
                        lines,
                        if (heavy) "Jogos, redes sociais e câmera por horas mantêm CPU/GPU no máximo: é a causa mais comum de calor." else ""
                    )
                }
            } catch (_: Exception) {
            }
        } else {
            f += Finding(
                Level.INFO, "Acesso de uso não concedido",
                "Sem ele o app não vê quais apps mais esquentam o aparelho.",
                "Toque em 'Dar acesso de uso' abaixo e ative o PocoBoost."
            )
        }

        val score = (100 -
            20 * f.count { it.level == Level.BAD } -
            8 * f.count { it.level == Level.WARN }).coerceIn(0, 100)
        return ScanResult(score, f)
    }
}
