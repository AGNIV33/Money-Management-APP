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
import com.agniv.hisaab.core.data.loan.GoalReminderNotifier
import com.agniv.hisaab.core.domain.usecase.GoalReminder
import com.agniv.hisaab.core.domain.usecase.GoalReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGoalNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : GoalReminderNotifier {
        override fun notify(reminder: GoalReminder) {
            val allowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            ensureChannel()
            val item = reminder.item
            val goal = item.goal
            // One id per goal: the "date is near" reminder replaces a monthly one still on screen.
            val id = ID_BASE + (goal.id.hashCode() and ID_MASK)
            val open =
                PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val (title, text) =
                when (reminder.kind) {
                    GoalReminderKind.MONTHLY ->
                        context.getString(R.string.goal_month_title, goal.name) to
                            context.getString(R.string.goal_month_text, (item.perMonthNeeded ?: item.remaining).formatted())
                    GoalReminderKind.DATE_NEAR -> {
                        val date = goal.deadline?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()
                        context.getString(R.string.goal_near_title, goal.name) to
                            context.getString(R.string.goal_near_text, item.remaining.formatted(), date)
                    }
                }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            // Low urgency: a nudge, not a bill.
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.goal_channel), NotificationManager.IMPORTANCE_LOW)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL = "goals"

            // Clear of budget alerts (1001/1002), EMI (2000+) and udhaar (300000+) reminders.
            const val ID_BASE = 400_000
            const val ID_MASK = 0xFFFF
        }
    }
