package com.rjasao.nowsei

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
// 1. IMPORTANDO NOSSO NAVGRAPH
import com.rjasao.nowsei.presentation.navigation.NavGraph
import com.rjasao.nowsei.ui.theme.NowseiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NowseiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 2. CRIANDO O CONTROLADOR DE NAVEGAÇÃO
                    val navController = rememberNavController()
                    // 3. CHAMANDO O GRAFO DE NAVEGAÇÃO
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
