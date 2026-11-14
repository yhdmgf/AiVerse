package com.aiverse.feature.agent.di

import com.aiverse.feature.agent.data.repository.InstalledAppRepositoryImpl
import com.aiverse.feature.agent.domain.repository.InstalledAppRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 把 [InstalledAppRepositoryImpl] 绑定为 [InstalledAppRepository] 的实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InstalledAppModule {

    @Binds
    @Singleton
    abstract fun bindInstalledAppRepository(
        impl: InstalledAppRepositoryImpl
    ): InstalledAppRepository
}
