package rw.inama.app.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import rw.inama.app.R
import rw.inama.app.ui.theme.Inama

enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int) {
    HOME(R.string.tab_home, R.drawable.ic_home),
    FARM(R.string.tab_farm, R.drawable.ic_farm),
    ASK(R.string.tab_ask, R.drawable.ic_mic),
    TASKS(R.string.tab_tasks, R.drawable.ic_tasks),
    ME(R.string.tab_me, R.drawable.ic_user),
}

/**
 * Floating pill navigation from the reference design; the centre action is voice ("Ask"),
 * raised and larger because asking by voice is the habit Inama is built around.
 */
@Composable
fun InamaBottomBar(current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Inama.colors
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.ground.copy(alpha = 0f), c.ground, c.ground)))
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(30.dp), spotColor = Color(0x800A180C))
                .clip(RoundedCornerShape(30.dp))
                .background(c.forestDeep)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                if (tab == Tab.ASK) {
                    Box(Modifier.width(72.dp))
                } else {
                    val on = tab == current
                    val fg = if (on) c.sprout else Color.White.copy(alpha = 0.78f)
                    Column(
                        Modifier
                            .width(60.dp)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        InamaIcon(tab.icon, tint = fg, size = 22.dp)
                        Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall, color = fg)
                    }
                }
            }
        }
        // Raised voice button
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = (-18).dp)
                .clip(RoundedCornerShape(24.dp))
                .selectable(selected = current == Tab.ASK, role = Role.Button, onClick = { onSelect(Tab.ASK) }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .border(5.dp, c.forestDeep, CircleShape)
                    .clip(CircleShape)
                    .background(c.sprout),
                contentAlignment = Alignment.Center,
            ) { InamaIcon(R.drawable.ic_mic, tint = c.ink, size = 28.dp, contentDescription = stringResource(R.string.cd_ask_by_voice)) }
            Text(stringResource(R.string.tab_ask), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}
