package de.druomi.osmolink.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import de.druomi.osmolink.adb.PairingService
import de.druomi.osmolink.wifi.DualWifi
import de.druomi.osmolink.wifi.EnsureWorker
import dev.konraditurbe.osmosis.R

/**
 * "Keep home Wi-Fi" screen: hero, live status, a one-time setup in three steps, and the
 * compatibility result. Built in code to keep the diff against upstream Osmosis small.
 */
class DualWifiActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    @Volatile private var busy: String? = null
    private var needsAllow = false
    private var lastState: PairingService.State? = null
    private val ticker = object : Runnable {
        override fun run() {
            val s = PairingService.state.value
            if (s != lastState) { lastState = s; render() }
            main.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scroll = ScrollView(this).apply {
            setBackgroundColor(c(R.color.osmo_bg)); isFillViewport = true; isVerticalScrollBarEnabled = false
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(32)) }
        scroll.addView(content)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
        if (intent.getBooleanExtra(EXTRA_FIX, false)) applyNow()
    }

    override fun onResume() {
        super.onResume()
        render()
        main.post(ticker)
        // Coming back from Settings with Wireless debugging just allowed: try right away.
        if (DualWifi.isPaired(this) && !DualWifi.isActive(this) && DualWifi.wirelessDebuggingOn(this) && busy == null) applyNow()
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(ticker)
    }

    // ---- actions -------------------------------------------------------------------------------

    private fun applyNow() {
        if (busy != null) return
        busy = getString(R.string.ol_busy_applying); render()
        Thread {
            val r = DualWifi.ensure(this)
            main.post {
                busy = null
                if (r.isFailure && !DualWifi.isActive(this)) needsAllow = true
                render()
            }
        }.start()
    }

    private fun turnOff() {
        busy = getString(R.string.ol_busy_off); render()
        Thread { DualWifi.turnOff(this); main.post { busy = null; render() } }.start()
    }

    private fun startPairing() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            return
        }
        PairingService.start(this)
        openWirelessDebugging()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) startPairing()
    }

    private fun openWirelessDebugging() {
        // Highlights the "Wireless debugging" row on most Android builds (same trick Shizuku uses).
        val i = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(i) }
    }

    private fun open(action: String) = runCatching { startActivity(Intent(action)) }

    // ---- UI ------------------------------------------------------------------------------------

    private enum class Tone { OK, WARN, BAD, NEUTRAL }

    private fun render() {
        content.removeAllViews()
        topBar()

        val paired = DualWifi.isPaired(this)
        val active = DualWifi.isActive(this)
        val compat = DualWifi.compat(this)
        val ps = PairingService.state.value

        val tone = when {
            active -> Tone.OK
            !DualWifi.osSupported || compat == false || ps == PairingService.State.Unsupported -> Tone.BAD
            paired -> Tone.WARN
            else -> Tone.NEUTRAL
        }
        hero(tone)

        when (tone) {
            Tone.OK -> statusCard(tone, getString(R.string.ol_chip_on), getString(R.string.ol_status_active), null)
            Tone.BAD -> statusCard(tone, getString(R.string.ol_chip_unsupported),
                getString(if (!DualWifi.osSupported) R.string.ol_status_old_android else R.string.ol_status_unsupported), null)
            Tone.WARN -> statusCard(tone, getString(R.string.ol_chip_off), getString(R.string.ol_status_paired_off),
                if (busy == null) getString(R.string.ol_btn_apply) to { applyNow() } else null)
            Tone.NEUTRAL -> statusCard(tone, getString(R.string.ol_chip_setup), getString(R.string.ol_status_not_setup), null)
        }
        busy?.let { progressLine(it) }

        if (paired && !active && needsAllow && busy == null) {
            stepCard("!", getString(R.string.ol_allow_title), getString(R.string.ol_allow_text), false, true,
                getString(R.string.ol_btn_open_wireless) to { openWirelessDebugging() })
        }

        if (!paired && DualWifi.osSupported && compat != false) {
            sectionLabel(getString(R.string.ol_section_setup))
            val dev = DualWifi.developerOptionsOn(this)
            val wifi = DualWifi.wifiConnected(this)
            stepCard("1", getString(R.string.ol_step1_title), getString(R.string.ol_step1_text), dev, !dev,
                if (dev) null else getString(R.string.ol_btn_open_settings) to { open(Settings.ACTION_DEVICE_INFO_SETTINGS) })
            stepCard("2", getString(R.string.ol_step2_title), getString(R.string.ol_step2_text), wifi, dev && !wifi,
                if (wifi) null else getString(R.string.ol_btn_open_wifi) to { open(Settings.ACTION_WIFI_SETTINGS) })
            val stateLine = when (ps) {
                PairingService.State.Searching -> getString(R.string.ol_pair_state_searching)
                PairingService.State.Found -> getString(R.string.ol_pair_state_found)
                PairingService.State.Failed -> getString(R.string.ol_pair_state_failed)
                PairingService.State.Paired -> getString(R.string.ol_pair_state_paired)
                else -> null
            }
            stepCard("3", getString(R.string.ol_step3_title), getString(R.string.ol_step3_text), false, dev && wifi,
                if (dev && wifi) getString(R.string.ol_btn_start_pairing) to { startPairing() } else null, stateLine)
        }

        if (paired) {
            sectionLabel(getString(R.string.ol_section_details))
            val (_, inner) = card()
            DualWifi.lastLog(this)?.let { detailRow(inner, getString(R.string.ol_detail_last), it) }
            detailRow(inner, getString(R.string.ol_detail_auto), getString(R.string.ol_auto_note))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
            if (active) row.addView(ghostButton(getString(R.string.ol_btn_off)) { turnOff() })
            row.addView(ghostButton(getString(R.string.ol_btn_reset)) {
                DualWifi.setPaired(this, false)
                DualWifi.prefs(this).edit().remove("compat").apply()
                PairingService.state.value = PairingService.State.Idle
                render()
            })
            inner.addView(row)
        }

        footer()
    }

    // ---- building blocks -----------------------------------------------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(res: Int) = ContextCompat.getColor(this, res)
    private fun toneColor(t: Tone) = c(when (t) {
        Tone.OK -> R.color.osmo_green
        Tone.WARN -> R.color.osmo_amber
        Tone.BAD -> R.color.osmo_danger
        Tone.NEUTRAL -> R.color.osmo_accent
    })

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat(); setColor(fill); stroke?.let { setStroke(dp(1), it) }
    }

    private fun text(s: CharSequence, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); setLineSpacing(0f, 1.18f)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }

    private fun topBar() {
        val bar = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(52)) }
        val back = ImageView(this).apply {
            setImageResource(R.drawable.ol_ic_back)
            imageTintList = ColorStateList.valueOf(c(R.color.osmo_ink))
            background = rounded(c(R.color.osmo_surface), 20, c(R.color.osmo_track))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER_VERTICAL)
            contentDescription = getString(R.string.ol_back)
            setOnClickListener { finish() }
        }
        bar.addView(back)
        content.addView(bar)
    }

    private fun hero(t: Tone) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(18), 0, dp(10))
        }
        val accent = toneColor(t)
        val halo = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(ColorUtils.setAlphaComponent(accent, 40))
            }
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
        }
        val badge = ImageView(this).apply {
            setImageResource(R.drawable.ol_ic_dual_wifi)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(ColorUtils.blendARGB(accent, Color.WHITE, 0.18f), ColorUtils.blendARGB(accent, Color.BLACK, 0.22f))
            ).apply { shape = GradientDrawable.OVAL }
            setPadding(dp(20), dp(20), dp(20), dp(20))
            elevation = dp(6).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(80), dp(80), Gravity.CENTER)
        }
        halo.addView(badge)
        box.addView(halo)
        box.addView(text(getString(R.string.ol_title), 26f, c(R.color.osmo_ink), true).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(6)); letterSpacing = -0.01f
        })
        box.addView(text(getString(R.string.ol_intro_short), 15f, c(R.color.osmo_muted)).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), 0, dp(8), 0)
        })
        content.addView(box)
    }

    private fun card(accentStroke: Int? = null): Pair<LinearLayout, LinearLayout> {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(c(R.color.osmo_surface), 24, accentStroke ?: c(R.color.osmo_track))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(1).toFloat()
        }
        content.addView(outer)
        return outer to outer
    }

    private fun chip(label: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(ColorUtils.setAlphaComponent(color, 36), 14)
        setPadding(dp(10), dp(5), dp(12), dp(5))
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) }
        })
        addView(text(label, 13f, color, true).apply { letterSpacing = 0.02f })
    }

    private fun statusCard(t: Tone, chipLabel: String, body: String, action: Pair<String, () -> Unit>?) {
        val color = toneColor(t)
        val (_, inner) = card(ColorUtils.setAlphaComponent(color, 110))
        inner.addView(chip(chipLabel, color), LinearLayout.LayoutParams(-2, -2))
        inner.addView(text(body, 15f, c(R.color.osmo_ink)).apply { setPadding(0, dp(12), 0, 0) })
        action?.let { (label, f) -> inner.addView(primaryButton(label, f)) }
    }

    private fun progressLine(s: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(14), 0, 0)
        }
        row.addView(com.google.android.material.progressindicator.CircularProgressIndicator(this).apply {
            isIndeterminate = true; indicatorSize = dp(18); trackThickness = dp(2)
            setIndicatorColor(c(R.color.osmo_accent))
        })
        row.addView(text(s, 14f, c(R.color.osmo_muted)).apply { setPadding(dp(10), 0, 0, 0) })
        content.addView(row)
    }

    private fun sectionLabel(s: String) = content.addView(text(s.uppercase(), 12f, c(R.color.osmo_muted), true).apply {
        letterSpacing = 0.12f; setPadding(dp(4), dp(26), 0, dp(2))
    })

    private fun stepCard(
        num: String, title: String, body: String, done: Boolean, current: Boolean,
        action: Pair<String, () -> Unit>?, stateLine: String? = null,
    ) {
        val accent = c(R.color.osmo_accent)
        val (_, inner) = card(if (current) ColorUtils.setAlphaComponent(accent, 120) else null)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dot = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (done) setColor(c(R.color.osmo_green))
                else if (current) setColor(accent)
                else { setColor(Color.TRANSPARENT); setStroke(dp(2), c(R.color.osmo_track)) }
            }
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(14) }
        }
        if (done) dot.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_check); imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
        }) else dot.addView(text(num, 14f, if (current) c(R.color.osmo_on_accent) else c(R.color.osmo_muted), true).apply {
            gravity = Gravity.CENTER; layoutParams = FrameLayout.LayoutParams(-1, -1)
        })
        head.addView(dot)
        head.addView(text(title, 16f, if (done) c(R.color.osmo_muted) else c(R.color.osmo_ink), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        inner.addView(head)
        if (!done) {
            inner.addView(text(body, 14f, c(R.color.osmo_muted)).apply { setPadding(dp(44), dp(8), 0, 0) })
            stateLine?.let {
                inner.addView(text(it, 14f, c(R.color.osmo_ink), true).apply {
                    background = rounded(c(R.color.osmo_surface_dim), 14)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(44); topMargin = dp(12) }
                })
            }
            action?.let { (label, f) ->
                inner.addView(primaryButton(label, f).apply {
                    (layoutParams as LinearLayout.LayoutParams).leftMargin = dp(44)
                    if (!current) { alpha = 0.55f }
                })
            }
        }
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = MaterialButton(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        cornerRadius = dp(26); insetTop = 0; insetBottom = 0; minHeight = dp(52)
        backgroundTintList = ColorStateList.valueOf(c(R.color.osmo_accent))
        setTextColor(c(R.color.osmo_on_accent))
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) }
        setOnClickListener { onClick() }
    }

    private fun ghostButton(label: String, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = label; isAllCaps = false; setTextColor(c(R.color.osmo_accent)); setOnClickListener { onClick() }
        }

    private fun detailRow(parent: LinearLayout, label: String, value: String) {
        parent.addView(text(label, 12f, c(R.color.osmo_muted), true).apply { letterSpacing = 0.04f; setPadding(0, dp(6), 0, dp(2)) })
        parent.addView(text(value, 14f, c(R.color.osmo_ink)).apply { setPadding(0, 0, 0, dp(8)) })
    }

    private fun footer() = content.addView(text(getString(R.string.ol_footer), 12f, c(R.color.osmo_muted)).apply {
        gravity = Gravity.CENTER; setPadding(dp(12), dp(28), dp(12), 0)
    })

    companion object {
        const val EXTRA_FIX = "fix"
    }
}

/** Small helper for the main screen: is the feature in a state where the user should be nudged? */
object DualWifiStatus {
    enum class Level { OK, ACTION, INFO }
    data class Summary(val level: Level, val title: Int, val subtitle: Int)

    fun summary(ctx: Context): Summary = when {
        DualWifi.isActive(ctx) -> Summary(Level.OK, R.string.ol_banner_active, R.string.ol_banner_active_sub)
        DualWifi.compat(ctx) == false || !DualWifi.osSupported ->
            Summary(Level.INFO, R.string.ol_banner_unsupported, R.string.ol_banner_unsupported_sub)
        DualWifi.isPaired(ctx) -> Summary(Level.ACTION, R.string.ol_banner_paired_off, R.string.ol_banner_paired_off_sub)
        else -> Summary(Level.ACTION, R.string.ol_banner_setup, R.string.ol_banner_setup_sub)
    }

    fun kick(ctx: Context) {
        if (DualWifi.isPaired(ctx) && !DualWifi.isActive(ctx) && DualWifi.compat(ctx) != false) EnsureWorker.schedule(ctx)
    }
}
