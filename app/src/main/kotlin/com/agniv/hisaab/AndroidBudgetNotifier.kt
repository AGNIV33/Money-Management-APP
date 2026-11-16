package com.agniv.hisaab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.formatted
import com.agniv.hisaab.core.data.budget.BudgetAlertNotifier
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.usecase.BudgetLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
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
            // Off when Android 13+ permission is missing or the user turned this type off in Settings.
            val allowed = Notifications.allowed(context, NotifyType.BUDGET)
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

        override fun notifyCategory(
            level: BudgetLevel,
            category: Category?,
            spent: Money,
            limit: Money,
        ) {
            if (!Notifications.allowed(context, NotifyType.BUDGET) || level == BudgetLevel.OK) return
            ensureChannel()
            val settings = AppSettings(context)
            val words = settings.wrap(context)
            val hindi = (settings.language ?: Locale.getDefault().language) == "hi"
            val name =
                when {
                    category == null -> words.getString(R.string.smart_this_category)
                    hindi && !category.nameHi.isNullOrBlank() -> category.nameHi.orEmpty()
                    else -> category.name
                }
            val over = level == BudgetLevel.OVER
            // One id per category: the 100% alert replaces its own 80% one, not another category's.
            val id = CATEGORY_ID_BASE + ((category?.id ?: name).hashCode() and CATEGORY_ID_MASK)
            val open =
                android.app.PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(words.getString(if (over) R.string.cat_over_title else R.string.cat_warning_title, name))
                    .setContentText(words.getString(R.string.cat_limit_text, spent.formatted(), limit.formatted()))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(context).notify(id, notification)
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

            // Clear of every other notification id (see AndroidGoalNotifier, SmartAlertNotifier).
            const val CATEGORY_ID_BASE = 600_000
            const val CATEGORY_ID_MASK = 0xFFFF
        }
    }
