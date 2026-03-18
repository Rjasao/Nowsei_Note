package com.rjasao.nowsei.presentation.section_detail

import com.rjasao.nowsei.domain.model.Page

object SectionDetailContract {

    data class State(
        val sectionId: String = "",
        val sectionTitle: String = "",
        val pages: List<Page> = emptyList(),
        val isLoading: Boolean = true,
        val isSearchActive: Boolean = false,
        val searchQuery: String = "",
        val isSyncing: Boolean = false
    ) {
        val filteredPages: List<Page>
            get() = if (searchQuery.isBlank()) {
                pages
            } else {
                pages.filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }
    }

    sealed interface Event {
        object OnAddPageClick : Event
        data class OnDuplicatePageClick(val page: Page) : Event
        data class OnDeletePageClick(val page: Page) : Event
        data class OnEditPageClick(val page: Page) : Event
        data class OnMovePage(val from: Int, val to: Int) : Event
        object OnConfirmDialog : Event
        object OnDismissDialog : Event
        data class OnDialogTitleChanged(val newTitle: String) : Event
        object OnToggleSearch : Event
        data class OnSearchQueryChanged(val query: String) : Event
        object OnSyncClick : Event
    }

    sealed interface DialogState {
        data class Delete(val page: Page) : DialogState
        data class Rename(val page: Page, val newTitle: String) : DialogState
    }

}
