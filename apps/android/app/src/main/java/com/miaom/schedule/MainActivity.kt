package com.miaom.schedule

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.miaom.schedule.ui.ScheduleApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScheduleApp()
        }
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val activeIntent = intent ?: return
        lifecycleScope.launch {
            val result = runCatching {
                (application as ScheduleApplication)
                    .appContainer
                    .shareImportHandler
                    .handleIntent(activeIntent)
            }
            result.getOrNull()?.let { status ->
                Toast.makeText(this@MainActivity, status, Toast.LENGTH_LONG).show()
                clearImportIntent(activeIntent)
            }
            result.exceptionOrNull()?.let { error ->
                if (error.message?.isNotBlank() == true) {
                    Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
                    clearImportIntent(activeIntent)
                }
            }
        }
    }

    private fun clearImportIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) {
            setIntent(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
    }
}
