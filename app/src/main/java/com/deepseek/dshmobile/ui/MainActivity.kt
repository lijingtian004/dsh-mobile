package com.deepseek.dshmobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.deepseek.dshmobile.ui.nav.NavigationHost
import com.deepseek.dshmobile.ui.theme.DSHMobileTheme
import com.deepseek.dshmobile.service.DshEngineManager
import com.deepseek.dshmobile.service.DshEngineService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!DshEngineManager.isRunning) {
            startForegroundService(
                Intent(this, DshEngineService::class.java)
                    .setAction(DshEngineService.ACTION_START)
            )
        }
        setContent {
            DSHMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavigationHost()
                }
            }
        }
    }
}
