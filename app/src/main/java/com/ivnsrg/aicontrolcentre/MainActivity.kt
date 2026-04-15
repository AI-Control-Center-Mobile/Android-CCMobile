package com.ivnsrg.aicontrolcentre

import android.os.Bundle
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.toColorInt
import com.ivnsrg.aicontrolcentre.app.ui.AiControlCentreRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = "#081019".toColorInt()
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        window.navigationBarColor = background
        window.isNavigationBarContrastEnforced = false
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(background),
            navigationBarStyle = SystemBarStyle.dark(background),
        )
        setContent {
            AiControlCentreRoot()
        }
    }
}
