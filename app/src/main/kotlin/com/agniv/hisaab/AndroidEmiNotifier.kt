package com.agniv.hisaab

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.data.loan.EmiReminderNotifier
import com.agniv.hisaab.core.domain.loan.EmiReminder
import com.agniv.hisaab.core.domain.loan.ReminderStage
import com.agniv.hisaab.core.domain.repository.LoanRepository
import com.agniv.hisaab.core.domain.repository.SessionRepository
import com.agniv.hisaab.core.domain.usecase.SetEmiPaid
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidEmiNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : EmiReminderNotifier {
        override fun notify(reminder: EmiReminder) {
            // Android 13+: no permission, no notification. The Loans screen asks after the first loan is saved.
            val allowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!allowed) return
            ensureChannel()
            val loan = reminder.loan
            val id = notificationId(loan.id)
            val open =
                PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val markPaid =
                PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, EmiPaidReceiver::class.java)
                        .putExtra(EmiPaidReceiver.EXTRA_LOAN, loan.id)
                        .putExtra(EmiPaidReceiver.EXTRA_MONTH, reminder.month.toString()),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val title =
                when (reminder.stage) {
                    ReminderStage.THREE_DAYS -> R.string.emi_soon_title
                    ReminderStage.ONE_DAY -> R.string.emi_tomorrow_title
                    ReminderStage.TODAY -> R.string.emi_today_title
                }
            val date = reminder.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(title))
                    .setContentText(context.getString(R.string.emi_text, loan.lender, loan.emi.formatted(), date))
                    .setContentIntent(open)
                    .addAction(0, context.getString(R.string.emi_mark_paid), markPaid)
                    .setAutoCancel(true)
                    .build()
            // One id per loan: the "tomorrow" reminder replaces the "3 days" one.
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.emi_channel), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        companion object {
            private const val CHANNEL = "emi"
            private const val ID_BASE = 2_000

            // Keeps ids clear of the budget alerts (1001/1002).
            private const val ID_MASK = 0xFFFF

            fun notificationId(loanId: String): Int = ID_BASE + (loanId.hashCode() and ID_MASK)
        }
    }

/** "Mark paid" on a reminder: ticks that month's EMI without opening the app, then clears the reminder. */
@AndroidEntryPoint
class EmiPaidReceiver : BroadcastReceiver() {
    @Inject lateinit var loans: LoanRepository

    @Inject lateinit var sessions: SessionRepository

    @Inject lateinit var setEmiPaid: SetEmiPaid

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val loanId = intent.getStringExtra(EXTRA_LOAN) ?: return
        val month = intent.getStringExtra(EXTRA_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = sessions.session.first()
                val loan = loans.getLoan(loanId)
                if (session != null && loan != null && !loan.deleted) {
                    setEmiPaid(session, loan, month, paid = true)
                    NotificationManagerCompat.from(context).cancel(AndroidEmiNotifier.notificationId(loanId))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_LOAN = "loan_id"
        const val EXTRA_MONTH = "month"
    }
}
