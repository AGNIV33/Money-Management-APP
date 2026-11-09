package com.agniv.hisaab.di

import com.agniv.hisaab.core.common.dispatcher.DefaultDispatcherProvider
import com.agniv.hisaab.core.common.dispatcher.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DispatchersModule {
    @Binds
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}
