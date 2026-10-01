package rw.inama.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wrapping row of chips. FlowRow and its FlowRowScope are still experimental in Compose 1.7, so
 * the opt-in stays inside this function and callers get a plain composable lambda (no
 * experimental receiver type leaks out to every screen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}
