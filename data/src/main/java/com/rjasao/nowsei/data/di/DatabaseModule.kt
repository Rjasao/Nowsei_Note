package com.rjasao.nowsei.data.di

import android.content.Context
import androidx.room.Room
import com.rjasao.nowsei.data.local.NowseiDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): NowseiDatabase {
        return Room.databaseBuilder(
            context,
            NowseiDatabase::class.java,
            "nowsei_db"
        )
            .addMigrations(
                NowseiDatabase.MIGRATION_4_5,
                NowseiDatabase.MIGRATION_5_6,
                NowseiDatabase.MIGRATION_6_7
            )
            .build()
    }

    @Provides
    fun provideNotebookDao(db: NowseiDatabase) = db.notebookDao()

    @Provides
    fun provideSectionDao(db: NowseiDatabase) = db.sectionDao()

    @Provides
    fun providePageDao(db: NowseiDatabase) = db.pageDao()
}
