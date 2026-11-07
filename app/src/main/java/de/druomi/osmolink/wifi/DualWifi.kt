package de.druomi.osmolink.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import de.druomi.osmolink.adb.AdbManager
import dev.konraditurbe.osmosis.R

/**
 * "Keep home Wi-Fi": many phones (Samsung among them) have a Wi-Fi chip that can hold a second,
 * local-only client connection next to the normal one, but ship the Android flag that allows it
 * switched off. With the flag on, joining the camera's Wi-Fi no longer drops home Wi-Fi.
 *
 * The flag can only be set from a shell (adb), and it resets on every reboot. OsmoLink gets that shell
 * from the phone's own Wireless debugging (see [AdbManager]), so no PC, root or Shizuku is needed.
 */
object DualWifi {
    private const val TAG = "OsmoDualWifi"
    private const val FLAG = "config_wifiMultiStaLocalOnlyConcurrencyEnabled"
    const val CMD_ON = "cmd wifi force-overlay-config-value bool $FLAG enabled true"
    // "disabled" only drops the override; set an explicit false so the result is certain.
    const val CMD_OFF = "cmd wifi force-overlay-config-value bool $FLAG enabled false"

    /** Wireless debugging (and its pairing) exists from Android 11. */
    val osSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private const val CHECK = "cmd wifi get-overlay-config-values | grep $FLAG"
    private const val HW_QUERY = "cmd wifi query-interface 2000 com.android.shell STA -new"

    private fun bootCount(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /**
     * The flag lives in memory until the next reboot. The public API
     * (WifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported) turned out to be cached on Samsung
     * and doesn't follow the override live, so we remember the boot in which a shell read-back
     * confirmed the flag.
     */
    fun isActive(ctx: Context): Boolean = prefs(ctx).getInt("active_boot", -2) == bootCount(ctx)

    private fun markActive(ctx: Context, on: Boolean) =
        prefs(ctx).edit().putInt("active_boot", if (on) bootCount(ctx) else -2).apply()

    private fun flagOn(out: String) = out.contains("$FLAG, value: true")

    fun prefs(ctx: Context) = ctx.getSharedPreferences("osmolink", 0)
    fun isPaired(ctx: Context) = prefs(ctx).getBoolean("paired", false)
    fun setPaired(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("paired", v).apply()

    /** null = not checked yet, true = works, false = this phone can't. */
    fun compat(ctx: Context): Boolean? =
        if (prefs(ctx).contains("compat")) prefs(ctx).getBoolean("compat", false) else null

    fun canToggleWirelessDebugging(ctx: Context) =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun wirelessDebuggingOn(ctx: Context) =
        Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1

    fun developerOptionsOn(ctx: Context) =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    fun wifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    /**
     * Right after pairing (shell reachable): grant ourselves what later boots need, ask the Wi-Fi HAL
     * whether a second client interface can coexist (the compatibility check), and if so turn the
     * flag on.
     */
    fun firstSetup(ctx: Context): Result<Boolean> {
        val hw = AdbManager.run(
            ctx,
            "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS; " +
                "dumpsys deviceidle whitelist +${ctx.packageName} >/dev/null; $HW_QUERY",
            20_000
        )
        if (hw.isFailure) return Result.failure(hw.exceptionOrNull()!!)
        val ok = hw.getOrThrow().contains("can be created without destroying")
        prefs(ctx).edit().putBoolean("compat", ok).putString("compat_raw", hw.getOrThrow().takeLast(300)).apply()
        if (ok) {
            val r = AdbManager.run(ctx, "$CMD_ON; $CHECK")
            markActive(ctx, flagOn(r.getOrDefault("")))
        }
        log(ctx, ctx.getString(if (ok) R.string.ol_log_setup_ok else R.string.ol_log_setup_unsupported))
        disconnectAndMaybeDisableDebugging(ctx, wasOn = true)
        return Result.success(ok)
    }

    /**
     * Once per boot: turn Wireless debugging on just long enough (if allowed), apply the flag, then
     * restore Wireless debugging. Needs *some* Wi-Fi connection, because Android only runs Wireless
     * debugging while Wi-Fi is connected.
     */
    @Synchronized
    fun ensure(ctx: Context): Result<String> {
        if (isActive(ctx)) return Result.success("already active")
        if (!isPaired(ctx)) return Result.failure(IllegalStateException("not set up"))
        if (compat(ctx) == false) return Result.failure(IllegalStateException("not supported"))
        val wasOn = wirelessDebuggingOn(ctx)
        if (!wasOn && canToggleWirelessDebugging(ctx)) {
            Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1)
            Thread.sleep(3000)
        }
        val needsCompat = compat(ctx) == null
        val r = AdbManager.run(ctx, (if (needsCompat) "$HW_QUERY; " else "") + "$CMD_ON; $CHECK", 15_000)
        if (needsCompat && r.isSuccess) {
            val hwOk = r.getOrThrow().contains("can be created without destroying")
            prefs(ctx).edit().putBoolean("compat", hwOk).apply()
            if (!hwOk) {
                AdbManager.run(ctx, CMD_OFF)
                disconnectAndMaybeDisableDebugging(ctx, wasOn)
                markActive(ctx, false)
                log(ctx, ctx.getString(R.string.ol_log_setup_unsupported))
                return Result.failure(IllegalStateException("not supported"))
            }
        }
        disconnectAndMaybeDisableDebugging(ctx, wasOn)
        val ok = flagOn(r.getOrDefault(""))
        markActive(ctx, ok)
        log(ctx, ctx.getString(if (ok) R.string.ol_log_auto_on else R.string.ol_log_failed))
        Log.i(TAG, "ensure -> $ok ($r)")
        return if (ok) Result.success("activated")
        else Result.failure(IllegalStateException(r.exceptionOrNull()?.message ?: "not active"))
    }

    fun turnOff(ctx: Context): Result<String> {
        val wasOn = wirelessDebuggingOn(ctx)
        if (!wasOn && canToggleWirelessDebugging(ctx)) {
            Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1); Thread.sleep(3000)
        }
        val r = AdbManager.run(ctx, CMD_OFF)
        disconnectAndMaybeDisableDebugging(ctx, wasOn)
        if (r.isSuccess) markActive(ctx, false)
        log(ctx, ctx.getString(R.string.ol_log_off))
        return r
    }

    private fun disconnectAndMaybeDisableDebugging(ctx: Context, wasOn: Boolean) {
        runCatching { AdbManager.get(ctx).disconnect() }
        if (!wasOn || prefs(ctx).getBoolean("auto_off_debugging", true)) {
            if (canToggleWirelessDebugging(ctx)) Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 0)
        }
    }

    fun log(ctx: Context, s: String) {
        val ts = java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(java.util.Date())
        prefs(ctx).edit().putString("last_log", "$ts · $s").apply()
    }

    fun lastLog(ctx: Context) = prefs(ctx).getString("last_log", null)
}
