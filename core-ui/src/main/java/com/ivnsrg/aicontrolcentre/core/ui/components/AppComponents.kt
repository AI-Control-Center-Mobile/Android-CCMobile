package com.ivnsrg.aicontrolcentre.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivnsrg.aicontrolcentre.core.ui.theme.LocalSpacing
import com.ivnsrg.aicontrolcentre.core.ui.theme.MonoMetaTextStyle
import com.ivnsrg.aicontrolcentre.core.ui.theme.appColors

enum class CardTone {
    Surface1,
    Surface2,
    Surface3,
    Danger,
}

enum class BadgeTone {
    Neutral,
    Primary,
    Info,
    Warning,
    Danger,
}

enum class HeaderDensity {
    Default,
    Compact,
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appColors.surface1,
            contentColor = MaterialTheme.appColors.textPrimary,
        ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentPrimary,
            contentColor = colors.background,
            disabledContainerColor = colors.surface3,
            disabledContentColor = colors.textMuted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surface2,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.surface3,
            disabledContentColor = colors.textMuted,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isSecret: Boolean = false,
) {
    val colors = MaterialTheme.appColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        singleLine = singleLine,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = colors.textPrimary,
            fontFamily = if (isSecret) FontFamily.Monospace else FontFamily.Default,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentPrimary,
            unfocusedBorderColor = colors.stroke,
            focusedContainerColor = colors.background,
            unfocusedContainerColor = colors.background,
            focusedLabelColor = colors.textSecondary,
            unfocusedLabelColor = colors.textMuted,
            cursorColor = colors.accentPrimary,
            disabledBorderColor = colors.stroke,
            disabledContainerColor = colors.surface2,
            disabledLabelColor = colors.textMuted,
            disabledTextColor = colors.textSecondary,
        ),
    )
}

@Composable
fun OperationalCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Surface1,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val background = when (tone) {
        CardTone.Surface1 -> colors.surface1
        CardTone.Surface2 -> colors.surface2
        CardTone.Surface3 -> colors.surface3
        CardTone.Danger -> colors.accentDanger.copy(alpha = 0.08f)
    }
    val borderColor = when (tone) {
        CardTone.Danger -> colors.accentDanger.copy(alpha = 0.35f)
        else -> colors.stroke
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(background, RoundedCornerShape(20.dp))
            .padding(padding),
    ) {
        content()
    }
}

@Composable
fun MetadataChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val colors = MaterialTheme.appColors
    val background = when (tone) {
        BadgeTone.Neutral -> colors.surface3
        BadgeTone.Primary -> colors.accentPrimary.copy(alpha = 0.16f)
        BadgeTone.Info -> colors.accentInfo.copy(alpha = 0.16f)
        BadgeTone.Warning -> colors.accentWarning.copy(alpha = 0.16f)
        BadgeTone.Danger -> colors.accentDanger.copy(alpha = 0.16f)
    }
    val contentColor = when (tone) {
        BadgeTone.Neutral -> colors.textSecondary
        BadgeTone.Primary -> colors.accentPrimary
        BadgeTone.Info -> colors.accentInfo
        BadgeTone.Warning -> colors.accentWarning
        BadgeTone.Danger -> colors.accentDanger
    }

    Box(
        modifier = modifier
            .wrapContentWidth(unbounded = false)
            .background(background, RoundedCornerShape(8.dp))
            .border(1.dp, colors.stroke.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text.uppercase(),
            modifier = Modifier.wrapContentWidth(unbounded = false),
            style = MonoMetaTextStyle,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Primary,
) {
    MetadataChip(
        text = text,
        modifier = modifier,
        tone = tone,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val colors = MaterialTheme.appColors
    val contentColor = when (tone) {
        BadgeTone.Primary -> colors.accentPrimary
        BadgeTone.Info -> colors.accentInfo
        BadgeTone.Warning -> colors.accentWarning
        BadgeTone.Danger -> colors.accentDanger
        BadgeTone.Neutral -> colors.textMuted
    }
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MonoMetaTextStyle,
        color = contentColor,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.appColors.textMuted,
    )
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.appColors.textPrimary,
        )
    }
}

