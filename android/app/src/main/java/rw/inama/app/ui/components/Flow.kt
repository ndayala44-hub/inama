package rw.inama.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Wrapping row of chips. Wrapped once so the opt-in lives in one place. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
