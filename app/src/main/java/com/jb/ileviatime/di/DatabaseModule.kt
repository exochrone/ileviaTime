package com.jb.ileviatime.di

import android.content.Context
import androidx.room.Room
import com.jb.ileviatime.data.local.AppDatabase
import com.jb.ileviatime.data.local.dao.GtfsStaticDao
import com.jb.ileviatime.data.local.dao.PinnedTripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ilevia_time.db"
        ).build()
    }

    @Provides
    fun provideGtfsStaticDao(database: AppDatabase): GtfsStaticDao {
        return database.gtfsStaticDao()
    }

    @Provides
    fun providePinnedTripDao(database: AppDatabase): PinnedTripDao {
        return database.pinnedTripDao()
    }
}
