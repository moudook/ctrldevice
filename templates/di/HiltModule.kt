package com.ctrldevice.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureModule {

    @Provides
    @Singleton
    fun provideExampleDependency(): String {
        return "Example Dependency"
    }
    
    // Add your Provides here...
}
