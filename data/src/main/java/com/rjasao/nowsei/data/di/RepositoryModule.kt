package com.rjasao.nowsei.data.di

import com.rjasao.nowsei.data.remote.DriveSyncDataSource
import com.rjasao.nowsei.data.remote.GoogleDriveService
import com.rjasao.nowsei.data.local.FilePageTombstoneStore
import com.rjasao.nowsei.data.local.PageTombstoneStore
import com.rjasao.nowsei.data.repository.NotebookRepositoryImpl
import com.rjasao.nowsei.data.repository.PageRepositoryImpl
import com.rjasao.nowsei.data.repository.SectionRepositoryImpl
import com.rjasao.nowsei.data.repository.SyncRepositoryImpl
import com.rjasao.nowsei.domain.repository.NotebookRepository
import com.rjasao.nowsei.domain.repository.PageRepository
import com.rjasao.nowsei.domain.repository.SectionRepository
import com.rjasao.nowsei.domain.repository.SyncRepository
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
    abstract fun bindDriveSyncDataSource(
        impl: GoogleDriveService
    ): DriveSyncDataSource

    @Binds
    @Singleton
    abstract fun bindPageTombstoneStore(
        impl: FilePageTombstoneStore
    ): PageTombstoneStore

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        impl: SyncRepositoryImpl
    ): SyncRepository

    @Binds
    @Singleton
    abstract fun bindNotebookRepository(
        impl: NotebookRepositoryImpl
    ): NotebookRepository

    @Binds
    @Singleton
    abstract fun bindSectionRepository(
        impl: SectionRepositoryImpl
    ): SectionRepository

    @Binds
    @Singleton
    abstract fun bindPageRepository(
        impl: PageRepositoryImpl
    ): PageRepository
}
