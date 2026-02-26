package com.dc.murmur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dc.murmur.navigation.MurmurNavGraph
import com.dc.murmur.ui.theme.MurmurTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MurmurTheme {
                MurmurNavGraph()
            }
        }
    }
}