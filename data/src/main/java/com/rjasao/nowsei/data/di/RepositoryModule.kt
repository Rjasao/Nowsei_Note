package com.rjasao.nowsei.data.di

import com.rjasao.nowsei.data.repository.NotebookRepositoryImpl
// 1. CORREÇÃO: Importar a implementação de PageRepository
import com.rjasao.nowsei.data.repository.PageRepositoryImpl
import com.rjasao.nowsei.data.repository.SectionRepositoryImpl
import com.rjasao.nowsei.domain.repository.NotebookRepository
// 2. CORREÇÃO: Importar a interface de PageRepository
import com.rjasao.nowsei.domain.repository.PageRepository
import com.rjasao.nowsei.domain.repository.SectionRepository
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
    abstract fun bindNotebookRepository(
        notebookRepositoryImpl: NotebookRepositoryImpl
    ): NotebookRepository

    @Binds
    @Singleton
    abstract fun bindSectionRepository(
        sectionRepositoryImpl: SectionRepositoryImpl
    ): SectionRepository

    // 3. CORREÇÃO: Adicionar a função de ligação que estava faltando
    /**
     * Informa ao Hilt que, sempre que uma dependência pedir por um [PageRepository],
     * ele deve fornecer uma instância de [PageRepositoryImpl].
     */
    @Binds
    @Singleton
    abstract fun bindPageRepository(
        pageRepositoryImpl: PageRepositoryImpl
    ): PageRepository
}
