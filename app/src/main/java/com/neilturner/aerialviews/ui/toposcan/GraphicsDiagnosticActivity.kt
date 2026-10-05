package com.neilturner.aerialviews.ui.toposcan

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.WindowHelper

class GraphicsDiagnosticActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val steps =
        listOf(RenderSize(1920, 1080), RenderSize(3840, 2160)).flatMap { size ->
            GraphicsDiagnostic.Input.entries.map { size to it }
        }
    private val results = mutableListOf<String>()
    private lateinit var root: FrameLayout
    private var surface: ToposcanView? = null
    private var step = 0
    private var pendingResult = ""
    private var gpu = ""
    private var closing = false
    private var acceptResult = false
    private val endTest = Runnable { finish() }
    private var stageTimeout: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowHelper.hideSystemUI(window)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        setContentView(root)
        handler.postDelayed(endTest, 120_000)
        showStep()
    }

    private fun showStep() {
        acceptResult = false
        stageTimeout?.let(handler::removeCallbacks)
        surface?.release()
        surface = null
        root.removeAllViews()
        if (step == steps.size) {
            persist()
            val panel = panel()
            panel.addView(label(getString(R.string.toposcan_graphics_result), 20f))
            panel.addView(
                ScrollView(this).apply { addView(label(GeneralPrefs.toposcanGraphicsStatus, 14f)) },
                LinearLayout.LayoutParams(-1, 0, 1f),
            )
            val close =
                Button(this).apply {
                    setText(R.string.toposcan_graphics_close)
                    setOnClickListener { finish() }
                }
            panel.addView(close)
            root.addView(panel, FrameLayout.LayoutParams(-1, -1))
            close.requestFocus()
            return
        }

        val (size, input) = steps[step]
        val generation = step
        pendingResult = "Waiting for GPU"
        persist()
        val panel = panel()
        panel.addView(label("${step + 1}/${steps.size} - ${size.width}x${size.height} / ${input.name}", 20f))
        val status =
            label(pendingResult, 14f).apply {
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            }
        panel.addView(status)
        val actions = LinearLayout(this)
        val buttons =
            listOf(
                R.string.toposcan_graphics_visible to "visible",
                R.string.toposcan_graphics_black to "black",
                R.string.toposcan_graphics_distorted to "distorted",
            ).map { (title, observation) ->
                Button(this).apply {
                    setText(title)
                    isEnabled = false
                    setOnClickListener {
                        if (acceptResult) {
                            acceptResult = false
                            results += "${size.width}x${size.height} / ${input.name}: $observation\n$pendingResult"
                            step++
                            showStep()
                        }
                    }
                    actions.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }
        panel.addView(actions)
        val onResult: (GraphicsDiagnostic.Result) -> Unit = result@{ report ->
            if (closing || step != generation || acceptResult) return@result
            if (report.gpu.isNotBlank()) gpu = report.gpu
            pendingResult = report.detail
            status.text = "$gpu\n${report.detail}"
            stageTimeout?.let(handler::removeCallbacks)
            persist()
            // Let the first tested buffer reach the display before accepting an observation.
            handler.postDelayed({
                if (!closing && step == generation) {
                    acceptResult = true
                    buttons.forEach { it.isEnabled = true }
                    buttons.first().requestFocus()
                }
            }, 750)
        }
        surface = ToposcanView(this, graphicsDiagnostic = GraphicsDiagnostic(size, input, onResult))
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        stageTimeout =
            Runnable { onResult(GraphicsDiagnostic.Result("TIMEOUT: no GPU result after 15 seconds")) }
                .also { handler.postDelayed(it, 15_000) }
    }

    private fun persist() {
        val header = "${Build.MODEL} / Android ${Build.VERSION.RELEASE} / ${BuildConfig.VERSION_NAME}\nSDR graphics test\n$gpu"
        val pending =
            if (step < steps.size) {
                val (size, input) = steps[step]
                "\n${size.width}x${size.height} / ${input.name}: unconfirmed\n$pendingResult"
            } else {
                ""
            }
        GeneralPrefs.toposcanGraphicsStatus =
            "$header\n${results.joinToString("\n\n")}$pending\n\n" + getString(R.string.toposcan_graphics_scope)
    }

    private fun panel(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }

    private fun label(
        value: String,
        size: Float,
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
        }

    override fun onStop() {
        closing = true
        handler.removeCallbacksAndMessages(null)
        surface?.release()
        surface = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onStop()
        finish()
    }
}
