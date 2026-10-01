package rw.inama.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.inama.app.R
import rw.inama.app.domain.model.Confidence
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.Urgency
import rw.inama.app.domain.model.Verdict
import rw.inama.app.ui.theme.Inama

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Inama.colors.paper,
    foreground: Color = Inama.colors.ink,
    @DrawableRes icon: Int? = null,
    border: Color? = null,
    height: Dp = 32.dp,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) InamaIcon(icon, tint = foreground, size = 16.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Urgency is always icon + word + colour, never colour alone. */
@Composable
fun UrgencyChip(urgency: Urgency, text: String? = null, height: Dp = 32.dp) {
    val c = Inama.colors
    val label = text ?: stringResource(urgencyLabel(urgency))
    when (urgency) {
        Urgency.TODAY -> Chip(label, background = c.clay, foreground = c.white, icon = R.drawable.ic_alert, height = height)
        Urgency.THIS_WEEK -> Chip(label, background = c.amber, foreground = c.ink, icon = R.drawable.ic_clock, height = height)
        Urgency.WATCH -> Chip(label, background = c.sproutSoft, foreground = c.ink, icon = R.drawable.ic_eye, border = c.line2, height = height)
        Urgency.PLAN -> Chip(label, background = c.paper, foreground = c.ink, icon = R.drawable.ic_calendar, border = c.line2, height = height)
    }
}

enum class FieldStatus { GOOD, CHECK, SICK, NOT_PLANTED }

@Composable
fun StatusChip(status: FieldStatus) {
    val c = Inama.colors
    when (status) {
        FieldStatus.GOOD -> Chip(stringResource(R.string.status_good), background = c.mint, foreground = c.mintInk, icon = R.drawable.ic_check, height = 30.dp)
        FieldStatus.CHECK -> Chip(stringResource(R.string.status_check), background = c.amberSoft, foreground = c.amberInk, icon = R.drawable.ic_eye, height = 30.dp)
        FieldStatus.SICK -> Chip(stringResource(R.string.status_sick), background = c.claySoft, foreground = c.clayInk, icon = R.drawable.ic_alert, height = 30.dp)
        FieldStatus.NOT_PLANTED -> Chip(stringResource(R.string.status_not_planted), background = c.sand, foreground = c.muted, icon = R.drawable.ic_clock, height = 30.dp)
    }
}

@Composable
fun VerdictChip(verdict: Verdict) {
    val c = Inama.colors
    val label = stringResource(verdictLabel(verdict))
    when (verdict) {
        Verdict.GO -> Chip(label, background = c.mint, foreground = c.mintInk, icon = R.drawable.ic_check, height = 28.dp)
        Verdict.CAREFUL -> Chip(label, background = c.amber, foreground = c.ink, icon = R.drawable.ic_clock, height = 28.dp)
        Verdict.WAIT -> Chip(label, background = c.clay, foreground = c.white, icon = R.drawable.ic_alert, height = 28.dp)
    }
}

/** Three rising bars + "Confidence: High · reason". Never a percentage (design rule). */
@Composable
fun ConfidenceMeter(confidence: Confidence, modifier: Modifier = Modifier, dark: Boolean = false, showReason: Boolean = true) {
    val c = Inama.colors
    val filled = when (confidence.level) {
        ConfidenceLevel.HIGH -> 3
        ConfidenceLevel.MEDIUM -> 2
        ConfidenceLevel.LOW -> 1
    }
    val on = when {
        dark -> c.sprout
        confidence.level == ConfidenceLevel.LOW -> c.clay
        else -> c.forest
    }
    val off = if (dark) Color.White.copy(alpha = 0.28f) else c.line2
    val level = stringResource(confidenceLevelLabel(confidence.level))
    val reason = if (showReason) confidenceReason(confidence) else null
    val spoken = stringResource(R.string.confidence_label, level) + (reason?.let { " · $it" } ?: "")
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until 3) {
                Box(Modifier.width(6.dp).height((8 + i * 5).dp).clip(RoundedCornerShape(3.dp)).background(if (i < filled) on else off))
            }
        }
        Text(
            text = stringResource(R.string.confidence_label, level) + (reason?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = if (dark) Color.White else c.ink,
        )
    }
}

/** Selectable pill used for choices and filters. */
@Composable
fun PillChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
) {
    val c = Inama.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) c.sprout else c.paper)
            .border(1.5.dp, if (selected) c.sprout else c.line2, shape)
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) InamaIcon(icon, tint = c.ink, size = 18.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.ink else c.muted, maxLines = 1)
    }
}

@Composable
fun SourceChip(text: String) {
    Chip(text, background = Inama.colors.sand, foreground = Inama.colors.muted, icon = R.drawable.ic_book, height = 30.dp)
}

/** Segmented control (e.g. Slow / Normal / Fast). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Inama.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.sand).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.paper else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(value) })
                    .semantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.muted)
            }
        }
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
