package com.rjasao.nowsei.data.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rjasao.nowsei.data.json.ContentBlockAdapter
import com.rjasao.nowsei.domain.model.ContentBlock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JsonModule {

    @Provides
    @Singleton
    fun provideGson(): Gson =
        GsonBuilder()
            .setLenient()
            // ✅ Data estável no JSON (evita “bug” por formato/localidade)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            // ✅ Suporte a sealed class (polimórfico) - evita crash do Gson
            .registerTypeAdapter(ContentBlock::class.java, ContentBlockAdapter())
            .create()
}
