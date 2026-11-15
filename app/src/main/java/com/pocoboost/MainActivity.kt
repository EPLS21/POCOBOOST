package com.pocoboost

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val bg = 0xFF0E1116.toInt()
    private val card = 0xFF1A1F27.toInt()
    private val txt = 0xFFE8EAED.toInt()
    private val sub = 0xFF9AA0A6.toInt()
    private val acc = 0xFFFF6A00.toInt()

    private lateinit var tempView: TextView
    private lateinit var scoreView: TextView
    private lateinit var secureStatus: TextView
    private lateinit var results: LinearLayout
    private lateinit var shizukuStatus: TextView
    private lateinit var shizukuLog: TextView

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            tempView.text = "Bateria agora: %.1f °C".format(Scanner.batteryTempC(i))
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun round(color: Int, r: Int = 14) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    private fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = round(acc, 12)
        setOnClickListener { onClick() }
    }

    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun copy(s: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("adb", s))
        toast("Copiado")
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            toast("Essa tela não existe neste aparelho")
        }
    }

    private fun animations(scale: Float) {
        if (!Scanner.hasSecureSettings(this)) {
            copy(Optimizer.GRANT_CMDS)
            toast("Falta a permissão. Rode no PC os comandos (já copiados) com o celular conectado por USB.")
            return
        }
        toast(if (Optimizer.setAnimations(this, scale)) "Animações em ${scale}x" else "Não foi possível alterar")
        runScan()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(32), dp(16), dp(40))
        }
        sv.addView(root)
        setContentView(sv)

        root.addView(tv("PocoBoost F7", 26f, txt, true))
        root.addView(tv("Calor e travamentos · ${Build.MODEL}", 13f, sub))

        tempView = tv("Bateria agora: -- °C", 18f, acc, true)
        root.addView(tempView, lp(16))
        scoreView = tv("", 40f, txt, true)
        root.addView(scoreView, lp(8))
        root.addView(btn("Escanear agora") { runScan() }, lp(12))

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results, lp(12))

        root.addView(tv("Otimizar", 20f, txt, true), lp(28))
        secureStatus = tv("", 12f, sub)
        root.addView(secureStatus, lp(4))
        root.addView(btn("🎮 Modo Jogo (liberar RAM e abrir o jogo)") { showGamePicker() }, lp(8))
        root.addView(btn("Animações rápidas (0.5x)") { animations(0.5f) }, lp(8))
        root.addView(btn("Desativar animações (máximo)") { animations(0f) }, lp(8))
        root.addView(btn("Restaurar animações (1x)") { animations(1f) }, lp(8))
        root.addView(btn("Dar acesso de uso (melhora o scan)") {
            open(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }, lp(8))
        root.addView(btn("Otimização de bateria dos apps") {
            open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }, lp(8))
        root.addView(btn("Economia de bateria") {
            open(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }, lp(8))
        root.addView(btn("Armazenamento") {
            open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        }, lp(8))
        root.addView(btn("Opções do desenvolvedor") {
            open(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        }, lp(8))

        root.addView(tv("⚡ Ativar tudo pelo Shizuku", 20f, txt, true), lp(28))
        shizukuStatus = tv("", 12f, sub)
        root.addView(shizukuStatus, lp(4))
        root.addView(btn("1) Conceder permissões ao app") {
            runShizuku(
                "Concedendo permissões",
                listOf(
                    "pm grant com.pocoboost android.permission.WRITE_SECURE_SETTINGS",
                    "appops set com.pocoboost GET_USAGE_STATS allow"
                )
            )
        }, lp(8))
        root.addView(btn("2) Otimizar tudo (demora alguns minutos)") {
            runShizuku(
                "Otimizando",
                listOf(
                    "settings put global window_animation_scale 0.5",
                    "settings put global transition_animation_scale 0.5",
                    "settings put global animator_duration_scale 0.5",
                    "pm trim-caches 999G",
                    "cmd package bg-dexopt-job",
                    "cmd package compile -m speed-profile -a"
                )
            )
        }, lp(8))
        shizukuLog = tv("", 11f, sub).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(shizukuLog, lp(8))

        root.addView(tv("Comandos ADB (manual, se preferir)", 20f, txt, true), lp(28))
        root.addView(
            tv(
                "Rode no PC com depuração USB ativa. Toque em Copiar e cole no terminal.",
                12f, sub
            ), lp(4)
        )
        for ((title, cmds) in Optimizer.ADB) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = round(card)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            box.addView(tv(title, 14f, txt, true))
            box.addView(tv(cmds, 11f, sub).apply {
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
            }, lp(6))
            box.addView(btn("Copiar") { copy(cmds) }, lp(8))
            root.addView(box, lp(10))
        }
    }

    private fun shizukuReady(): Boolean {
        if (!ShizukuRunner.running()) {
            toast("Shizuku não está ligado. Abra o Shizuku e toque em Iniciar.")
            return false
        }
        if (!ShizukuRunner.hasPermission()) {
            ShizukuRunner.requestPermission(77)
            toast("Aceite a permissão do Shizuku e toque no botão de novo.")
            return false
        }
        return true
    }

    private fun runShizuku(title: String, cmds: List<String>) {
        if (!shizukuReady()) return
        shizukuLog.text = "$title...\n"
        Thread {
            for (c in cmds) {
                val out = ShizukuRunner.run(c)
                runOnUiThread {
                    shizukuLog.append("$ " + c + "\n" + (if (out.isEmpty()) "ok" else out) + "\n")
                }
            }
            runOnUiThread {
                shizukuLog.append("Pronto.\n")
                runScan()
            }
        }.start()
    }

    private fun showGamePicker() {
        val apps = GameMode.launchables(this)
        val names = apps.map { it.label }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Escolha o jogo")
            .setItems(names) { _, i ->
                val g = apps[i]
                val n = GameMode.freeMemory(this, g.pkg)
                if (Scanner.hasSecureSettings(this)) Optimizer.setAnimations(this, 0.5f)
                toast("$n apps de fundo fechados. Abrindo ${g.label}...")
                val launch = packageManager.getLaunchIntentForPackage(g.pkg)
                if (launch != null) startActivity(launch)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        runScan()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
    }

    private fun levelColor(l: Level) = when (l) {
        Level.OK -> 0xFF34A853.toInt()
        Level.WARN -> 0xFFFBBC04.toInt()
        Level.BAD -> 0xFFEA4335.toInt()
        Level.INFO -> 0xFF8AB4F8.toInt()
    }

    private fun runScan() {
        shizukuStatus.text = when {
            !ShizukuRunner.running() -> "Shizuku: desligado (abra o Shizuku e toque em Iniciar)"
            !ShizukuRunner.hasPermission() -> "Shizuku: ligado, falta autorizar o PocoBoost"
            else -> "Shizuku: pronto ✔"
        }
        secureStatus.text = if (Scanner.hasSecureSettings(this))
            "Permissão avançada: ativa ✔"
        else
            "Permissão avançada: falta (veja o passo 1 dos comandos ADB)"

        val r = Scanner.scan(this)
        scoreView.text = "${r.score}/100"
        scoreView.setTextColor(
            when {
                r.score >= 80 -> levelColor(Level.OK)
                r.score >= 55 -> levelColor(Level.WARN)
                else -> levelColor(Level.BAD)
            }
        )

        results.removeAllViews()
        // problemas primeiro
        val order = listOf(Level.BAD, Level.WARN, Level.INFO, Level.OK)
        for (fd in r.findings.sortedBy { order.indexOf(it.level) }) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = round(card)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            box.addView(tv("● " + fd.title, 15f, levelColor(fd.level), true))
            box.addView(tv(fd.detail, 13f, txt), lp(4))
            if (fd.fix.isNotEmpty()) box.addView(tv("→ " + fd.fix, 13f, acc), lp(6))
            results.addView(box, lp(10))
        }
    }
}
