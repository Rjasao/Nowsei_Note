package com.rjasao.nowsei.presentation

// Representa o estado da UI para a HomeScreen
data class HomeState(
    val isLoading: Boolean = false,
    val data: String = "Dado inicial", // Exemplo de um dado
    val error: String? = null
)
