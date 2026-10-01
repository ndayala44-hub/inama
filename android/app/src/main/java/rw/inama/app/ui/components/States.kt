package rw.inama.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import rw.inama.app.R
import rw.inama.app.ui.theme.Inama

enum class BannerKind { OFFLINE, SUCCESS, INFO, WARNING }

/** Status strip: offline, synced, info, warning. Announced by screen readers (live region). */
@Composable
fun Banner(text: String, kind: BannerKind, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Inama.colors
    val (bg, fg, icon) = when (kind) {
        BannerKind.OFFLINE -> Triple(c.ink, Color.White, R.drawable.ic_wifioff)
        BannerKind.SUCCESS -> Triple(c.mint, c.mintInk, R.drawable.ic_check)
        BannerKind.INFO -> Triple(c.skySoft, c.skyInk, R.drawable.ic_info)
        BannerKind.WARNING -> Triple(c.amberSoft, c.amberInk, R.drawable.ic_alert)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InamaIcon(icon, tint = fg, size = 20.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = MaterialTheme.typography.labelLarge, color = fg, textDecoration = TextDecoration.Underline)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Banner(stringResource(R.string.offline_banner), BannerKind.OFFLINE, modifier)
}

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Inama.colors.paper), contentAlignment = Alignment.Center) {
            InamaIcon(icon, tint = Inama.colors.forest, size = 44.dp)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Inama.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Inama.colors.muted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            InamaButton(action, onAction, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ErrorState(title: String, body: String, modifier: Modifier = Modifier, retry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(Inama.colors.amberSoft), contentAlignment = Alignment.Center) {
            InamaIcon(R.drawable.ic_alert, tint = Inama.colors.amberInk, size = 32.dp)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Inama.colors.ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Inama.colors.muted, textAlign = TextAlign.Center)
        if (retry != null) InamaButton(stringResource(R.string.action_try_again), retry, icon = R.drawable.ic_sync)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = stringResource(R.string.loading)) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Inama.colors.forest)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Inama.colors.muted)
    }
}

@Composable
fun VerticalSpace(height: Int) {
    Box(Modifier.height(height.dp))
}
