package rw.inama.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.inama.app.R
import rw.inama.app.domain.model.Crop
import rw.inama.app.domain.model.FarmTask
import rw.inama.app.ui.theme.Inama

/** Crop illustration by crop id (shared KB ids). Unknown crops fall back to a leaf. */
@DrawableRes
fun cropDrawable(cropId: String?): Int = when (cropId) {
    "maize" -> R.drawable.crop_maize
    "beans" -> R.drawable.crop_beans
    "cassava" -> R.drawable.crop_cassava
    "potato" -> R.drawable.crop_potato
    "banana" -> R.drawable.crop_banana
    "sweetpotato" -> R.drawable.crop_sweetpotato
    "rice" -> R.drawable.crop_rice
    "coffee" -> R.drawable.crop_coffee
    "sorghum" -> R.drawable.crop_sorghum
    "tomato" -> R.drawable.crop_tomato
    "cow" -> R.drawable.crop_cow
    else -> R.drawable.crop_beans
}

@Composable
fun CropImage(cropId: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Image(painter = painterResource(cropDrawable(cropId)), contentDescription = null, modifier = modifier.size(size))
}

@Composable
fun CropTile(cropId: String?, size: Dp = 64.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Inama.colors.sand),
        contentAlignment = Alignment.Center,
    ) { CropImage(cropId, size * 0.78f) }
}

/** Selectable crop grid cell used in field set-up and the photo check. */
@Composable
fun CropChoice(crop: Crop, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Inama.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) c.sproutSoft else c.paper)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) c.forest else c.line, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CropImage(crop.id, 52.dp)
            Text(crop.name, style = MaterialTheme.typography.labelMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 6.dp).size(22.dp).clip(CircleShape).background(c.forest),
                contentAlignment = Alignment.Center,
            ) { InamaIcon(R.drawable.ic_check, tint = c.white, size = 14.dp) }
        }
    }
}

/** Four-step crop stage track (Planted → Growing → Flowering → Harvest). */
@Composable
fun StageTrack(labels: List<String>, current: Int) {
    val c = Inama.colors
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val done = i < current
            val on = i == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (done || on) c.forest else c.paper)
                        .border(2.dp, if (done || on) c.forest else c.line2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) InamaIcon(R.drawable.ic_check, tint = c.white, size = 12.dp)
                    if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(c.sprout))
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (on) c.ink else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)),
        color = Inama.colors.leaf,
        trackColor = Inama.colors.line,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}

@Composable
fun TaskRow(
    task: FarmTask,
    fieldName: String?,
    onToggle: (Boolean) -> Unit,
    onOpen: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Inama.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = c.forest, uncheckedColor = c.line2, checkmarkColor = c.white),
        )
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (task.done) c.muted else c.ink,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!task.done) UrgencyChip(task.urgency, height = 26.dp)
                if (task.source == rw.inama.app.domain.model.TaskSource.ADVICE) {
                    Chip(stringResource(R.string.task_from_inama), background = c.sproutSoft, foreground = c.forest, icon = R.drawable.ic_sparkle, height = 26.dp)
                }
                if (fieldName != null) Text(fieldName, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
            }
        }
        trailing?.invoke()
    }
}

/** Horizontal field card from the Home screen. */
@Composable
fun FieldCard(name: String, subtitle: String, cropId: String, progress: Float, status: FieldStatus, onClick: () -> Unit, width: Dp = 228.dp) {
    val c = Inama.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .width(width)
            .shadow(3.dp, shape)
            .clip(shape)
            .background(c.paper)
            .border(1.dp, c.line, shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().height(96.dp).background(c.sand).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CropImage(cropId, 64.dp)
            StatusChip(status)
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressLine(progress, height = 6.dp)
        }
    }
}
