package com.ayrindigital.drme2edemo.di

import com.ayrindigital.drme2edemo.data.api.ApiService
import com.ayrindigital.drme2edemo.data.auth.AuthRepository
import com.ayrindigital.drme2edemo.data.auth.TokenStore
import com.ayrindigital.drme2edemo.data.catalog.CatalogRepository
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
    fun provideAuthRepository(
        apiService: ApiService,
        tokenStore: TokenStore,
    ): AuthRepository {
        return AuthRepository(apiService, tokenStore)
    }

    @Provides
    @Singleton
    fun provideCatalogRepository(apiService: ApiService): CatalogRepository {
        return CatalogRepository(apiService)
    }
}
