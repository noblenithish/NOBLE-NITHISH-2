package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.firebase.configureAppCheck
import com.example.ui.navigation.JarvisRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure Firebase App Check with launch intent BEFORE setContent or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        val container = (application as JarvisApplication).container
        setContent {
            JarvisRoot(container = container)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            (application as JarvisApplication).container.speechOutput.shutdown()
        }
        super.onDestroy()
    }
}
