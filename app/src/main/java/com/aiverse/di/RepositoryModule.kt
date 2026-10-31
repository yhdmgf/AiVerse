package com.aiverse.di

import com.aiverse.feature.settings.domain.repository.AIProviderRepository
import com.aiverse.feature.settings.data.repository.AIProviderRepositoryImpl
import com.aiverse.feature.credentials.domain.repository.CredentialRepository
import com.aiverse.feature.credentials.data.repository.FileCredentialRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAIProviderRepository(
        aiProviderRepositoryImpl: AIProviderRepositoryImpl
    ): AIProviderRepository

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(
        fileCredentialRepository: FileCredentialRepository
    ): CredentialRepository
}