@Composable
fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val colors = MaterialTheme.appColors
    val background = when (tone) {
        BadgeTone.Primary -> colors.accentPrimary.copy(alpha = 0.18f)
        BadgeTone.Info -> colors.accentInfo.copy(alpha = 0.18f)
        BadgeTone.Warning -> colors.accentWarning.copy(alpha = 0.18f)
        BadgeTone.Danger -> colors.accentDanger.copy(alpha = 0.18f)
        BadgeTone.Neutral -> colors.surface3
    }
    val contentColor = when (tone) {
        BadgeTone.Primary -> colors.accentPrimary
        BadgeTone.Info -> colors.accentInfo
        BadgeTone.Warning -> colors.accentWarning
        BadgeTone.Danger -> colors.accentDanger
        BadgeTone.Neutral -> colors.textPrimary
    }
    Box(
        modifier = modifier
            .height(36.dp)
            .background(background, RoundedCornerShape(12.dp))
            .border(1.dp, colors.stroke, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
fun AppInfoCallout(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Info,
) {
    val colors = MaterialTheme.appColors
    val accent = when (tone) {
        BadgeTone.Primary -> colors.accentPrimary
        BadgeTone.Info -> colors.accentInfo
        BadgeTone.Warning -> colors.accentWarning
        BadgeTone.Danger -> colors.accentDanger
        BadgeTone.Neutral -> colors.textSecondary
    }
    OperationalCard(
        modifier = modifier,
        tone = CardTone.Surface2,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(title, tone = tone)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.25f), RoundedCornerShape(100.dp))
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Neutral,
) {
    val colors = MaterialTheme.appColors
    val valueColor = when (tone) {
        BadgeTone.Primary -> colors.accentPrimary
        BadgeTone.Info -> colors.accentInfo
        BadgeTone.Warning -> colors.accentWarning
        BadgeTone.Danger -> colors.accentDanger
        BadgeTone.Neutral -> colors.textPrimary
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
        )
    }
}

@Composable
fun AppScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    alignCenter: Boolean = false,
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = if (alignCenter) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = if (alignCenter) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerDensity: HeaderDensity = HeaderDensity.Default,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.background,
                        Color(0xFF0B1523),
                        colors.background,
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .offset(y = if (headerDensity == HeaderDensity.Compact) (-8).dp else 0.dp)
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = if (headerDensity == HeaderDensity.Compact) 0.dp else 6.dp,
                        ),
                ) {
                    if (topBar != null) {
                        topBar()
                    } else {
                        AppScreenHeader(
                            title = title,
                            subtitle = subtitle,
                        )
                    }
                }
            },
            bottomBar = {
                bottomBar?.invoke()
            },
            content = content,
        )
    }
}

@Composable
fun FloatingBottomBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.appColors
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = colors.background.copy(alpha = 0.72f),
                    spotColor = colors.accentPrimary.copy(alpha = 0.18f),
                )
                .border(1.dp, colors.stroke.copy(alpha = 0.7f), shape)
                .background(colors.surface2.copy(alpha = 0.82f), shape),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.textPrimary.copy(alpha = 0.08f),
                                colors.accentPrimary.copy(alpha = 0.03f),
                                Color.Transparent,
                            ),
                        ),
                        shape = shape,
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun LoadingState(
    title: String = "Загрузка…",
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        OperationalCard(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth(),
            tone = CardTone.Surface2,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = colors.accentPrimary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.appColors
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        OperationalCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg),
            tone = CardTone.Surface1,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                action?.invoke()
            }
        }
    }
}

@Composable
fun RecoverableErrorState(
    title: String,
    subtitle: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        action = {
            PrimaryButton(
                text = "Повторить",
                onClick = onRetry,
            )
        },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String = "Отмена",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    AlertDialog(
        containerColor = colors.surface1,
        textContentColor = colors.textSecondary,
        titleContentColor = colors.textPrimary,
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = colors.accentDanger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = colors.textSecondary)
            }
        },
    )
}
