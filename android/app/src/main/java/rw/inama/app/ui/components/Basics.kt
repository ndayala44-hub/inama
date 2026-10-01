package rw.inama.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rw.inama.app.R
import rw.inama.app.ui.theme.Inama

// ------------------------------------------------------------------------------- icons

@Composable
fun InamaIcon(@DrawableRes id: Int, modifier: Modifier = Modifier, tint: Color = Inama.colors.ink, size: Dp = 24.dp, contentDescription: String? = null) {
    Icon(painter = painterResource(id), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

/** Rounded square with an icon — the design's "ic_circle". */
@Composable
fun IconTile(@DrawableRes id: Int, background: Color = Inama.colors.mint, tint: Color = Inama.colors.forest, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(background),
        contentAlignment = Alignment.Center,
    ) { InamaIcon(id, tint = tint, size = size * 0.5f) }
}

@Composable
fun CircleIconButton(
    @DrawableRes id: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Inama.colors.paper,
    tint: Color = Inama.colors.ink,
    size: Dp = 48.dp,
    bordered: Boolean = true,
    badge: Boolean = false,
) {
    Box(modifier = modifier.size(size)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .then(if (bordered) Modifier.border(1.dp, Inama.colors.line, CircleShape) else Modifier),
            colors = IconButtonDefaults.iconButtonColors(containerColor = background, contentColor = tint),
        ) { InamaIcon(id, tint = tint, size = 22.dp, contentDescription = contentDescription) }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 11.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Inama.colors.clay),
            )
        }
    }
}

// ------------------------------------------------------------------------------- buttons

enum class ButtonKind { PRIMARY, SECONDARY, SPROUT, CLAY, LIGHT, GHOST, OUTLINE_LIGHT }

@Composable
fun InamaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    height: Dp = 56.dp,
) {
    val c = Inama.colors
    val shape = RoundedCornerShape(18.dp)
    val widthModifier = if (fullWidth) modifier.fillMaxWidth() else modifier
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            // No explicit tint: Icon uses the button's LocalContentColor.
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    when (kind) {
        ButtonKind.SECONDARY, ButtonKind.OUTLINE_LIGHT -> {
            val fg = if (kind == ButtonKind.SECONDARY) c.forest else c.white
            val border = if (kind == ButtonKind.SECONDARY) c.forest else c.white.copy(alpha = 0.6f)
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = widthModifier.heightIn(min = height),
                shape = shape,
                border = BorderStroke(1.5.dp, border),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (kind == ButtonKind.SECONDARY) c.paper else Color.Transparent, contentColor = fg),
                contentPadding = PaddingValues(horizontal = 18.dp),
                content = content,
            )
        }
        ButtonKind.GHOST -> TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = widthModifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = c.forest),
            content = content,
        )
        else -> {
            val (bg, fg) = when (kind) {
                ButtonKind.SPROUT -> c.sprout to c.ink
                ButtonKind.CLAY -> c.clay to c.white
                ButtonKind.LIGHT -> c.white to c.forest
                else -> c.forest to c.white
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = widthModifier.heightIn(min = height),
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = c.line, disabledContentColor = c.muted),
                contentPadding = PaddingValues(horizontal = 18.dp),
                content = content,
            )
        }
    }
}

// ------------------------------------------------------------------------------- surfaces

@Composable
fun InamaCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    background: Color = Inama.colors.paper,
    border: Color? = Inama.colors.line,
    shape: Shape = RoundedCornerShape(24.dp),
    onClick: (() -> Unit)? = null,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .shadow(elevation = 3.dp, shape = shape, ambientColor = Inama.colors.ink.copy(alpha = 0.08f), spotColor = Inama.colors.ink.copy(alpha = 0.12f))
        .clip(shape)
        .background(background)
        .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(padding)
    Column(modifier = base, verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/** Frosted card for use over aerial imagery (reference pattern), darkened for sunlight legibility. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    tint: Color = Color(0x3315301A),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f))
            .background(tint)
            .border(1.dp, Color.White.copy(alpha = 0.26f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier, color: Color = Inama.colors.line) {
    Box(modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(color))
}

// ------------------------------------------------------------------------------- headers

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    dark: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val fg = if (dark) Color.White else Inama.colors.ink
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            CircleIconButton(
                R.drawable.ic_back,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                background = if (dark) Color.White.copy(alpha = 0.14f) else Inama.colors.paper,
                tint = fg,
                bordered = !dark,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = fg, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (dark) Color.White.copy(alpha = 0.78f) else Inama.colors.muted)
        }
        actions()
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Inama.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onAction).heightIn(min = 44.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = Inama.colors.forest)
                InamaIcon(R.drawable.ic_chev, tint = Inama.colors.forest, size = 18.dp)
            }
        }
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Inama.colors.muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
}

/** Circular initials avatar. */
@Composable
fun Avatar(initials: String, size: Dp = 44.dp, background: Color = Inama.colors.sprout, foreground: Color = Inama.colors.ink) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(initials, style = MaterialTheme.typography.titleSmall, color = foreground)
    }
}

@Composable
fun Illustration(@DrawableRes id: Int, modifier: Modifier = Modifier) {
    Image(painter = painterResource(id), contentDescription = null, modifier = modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
}
