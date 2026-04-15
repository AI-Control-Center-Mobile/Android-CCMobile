package com.ivnsrg.aicontrolcentre.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivnsrg.aicontrolcentre.app.navigation.AiControlCentreNavHost
import com.ivnsrg.aicontrolcentre.core.ui.theme.AiControlCentreTheme
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors
import androidx.compose.material3.MaterialTheme

@Composable
fun AiControlCentreRoot() {
    AiControlCentreTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.appColors.background),
        ) {
            AiControlCentreNavHost()
        }
    }
}
