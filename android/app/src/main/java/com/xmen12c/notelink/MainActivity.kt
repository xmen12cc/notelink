package com.xmen12c.notelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xmen12c.notelink.ui.NoteLinkApp
import com.xmen12c.notelink.ui.theme.NoteLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteLinkTheme {
                NoteLinkApp()
            }
        }
    }
}
