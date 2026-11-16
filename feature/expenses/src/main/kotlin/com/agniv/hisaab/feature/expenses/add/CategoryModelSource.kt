package com.agniv.hisaab.feature.expenses.add

import android.content.Context
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import com.agniv.hisaab.core.ml.category.CategoryClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Hands the Add expense screen the on-device category model, read from the app's files off the main thread. */
class CategoryModelSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dispatchers: DispatcherProvider,
    ) {
        suspend fun get(): CategoryClassifier? = withContext(dispatchers.default) { CategoryClassifier.fromAssets(context) }
    }
