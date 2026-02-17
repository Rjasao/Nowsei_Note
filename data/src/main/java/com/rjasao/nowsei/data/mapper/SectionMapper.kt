package com.rjasao.nowsei.data.mapper

// 1. Importar os modelos de ambas as camadas
import com.rjasao.nowsei.data.local.entity.SectionEntity
import com.rjasao.nowsei.domain.model.Section
import java.util.Date
/**
 * Converte um objeto SectionEntity (camada de dados) para um objeto Section (camada de domínio).
 */
// In data/src/main/java/com/rjasao/nowsei/data/mapper/SectionMapper.kt
fun SectionEntity.toDomain(): Section {
    return Section(
        id = this.id,
        notebookId = this.notebookId,
        title = this.title,
        content = this.content,
        lastModifiedAt = Date(this.lastModifiedAt)
    )
}

fun Section.toEntity(): SectionEntity {
    return SectionEntity(
        id = this.id,
        notebookId = this.notebookId,
        title = this.title,
        content = this.content, // CORRIGIDO: Passa o conteúdo do modelo.
        createdAt = this.lastModifiedAt.time, // CORRIGIDO: Usa 'lastModifiedAt' para preencher 'createdAt'.
        lastModifiedAt = this.lastModifiedAt.time
    )
}
