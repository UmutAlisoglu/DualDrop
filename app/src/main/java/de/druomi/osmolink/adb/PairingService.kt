package de.druomi.osmolink.adb

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import de.druomi.osmolink.Channels
import de.druomi.osmolink.ui.DualWifiActivity
import de.druomi.osmolink.wifi.DualWifi
import dev.konraditurbe.osmosis.R
import io.github.muntashirakon.adb.android.AdbMdns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One-time setup. Watches (mDNS) for the "pair with code" service that Settings opens, and lets the
 * user type the 6-digit code straight into our notification, without leaving the Settings screen.
 * After a successful pairing it immediately runs [DualWifi.firstSetup].
 */
@RequiresApi(Build.VERSION_CODES.R)
class PairingService : Service() {
    private var mdns: AdbMdns? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(getString(R.string.ol_pair_searching_title), getString(R.string.ol_pair_searching_text), false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIF_ID, n)
        state.value = State.Searching
        mdns?.stop()
        mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
            if (port > 0 && port != pairingPort) {
                pairingPort = port
                state.value = State.Found
                notify(this, build(getString(R.string.ol_pair_found_title), getString(R.string.ol_pair_found_text), true))
            }
        }.also { it.start() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mdns?.stop()
        super.onDestroy()
    }

    private fun build(title: String, text: String, withInput: Boolean) = builder(this, title, text, withInput).build()

    class CodeReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()
                ?.filter { it.isDigit() } ?: return
            val pending = goAsync()
            notify(ctx, builder(ctx, ctx.getString(R.string.ol_pair_working), "", false).build())
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val ok = runCatching { AdbManager.get(ctx).pair("127.0.0.1", pairingPort, code) }
                        .onFailure { Log.w("OsmoPair", "pair failed", it) }.getOrDefault(false)
                    if (!ok) {
                        state.value = State.Failed
                        notify(ctx, builder(ctx, ctx.getString(R.string.ol_pair_wrong_title),
                            ctx.getString(R.string.ol_pair_wrong_text), true).build())
                        return@launch
                    }
                    DualWifi.setPaired(ctx, true)
                    state.value = State.Paired
                    ctx.stopService(Intent(ctx, PairingService::class.java))
                    // Shell is reachable right now (Wireless debugging is on): finish the setup.
                    val res = DualWifi.firstSetup(ctx)
                    state.value = if (res.getOrDefault(false)) State.Done else State.Unsupported
                    val nm = ctx.getSystemService(NotificationManager::class.java)
                    val title = when {
                        res.isFailure -> R.string.ol_setup_error
                        res.getOrThrow() -> R.string.ol_setup_done
                        else -> R.string.ol_setup_unsupported
                    }
                    nm.notify(NOTIF_ID + 1, Notification.Builder(ctx, Channels.SETUP)
                        .setSmallIcon(R.drawable.ic_signal_in_range)
                        .setContentTitle(ctx.getString(title))
                        .setContentText(ctx.getString(R.string.ol_setup_back))
                        .setContentIntent(PendingIntent.getActivity(ctx, 2,
                            Intent(ctx, DualWifiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE))
                        .setAutoCancel(true).build())
                } finally {
                    pending.finish()
                }
            }
        }
    }

    enum class State { Idle, Searching, Found, Paired, Failed, Done, Unsupported }

    companion object {
        const val NOTIF_ID = 42
        const val KEY_CODE = "code"
        @Volatile var pairingPort = -1
        val state = MutableStateFlow(State.Idle)

        fun start(ctx: Context) {
            pairingPort = -1
            ctx.startForegroundService(Intent(ctx, PairingService::class.java))
        }

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, PairingService::class.java))

        private fun notify(ctx: Context, n: Notification) =
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)

        private fun builder(ctx: Context, title: String, text: String, withInput: Boolean): Notification.Builder {
            val b = Notification.Builder(ctx, Channels.SETUP)
                .setSmallIcon(R.drawable.ic_signal_in_range)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(false)
            if (withInput) {
                val ri = RemoteInput.Builder(KEY_CODE).setLabel(ctx.getString(R.string.ol_pair_code_label)).build()
                val pi = PendingIntent.getBroadcast(
                    ctx, 1, Intent(ctx, CodeReceiver::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                b.addAction(Notification.Action.Builder(null, ctx.getString(R.string.ol_pair_enter_code), pi)
                    .addRemoteInput(ri).build())
            }
            return b
        }
    }
}
