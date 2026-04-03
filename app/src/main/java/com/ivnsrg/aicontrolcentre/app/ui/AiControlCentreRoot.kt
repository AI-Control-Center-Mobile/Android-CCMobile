package com.ivnsrg.aicontrolcentre.app.ui

import androidx.compose.runtime.Composable
import com.ivnsrg.aicontrolcentre.app.navigation.AiControlCentreNavHost
import com.ivnsrg.aicontrolcentre.core.ui.theme.AiControlCentreTheme

@Composable
fun AiControlCentreRoot() {
    AiControlCentreTheme {
        AiControlCentreNavHost()
    }
}
