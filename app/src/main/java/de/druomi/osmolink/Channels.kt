package de.druomi.osmolink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.konraditurbe.osmosis.R

/** OsmoLink additions on top of Osmosis: notification channels. */
object Channels {
    const val SETUP = "ol_setup"
    const val STATUS = "ol_status"

    fun init(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(SETUP, ctx.getString(R.string.ol_channel_setup), NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(STATUS, ctx.getString(R.string.ol_channel_status), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
