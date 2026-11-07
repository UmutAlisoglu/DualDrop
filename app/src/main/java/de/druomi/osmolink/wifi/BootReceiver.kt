package de.druomi.osmolink.wifi

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.druomi.osmolink.Channels
import de.druomi.osmolink.ui.DualWifiActivity
import dev.konraditurbe.osmosis.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** After a reboot or an app update: as soon as any Wi-Fi is connected, switch "keep home Wi-Fi" back on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (DualWifi.isPaired(ctx)) EnsureWorker.schedule(ctx)
    }
}

class EnsureWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        if (!DualWifi.isPaired(ctx) || DualWifi.compat(ctx) == false) return@withContext Result.success()
        if (DualWifi.ensure(ctx).isSuccess) {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_LOST)
            Result.success()
        } else if (runAttemptCount < 5) {
            Result.retry()
        } else {
            // Typical cause: connected to a Wi-Fi where Wireless debugging was never allowed.
            notifyNeedsTap(ctx)
            Result.failure()
        }
    }

    companion object {
        const val NOTIF_LOST = 43

        fun schedule(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<EnsureWorker>()
                // Wireless debugging only runs while Wi-Fi is connected.
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("ensure", ExistingWorkPolicy.REPLACE, req)
        }

        fun notifyNeedsTap(ctx: Context) {
            val pi = PendingIntent.getActivity(
                ctx, 3, Intent(ctx, DualWifiActivity::class.java).putExtra(DualWifiActivity.EXTRA_FIX, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            ctx.getSystemService(NotificationManager::class.java).notify(
                NOTIF_LOST,
                Notification.Builder(ctx, Channels.STATUS)
                    .setSmallIcon(R.drawable.ic_signal_in_range)
                    .setContentTitle(ctx.getString(R.string.ol_lost_title))
                    .setContentText(ctx.getString(R.string.ol_lost_text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
