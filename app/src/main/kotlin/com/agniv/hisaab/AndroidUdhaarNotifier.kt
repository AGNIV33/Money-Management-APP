package com.agniv.hisaab

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.data.loan.UdhaarReminderNotifier
import com.agniv.hisaab.core.domain.loan.ReminderStage
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.usecase.UdhaarReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidUdhaarNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UdhaarReminderNotifier {
        override fun notify(reminder: UdhaarReminder) {
            val allowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            ensureChannel()
            val e = reminder.item.udhaar
            val id = ID_BASE + (e.id.hashCode() and ID_MASK)
            val open =
                PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val title = if (reminder.stage == ReminderStage.TODAY) R.string.udhaar_due_today_title else R.string.udhaar_due_tomorrow_title
            val text = if (e.direction == UdhaarDirection.I_OWE) R.string.udhaar_pay_text else R.string.udhaar_collect_text
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(title))
                    .setContentText(context.getString(text, e.person, reminder.item.remaining.formatted()))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.udhaar_channel), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL = "udhaar"

            // Clear of budget alerts (1001/1002) and EMI reminders (2000+).
            const val ID_BASE = 300_000
            const val ID_MASK = 0xFFFF
        }
    }
