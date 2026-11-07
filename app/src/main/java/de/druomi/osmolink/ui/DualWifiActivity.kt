package de.druomi.osmolink.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import de.druomi.osmolink.adb.PairingService
import de.druomi.osmolink.wifi.DualWifi
import de.druomi.osmolink.wifi.EnsureWorker
import dev.konraditurbe.osmosis.R

/**
 * "Keep home Wi-Fi" screen: status, a one-time setup in three simple steps, and the
 * compatibility result. Built in code to keep the diff against upstream Osmosis small.
 */
class DualWifiActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    @Volatile private var busy: String? = null
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
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(24)) }
        scroll.addView(content)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
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

    private var needsAllow = false

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

    private fun render() {
        content.removeAllViews()
        title(getString(R.string.ol_title))
        body(getString(R.string.ol_intro))

        val paired = DualWifi.isPaired(this)
        val active = DualWifi.isActive(this)
        val compat = DualWifi.compat(this)
        val ps = PairingService.state.value

        when {
            active -> status(R.color.osmo_green, getString(R.string.ol_status_active), null)
            !DualWifi.osSupported -> status(R.color.osmo_danger, getString(R.string.ol_status_old_android), null)
            compat == false || ps == PairingService.State.Unsupported ->
                status(R.color.osmo_danger, getString(R.string.ol_status_unsupported), null)
            paired -> status(R.color.osmo_amber, getString(R.string.ol_status_paired_off),
                if (busy == null) getString(R.string.ol_btn_apply) to { applyNow() } else null)
            else -> status(R.color.osmo_muted, getString(R.string.ol_status_not_setup), null)
        }
        busy?.let { body("⏳ $it") }

        if (paired && !active && needsAllow && busy == null) {
            step("!", getString(R.string.ol_allow_title), getString(R.string.ol_allow_text), false,
                getString(R.string.ol_btn_open_wireless) to { openWirelessDebugging() })
        }

        if (!paired && DualWifi.osSupported && compat != false) {
            val dev = DualWifi.developerOptionsOn(this)
            val wifi = DualWifi.wifiConnected(this)
            step("1", getString(R.string.ol_step1_title), getString(R.string.ol_step1_text), dev,
                if (dev) null else getString(R.string.ol_btn_open_settings) to { open(Settings.ACTION_DEVICE_INFO_SETTINGS) })
            step("2", getString(R.string.ol_step2_title), getString(R.string.ol_step2_text), wifi,
                if (wifi) null else getString(R.string.ol_btn_open_wifi) to { open(Settings.ACTION_WIFI_SETTINGS) })
            val pairText = getString(R.string.ol_step3_text) + when (ps) {
                PairingService.State.Searching -> "\n\n" + getString(R.string.ol_pair_state_searching)
                PairingService.State.Found -> "\n\n" + getString(R.string.ol_pair_state_found)
                PairingService.State.Failed -> "\n\n" + getString(R.string.ol_pair_state_failed)
                PairingService.State.Paired -> "\n\n" + getString(R.string.ol_pair_state_paired)
                else -> ""
            }
            step("3", getString(R.string.ol_step3_title), pairText, false,
                if (dev && wifi) getString(R.string.ol_btn_start_pairing) to { startPairing() } else null)
        }

        if (paired) {
            DualWifi.lastLog(this)?.let { small(getString(R.string.ol_last_log, it)) }
            small(getString(R.string.ol_auto_note))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (active) row.addView(textButton(getString(R.string.ol_btn_off)) { turnOff() })
            row.addView(textButton(getString(R.string.ol_btn_reset)) {
                DualWifi.setPaired(this, false)
                DualWifi.prefs(this).edit().remove("compat").apply()
                PairingService.state.value = PairingService.State.Idle
                render()
            })
            content.addView(row)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun ink() = ContextCompat.getColor(this, R.color.osmo_ink)
    private fun muted() = ContextCompat.getColor(this, R.color.osmo_muted)

    private fun title(s: String) = content.addView(TextView(this).apply {
        text = s; textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink()); setPadding(0, dp(8), 0, dp(8))
    })

    private fun body(s: String) = content.addView(TextView(this).apply {
        text = s; textSize = 15f; setTextColor(ink()); setLineSpacing(0f, 1.15f); setPadding(0, dp(4), 0, dp(10))
    })

    private fun small(s: String) = content.addView(TextView(this).apply {
        text = s; textSize = 13f; setTextColor(muted()); setPadding(0, dp(8), 0, 0)
    })

    private fun textButton(label: String, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = label; setOnClickListener { onClick() }
        }

    private fun card(): Pair<MaterialCardView, LinearLayout> {
        val c = MaterialCardView(this).apply {
            radius = dp(18).toFloat(); strokeWidth = dp(2); useCompatPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        c.addView(inner)
        content.addView(c)
        return c to inner
    }

    private fun status(colorRes: Int, text: String, action: Pair<String, () -> Unit>?) {
        val (c, inner) = card()
        val color = ContextCompat.getColor(this, colorRes)
        c.strokeColor = color
        inner.addView(TextView(this).apply {
            this.text = text; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink())
        })
        action?.let { (label, f) ->
            inner.addView(MaterialButton(this).apply {
                this.text = label; setOnClickListener { f() }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) }
            })
        }
    }

    private fun step(num: String, t: String, text: String, done: Boolean, action: Pair<String, () -> Unit>?) {
        val (c, inner) = card()
        c.strokeColor = ContextCompat.getColor(this, if (done) R.color.osmo_green else R.color.osmo_track)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            this.text = if (done) "✓" else num
            textSize = 16f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@DualWifiActivity, if (done) R.color.osmo_green else R.color.osmo_accent))
            layoutParams = LinearLayout.LayoutParams(dp(28), -2)
        })
        head.addView(TextView(this).apply { this.text = t; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink()) })
        inner.addView(head)
        if (!done) {
            inner.addView(TextView(this).apply {
                this.text = text; textSize = 14f; setTextColor(ink()); setLineSpacing(0f, 1.15f); setPadding(dp(28), dp(6), 0, 0)
            })
            action?.let { (label, f) ->
                inner.addView(MaterialButton(this).apply {
                    this.text = label; setOnClickListener { f() }
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10); leftMargin = dp(28) }
                })
            }
        }
    }

    companion object {
        const val EXTRA_FIX = "fix"
    }
}

/** Small helper for the main screen: is the feature in a state where the user should be nudged? */
object DualWifiStatus {
    fun summary(ctx: android.content.Context): Pair<Boolean, Int> = when {
        DualWifi.isActive(ctx) -> true to R.string.ol_banner_active
        DualWifi.compat(ctx) == false || !DualWifi.osSupported -> false to R.string.ol_banner_unsupported
        DualWifi.isPaired(ctx) -> false to R.string.ol_banner_paired_off
        else -> false to R.string.ol_banner_setup
    }

    fun kick(ctx: android.content.Context) {
        if (DualWifi.isPaired(ctx) && !DualWifi.isActive(ctx) && DualWifi.compat(ctx) != false) EnsureWorker.schedule(ctx)
    }
}
