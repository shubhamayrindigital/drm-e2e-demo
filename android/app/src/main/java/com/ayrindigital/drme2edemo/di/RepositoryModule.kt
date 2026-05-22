package com.ayrindigital.drme2edemo.di

import android.content.Context
import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.auth.AuthRepository
import com.ayrindigital.drme2edemo.data.auth.TokenStore
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
import com.ayrindigital.drme2edemo.data.downloads.DownloadRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideContext(application: android.app.Application): Context = application.applicationContext

    @Provides
    @Singleton
    fun provideAuthRepository(
        apiService: ApiService,
        tokenStore: TokenStore,
    ): AuthRepository = AuthRepository(apiService, tokenStore)

    @Provides
    @Singleton
    fun provideCatalogRepository(apiService: ApiService): CatalogRepository = CatalogRepository(apiService)

    @Provides
    @Singleton
    fun provideDownloadRepository(context: Context, apiService: ApiService): DownloadRepository =
        DownloadRepository(context, apiService)
}
