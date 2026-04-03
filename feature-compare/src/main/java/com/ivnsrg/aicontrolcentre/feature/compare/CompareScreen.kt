package com.ivnsrg.aicontrolcentre.feature.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivnsrg.aicontrolcentre.core.ui.components.AppScreenScaffold
import com.ivnsrg.aicontrolcentre.core.ui.components.EmptyState
import com.ivnsrg.aicontrolcentre.core.ui.components.PrimaryButton

@Composable
fun CompareRoute(
    threadId: Long,
    onDone: () -> Unit,
) {
    CompareScreen(threadId = threadId, onDone = onDone)
}

@Composable
fun CompareScreen(
    threadId: Long,
    onDone: () -> Unit,
) {
    AppScreenScaffold(title = "Compare #$threadId") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EmptyState(
                title = "Compare stub",
                subtitle = "Экран сравнения подключён как bootstrap-заглушка для последующей реализации.",
            )
            PrimaryButton(text = "Назад", onClick = onDone)
        }
    }
}
