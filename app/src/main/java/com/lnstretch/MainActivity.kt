package com.lnstretch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class MainActivity : AppCompatActivity() {

    private val ffPackages = listOf(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    )

    private var selectedPercent: Int = 15

    // Views
    private lateinit var tvStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvSliderLabel: TextView
    private lateinit var seekbarStretch: SeekBar
    private lateinit var btnStretch: Button
    private lateinit var btnUnstretch: Button
    private lateinit var btnOptimize: Button
    private lateinit var btnOpenFF: Button
    private lateinit var btnShizuku: Button

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            showToast("Shizuku autorizado!")
            updateShizukuStatus()
        } else {
            showToast("Permissao negada pelo Shizuku")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        tvStatus        = findViewById(R.id.tvStatus)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        tvSliderLabel   = findViewById(R.id.tvSliderLabel)
        seekbarStretch  = findViewById(R.id.seekbarStretch)
        btnStretch      = findViewById(R.id.btnStretch)
        btnUnstretch    = findViewById(R.id.btnUnstretch)
        btnOptimize     = findViewById(R.id.btnOptimize)
        btnOpenFF       = findViewById(R.id.btnOpenFF)
        btnShizuku      = findViewById(R.id.btnShizuku)

        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        setupSlider()
        setupButtons()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    // ── SLIDER ──────────────────────────────────────────────────────────────

    private fun setupSlider() {
        seekbarStretch.max = 30
        seekbarStretch.progress = 5
        updateSliderLabel(15)

        seekbarStretch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedPercent = 10 + progress
                updateSliderLabel(selectedPercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateSliderLabel(percent: Int) {
        val metrics = getRealMetrics()
        val newWidth = (metrics.widthPixels * (1 + percent / 100.0)).toInt()
        tvSliderLabel.text = "+${percent}%   ${metrics.widthPixels}px > ${newWidth}px"
    }

    // ── BUTTONS ─────────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnStretch.setOnClickListener   { applyStretch() }
        btnUnstretch.setOnClickListener { removeStretch() }
        btnOptimize.setOnClickListener  { optimizeFF() }
        btnOpenFF.setOnClickListener    { openFreefire() }
        btnShizuku.setOnClickListener   { requestShizukuPermission() }
    }

    // ── STRETCH ─────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun applyStretch() {
        if (!isShizukuReady()) {
            tvStatus.text = "Shizuku nao autorizado!\nClique em AUTORIZAR primeiro."
            showToast("Autorize o Shizuku primeiro!")
            return
        }
        val metrics  = getRealMetrics()
        val origW    = metrics.widthPixels
        val origH    = metrics.heightPixels
        val newWidth = (origW * (1 + selectedPercent / 100.0)).toInt()

        if (runShizukuCommand("wm size ${newWidth}x${origH}")) {
            saveBackup(origW, origH, metrics.densityDpi)
            tvStatus.text = "Tela esticada +${selectedPercent}%!\n${origW}x${origH} > ${newWidth}x${origH}\n\nAbra o Free Fire agora!"
            showToast("Esticado +${selectedPercent}%!")
        } else {
            tvStatus.text = "Erro ao esticar. Verifique o Shizuku."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun removeStretch() {
        if (!isShizukuReady()) { showToast("Autorize o Shizuku primeiro!"); return }
        val prefs = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
        val origW = prefs.getInt("orig_width", 0)
        val origH = prefs.getInt("orig_height", 0)
        val cmd   = if (origW > 0) "wm size ${origW}x${origH}" else "wm size reset"
        if (runShizukuCommand(cmd)) {
            clearBackup()
            tvStatus.text = "Tela restaurada!\n${origW}x${origH}"
            showToast("Tela restaurada!")
        } else {
            tvStatus.text = "Erro ao restaurar."
        }
    }

    // ── OTIMIZAR ────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun optimizeFF() {
        tvStatus.text = "Otimizando..."
        btnOptimize.isEnabled = false

        Thread {
            var ffFound = false
            if (isShizukuReady()) {
                runShizukuCommand("am kill-all")
                ffPackages.forEach { pkg ->
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        ffFound = true
                        runShizukuCommand("am send-trim-memory $pkg COMPLETE")
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
            }
            System.gc()
            Runtime.getRuntime().gc()
            val free  = Runtime.getRuntime().freeMemory() / 1024 / 1024
            val total = Runtime.getRuntime().totalMemory() / 1024 / 1024

            runOnUiThread {
                btnOptimize.isEnabled = true
                val ffStatus = if (ffFound) "Free Fire otimizado" else "FF nao encontrado"
                tvStatus.text = "Otimizacao concluida!\n$ffStatus\nRAM livre: ${free}MB / ${total}MB"
                showToast("Otimizado!")
            }
        }.start()
    }

    // ── ABRIR FF ────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun openFreefire() {
        for (pkg in ffPackages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                tvStatus.text = "Abrindo Free Fire...\nTela +${selectedPercent}% ativa"
                return
            } catch (_: Exception) {}
        }
        tvStatus.text = "Free Fire nao encontrado!\nInstale o jogo primeiro."
        showToast("Free Fire nao instalado!")
    }

    // ── SHIZUKU ─────────────────────────────────────────────────────────────

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                showToast("Shizuku nao esta rodando!\nAbra o app Shizuku primeiro.")
                tvStatus.text = "Shizuku nao encontrado!\nInstale e inicie o app Shizuku."
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                showToast("Shizuku ja esta autorizado!")
                updateShizukuStatus()
                return
            }
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            tvStatus.text = "Erro Shizuku: ${e.message}"
        }
    }

    private fun runShizukuCommand(command: String): Boolean {
        return try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", command), null, null
            )
            process.waitFor()
            true
        } catch (_: Exception) { false }
    }

    // ── STATUS ──────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun updateStatus() {
        val metrics = getRealMetrics()
        val ffInstalled = ffPackages.any { pkg ->
            try { packageManager.getPackageInfo(pkg, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }
        val ffStatus = if (ffInstalled) "Free Fire detectado" else "Free Fire nao instalado"
        tvStatus.text = "Resolucao: ${metrics.widthPixels}x${metrics.heightPixels}\n$ffStatus"
        updateShizukuStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun updateShizukuStatus() {
        try {
            val running = Shizuku.pingBinder()
            val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            tvShizukuStatus.text = when {
                granted -> "Shizuku: Autorizado"
                running -> "Shizuku: Ativo (clique Autorizar)"
                else    -> "Shizuku: Nao encontrado"
            }
            btnShizuku.isEnabled = running && !granted
        } catch (_: Exception) {
            tvShizukuStatus.text = "Shizuku: Nao instalado"
        }
    }

    // ── UTIL ────────────────────────────────────────────────────────────────

    private fun getRealMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun saveBackup(width: Int, height: Int, dpi: Int) {
        getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit()
            .putInt("orig_width", width)
            .putInt("orig_height", height)
            .putInt("orig_dpi", dpi)
            .apply()
    }

    private fun clearBackup() {
        getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
