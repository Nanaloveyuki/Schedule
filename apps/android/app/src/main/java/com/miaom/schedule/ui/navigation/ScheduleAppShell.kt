package com.miaom.schedule.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AppShellDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
internal fun ScheduleAdaptiveShell(
    destinations: List<AppShellDestination>,
    selectedRoute: String,
    onNavigateToTopLevel: (String) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRailLayout = maxWidth >= 980.dp

        if (useRailLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface {
                    NavigationRail(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 112.dp)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        destinations.forEach { destination ->
                            WideNavigationRailItem(
                                selected = destination.route == selectedRoute,
                                onClick = { onNavigateToTopLevel(destination.route) },
                                destination = destination
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
                VerticalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content(Modifier.fillMaxSize())
                }
            }
        } else {
            val navigationBarInsets = WindowInsets.navigationBars.asPaddingValues()

            androidx.compose.material3.Scaffold(
                bottomBar = {
                    Surface(
                        color = bottomBarContainerColor(),
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 10.dp,
                                end = 10.dp,
                                top = 6.dp,
                                bottom = navigationBarInsets.calculateBottomPadding().coerceAtLeast(8.dp)
                            )
                    ) {
                        NavigationBar(
                            tonalElevation = 0.dp,
                            containerColor = Color.Transparent,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            destinations.forEach { destination ->
                                CompactBottomNavigationItem(
                                    selected = destination.route == selectedRoute,
                                    onClick = { onNavigateToTopLevel(destination.route) },
                                    destination = destination,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                content(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun CompactBottomNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    destination: AppShellDestination,
    modifier: Modifier = Modifier
) {
    val activeContainer = bottomBarIndicatorColor(selected = true)
    val itemShape = RoundedCornerShape(24.dp)
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    }
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(itemShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) activeContainer else Color.Transparent,
                modifier = Modifier
                    .size(42.dp),
                contentColor = iconColor
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier.size(22.dp),
                        tint = iconColor
                    )
                }
            }
            Text(
                text = destination.label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WideNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    destination: AppShellDestination
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = railCaptionFor(destination.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.78f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun railCaptionFor(label: String): String {
    return when (label) {
        "课表" -> "本周"
        "编辑" -> "课程"
        "预设" -> "样式"
        "个性化" -> "配色"
        "设置" -> "偏好"
        else -> label
    }
}

@Composable
private fun bottomBarContainerColor(): Color {
    return if (MaterialTheme.colorScheme.surface.luminance() < 0.2f) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.98f)
    } else {
        MaterialTheme.colorScheme.surface
    }
}

@Composable
private fun bottomBarIndicatorColor(selected: Boolean): Color {
    if (!selected) return Color.Transparent
    val base = MaterialTheme.colorScheme.secondaryContainer
    val background = bottomBarContainerColor()
    return if (background.luminance() < 0.24f) {
        base.copy(alpha = 0.92f)
    } else {
        base
    }
}
