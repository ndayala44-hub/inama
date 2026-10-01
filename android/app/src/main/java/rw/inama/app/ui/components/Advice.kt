package rw.inama.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rw.inama.app.R
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.theme.Inama

/** The core advice pattern: What's happening · Why it matters · What to do. */
@Composable
fun ThreeQuestions(
    happening: String,
    why: String,
    steps: List<String>,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    compact: Boolean = false,
) {
    val c = Inama.colors
    val fg = if (dark) Color.White else c.ink
    val sub = if (dark) Color.White.copy(alpha = 0.78f) else c.muted
    val size = if (compact) 32.dp else 36.dp
    val items = listOf(
        Triple(R.string.q_happening, R.drawable.ic_eye, if (dark) Color.White.copy(alpha = 0.12f) to Color.White else c.mint to c.forest),
        Triple(R.string.q_why, R.drawable.ic_info, if (dark) Color.White.copy(alpha = 0.12f) to Color.White else c.amberSoft to c.amberInk),
        Triple(R.string.q_todo, R.drawable.ic_check, c.sprout to c.ink),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
        items.forEachIndexed { index, (label, icon, colors) ->
            if (index > 0) Divider(Modifier.padding(start = size + 12.dp), color = if (dark) Color.White.copy(alpha = 0.16f) else c.line)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(size).clip(CircleShape).background(colors.first), contentAlignment = Alignment.Center) {
                    InamaIcon(icon, tint = colors.second, size = 18.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = sub)
                    when (index) {
                        0 -> Text(happening, style = MaterialTheme.typography.bodyLarge, color = fg)
                        1 -> Text(why, style = MaterialTheme.typography.bodyLarge, color = fg)
                        else -> NumberedSteps(steps, dark = dark)
                    }
                }
            }
        }
    }
}

@Composable
fun NumberedSteps(steps: List<String>, dark: Boolean = false) {
    val c = Inama.colors
    if (steps.size == 1) {
        Text(steps.first(), style = MaterialTheme.typography.bodyLarge, color = if (dark) Color.White else c.ink)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.padding(top = 1.dp).size(24.dp).clip(CircleShape).background(if (dark) c.sprout else c.forest),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (dark) c.ink else Color.White)
                }
                Text(step, style = MaterialTheme.typography.bodyLarge, color = if (dark) Color.White else c.ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * "Listen" — reads text aloud in the app language (TTS). Shows an estimated duration so farmers
 * know what it will cost them. Tapping again stops.
 */
@Composable
fun ListenButton(id: String, text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    val container = LocalAppContainer.current
    val speaking by container.speechOutput.speakingId.collectAsState()
    val scope = rememberCoroutineScope()
    val isMe = speaking == id
    val c = Inama.colors
    val fg = if (dark) Color.White else c.forest
    val seconds = (text.split(Regex("\\s+")).size / 2.4).toInt().coerceAtLeast(3)
    val duration = "%d:%02d".format(seconds / 60, seconds % 60)
    OutlinedButton(
        onClick = {
            scope.launch {
                val s = container.settingsRepository.current()
                container.speechOutput.toggle(id, text, s.language, s.speechRate.rate)
            }
        },
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.5.dp, if (dark) Color.White.copy(alpha = 0.55f) else c.line2),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (dark) Color.White.copy(alpha = 0.08f) else c.paper, contentColor = fg),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
    ) {
        InamaIcon(if (isMe) R.drawable.ic_pause else R.drawable.ic_speaker, tint = fg, size = 20.dp)
        Text(
            text = if (isMe) " " + stringResource(R.string.action_stop) else " " + stringResource(R.string.action_listen),
            style = MaterialTheme.typography.labelLarge,
        )
        if (!isMe) Text("  $duration", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Sources behind a piece of advice — shown so farmers and agronomists can check it. */
@Composable
fun SourcesRow(sources: List<String>) {
    if (sources.isEmpty()) return
    ChipFlow { sources.forEach { SourceChip(it) } }
}

@Composable
fun EvidenceLine(ok: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        InamaIcon(if (ok) R.drawable.ic_check else R.drawable.ic_eye, tint = if (ok) Inama.colors.leaf else Inama.colors.amberInk, size = 18.dp, modifier = Modifier.padding(top = 2.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Inama.colors.ink)
    }
}

/** Bordered call-out used for "Want a person to check?" style links. */
@Composable
fun DashedLinkCard(title: String, body: String, onClick: () -> Unit) {
    val c = Inama.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.5.dp, c.line2, shape)
            .background(c.paper)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(R.drawable.ic_headset, background = c.sand, tint = c.ink, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
    }
}
