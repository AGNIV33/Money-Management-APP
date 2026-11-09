package com.agniv.hisaab

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.data.budget.BudgetAlertNotifier
import com.agniv.hisaab.core.domain.usecase.BudgetLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBudgetNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BudgetAlertNotifier {
        override fun notify(
            level: BudgetLevel,
            spent: Money,
            budget: Money,
        ) {
            // Android 13+: no permission, no notification. The home screen asks for it when a budget is set.
            val allowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!allowed || level == BudgetLevel.OK) return
            ensureChannel()
            val over = level == BudgetLevel.OVER
            val open =
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(if (over) R.string.budget_over_title else R.string.budget_warning_title))
                    .setContentText(
                        context.getString(
                            if (over) R.string.budget_over_text else R.string.budget_warning_text,
                            spent.formatted(),
                            budget.formatted(),
                        ),
                    ).setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(if (over) ID_OVER else ID_WARNING, notification)
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel =
                NotificationChannel(CHANNEL, context.getString(R.string.budget_channel), NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private companion object {
            const val CHANNEL = "budget"
            const val ID_WARNING = 1001
            const val ID_OVER = 1002
        }
    }
