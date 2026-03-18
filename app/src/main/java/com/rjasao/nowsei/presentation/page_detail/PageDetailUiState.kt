package com.rjasao.nowsei.presentation.page_detail

data class PageDetailUiState(
    val title: String = "",
    val visitDate: String = "",
    val visitDateMillis: Long = 0L,
    val htmlContent: String = "",
    val isLoading: Boolean = true,
    val lastModified: String? = null
)
