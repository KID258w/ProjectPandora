package com.projectpandora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.projectpandora.app.navigation.PandoraApp
import com.projectpandora.app.ui.theme.PandoraAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PandoraAppTheme {
                PandoraApp()
            }
        }
    }
}
