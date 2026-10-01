package rw.inama.app.feature.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rw.inama.app.R
import rw.inama.app.domain.model.AiMode
import rw.inama.app.domain.model.AppLanguage
import rw.inama.app.domain.model.SpeechRate
import rw.inama.app.domain.model.TextScale
import rw.inama.app.feature.onboarding.ToggleLine
import rw.inama.app.feature.onboarding.fieldColors
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.Avatar
import rw.inama.app.ui.components.Banner
import rw.inama.app.ui.components.BannerKind
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.Illustration
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.Segmented
import rw.inama.app.ui.theme.Inama

// =============================================================================== Me tab

@Composable
fun MeScreen(onHistory: () -> Unit, onSettings: () -> Unit, onHelp: () -> Unit, onEditProfile: () -> Unit, onFarm: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: MeViewModel = viewModel {
        MeViewModel(container.profileRepository, container.sessionRepository, container.fieldRepository, container.diagnosisRepository, container.taskRepository, container.catalogRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    StatusBarIcons(light = true)
    val farmer = state.farmer

    LazyColumn(Modifier.fillMaxSize().background(c.ground), contentPadding = PaddingValues(bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(c.forestDeep)) {
                Illustration(R.drawable.bg_aerial_header, Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xB315301A), Color(0xF215301A)))))
                Column(Modifier.statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(farmer?.initials ?: "?", 68.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(farmer?.name ?: stringResource(R.string.me_no_name), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            if (farmer != null) Text(farmer.phone, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                            val place = listOfNotNull(farmer?.sector?.takeIf { it.isNotBlank() }, state.district?.name).joinToString(", ")
                            if (place.isNotBlank()) Text(place, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(stringResource(R.string.me_stat_fields), "${state.fieldCount}", Modifier.weight(1f))
                        Stat(stringResource(R.string.me_stat_area), stringResource(R.string.area_ares, state.totalAres), Modifier.weight(1f))
                        Stat(stringResource(R.string.me_stat_checks), "${state.checkCount}", Modifier.weight(1f))
                        Stat(stringResource(R.string.me_stat_done), "${state.tasksDone}", Modifier.weight(1f))
                    }
                }
            }
        }
        if (state.session?.remote == false) {
            item { Banner(stringResource(R.string.me_local_session), BannerKind.INFO, Modifier.padding(horizontal = 20.dp)) }
        }
        item {
            InamaCard(Modifier.padding(horizontal = 20.dp), padding = 6.dp, spacing = 0.dp) {
                MenuRow(R.drawable.ic_farm, stringResource(R.string.me_menu_farm), onFarm)
                Divider()
                MenuRow(R.drawable.ic_records, stringResource(R.string.me_menu_history), onHistory)
                Divider()
                MenuRow(R.drawable.ic_edit, stringResource(R.string.me_menu_profile), onEditProfile)
            }
        }
        item {
            InamaCard(Modifier.padding(horizontal = 20.dp), padding = 6.dp, spacing = 0.dp) {
                MenuRow(R.drawable.ic_sliders, stringResource(R.string.me_menu_settings), onSettings)
                Divider()
                MenuRow(R.drawable.ic_help, stringResource(R.string.me_menu_help), onHelp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.me_version, container.config.versionName), style = MaterialTheme.typography.bodySmall, color = c.muted)
                Text(stringResource(R.string.advice_disclaimer), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f), maxLines = 1)
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, onClick: () -> Unit, detail: String? = null) {
    val c = Inama.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
    }
}

// =============================================================================== Settings

@Composable
fun SettingsScreen(onBack: () -> Unit, onReset: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(container.settingsRepository, container.serverRepository, container.sessionRepository, container.dataResetter, container.config.defaultApiBaseUrl)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.settings
    val c = Inama.colors
    var confirmReset by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        ScreenHeader(stringResource(R.string.settings_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---- language
            item { Eyebrow(stringResource(R.string.settings_language)) }
            item {
                InamaCard(padding = 6.dp, spacing = 0.dp) {
                    AppLanguage.entries.forEachIndexed { i, lang ->
                        if (i > 0) Divider()
                        LanguageRow(lang, selected = s.language == lang) { vm.update { it.copy(language = lang) } }
                    }
                }
            }
            item { Text(stringResource(R.string.settings_language_note), style = MaterialTheme.typography.bodySmall, color = c.muted) }

            // ---- listening
            item { Eyebrow(stringResource(R.string.settings_listening)) }
            item {
                InamaCard(spacing = 12.dp) {
                    ToggleLine(stringResource(R.string.settings_read_aloud), stringResource(R.string.settings_read_aloud_body), s.readAloud) { on -> vm.update { it.copy(readAloud = on) } }
                    Divider()
                    Text(stringResource(R.string.settings_speech_speed), style = MaterialTheme.typography.labelLarge, color = c.ink)
                    Segmented(
                        options = listOf(SpeechRate.SLOW to stringResource(R.string.speed_slow), SpeechRate.NORMAL to stringResource(R.string.speed_normal), SpeechRate.FAST to stringResource(R.string.speed_fast)),
                        selected = s.speechRate,
                        onSelect = { r -> vm.update { it.copy(speechRate = r) } },
                    )
                }
            }

            // ---- display
            item { Eyebrow(stringResource(R.string.settings_display)) }
            item {
                InamaCard(spacing = 12.dp) {
                    Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.labelLarge, color = c.ink)
                    Segmented(
                        options = listOf(TextScale.NORMAL to stringResource(R.string.text_normal), TextScale.LARGE to stringResource(R.string.text_large), TextScale.EXTRA_LARGE to stringResource(R.string.text_extra_large)),
                        selected = s.textScale,
                        onSelect = { t -> vm.update { it.copy(textScale = t) } },
                    )
                    Divider()
                    ToggleLine(stringResource(R.string.settings_high_contrast), stringResource(R.string.settings_high_contrast_body), s.highContrast) { on -> vm.update { it.copy(highContrast = on) } }
                }
            }

            // ---- data & AI
            item { Eyebrow(stringResource(R.string.settings_data)) }
            item {
                InamaCard(spacing = 12.dp) {
                    ToggleLine(stringResource(R.string.settings_low_data), stringResource(R.string.settings_low_data_body), s.lowDataMode) { on -> vm.update { it.copy(lowDataMode = on) } }
                    Divider()
                    Text(stringResource(R.string.settings_ai_mode), style = MaterialTheme.typography.labelLarge, color = c.ink)
                    Segmented(
                        options = listOf(AiMode.AUTO to stringResource(R.string.ai_auto), AiMode.ON_DEVICE_ONLY to stringResource(R.string.ai_on_device)),
                        selected = s.aiMode,
                        onSelect = { m -> vm.update { it.copy(aiMode = m) } },
                    )
                    Text(
                        stringResource(if (s.aiMode == AiMode.AUTO) R.string.ai_auto_body else R.string.ai_on_device_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
            }

            // ---- privacy
            item { Eyebrow(stringResource(R.string.settings_privacy)) }
            item {
                InamaCard(spacing = 12.dp) {
                    ToggleLine(stringResource(R.string.consent_share), stringResource(R.string.consent_share_body), s.shareAnonymously) { on -> vm.update { it.copy(shareAnonymously = on) } }
                    Divider()
                    ToggleLine(stringResource(R.string.consent_promoter), stringResource(R.string.consent_promoter_body), s.promoterCanSeeFields) { on -> vm.update { it.copy(promoterCanSeeFields = on) } }
                    Divider()
                    ToggleLine(stringResource(R.string.settings_sms), stringResource(R.string.settings_sms_body), s.smsCopies) { on -> vm.update { it.copy(smsCopies = on) } }
                }
            }

            // ---- developer / server
            item { Eyebrow(stringResource(R.string.settings_server)) }
            item { ServerCard(state, vm) }

            // ---- account
            item { Eyebrow(stringResource(R.string.settings_account)) }
            item {
                InamaCard(spacing = 10.dp) {
                    InamaButton(stringResource(R.string.settings_sign_out), { confirmSignOut = true }, kind = ButtonKind.SECONDARY, icon = R.drawable.ic_arrow)
                    InamaButton(stringResource(R.string.settings_reset), { confirmReset = true }, kind = ButtonKind.CLAY, icon = R.drawable.ic_trash, enabled = !state.resetting)
                    Text(stringResource(R.string.settings_reset_body), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmReset = false; container.speechOutput.stop(); vm.resetAll(onReset) }) { Text(stringResource(R.string.settings_reset_confirm), color = c.clay) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut(onReset) }) { Text(stringResource(R.string.settings_sign_out)) } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun LanguageRow(lang: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val c = Inama.colors
    val enabled = lang.availableInThisVersion
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(R.drawable.ic_globe, background = if (selected) c.sprout else c.sand, tint = if (enabled) c.ink else c.muted, size = 40.dp)
        Text(lang.nativeName, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.ink else c.muted, modifier = Modifier.weight(1f))
        when {
            !enabled -> Chip(stringResource(R.string.coming_soon), background = c.sand, foreground = c.muted, height = 28.dp)
            selected -> InamaIcon(R.drawable.ic_check, tint = c.forest, size = 22.dp)
        }
    }
}

@Composable
private fun ServerCard(state: SettingsUiState, vm: SettingsViewModel) {
    val c = Inama.colors
    InamaCard(spacing = 10.dp) {
        Text(stringResource(R.string.settings_server_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
        OutlinedTextField(
            value = state.serverDraft,
            onValueChange = vm::onServerDraft,
            label = { Text(stringResource(R.string.settings_server_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InamaButton(stringResource(R.string.settings_server_test), vm::testServer, kind = ButtonKind.SECONDARY, modifier = Modifier.weight(1f), enabled = state.serverCheck != ServerCheck.Checking, height = 48.dp)
            InamaButton(stringResource(R.string.action_save), vm::saveServer, modifier = Modifier.weight(1f), height = 48.dp)
        }
        when (val check = state.serverCheck) {
            ServerCheck.Idle -> Unit
            ServerCheck.Checking -> Text(stringResource(R.string.settings_server_checking), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            is ServerCheck.Done -> {
                val h = check.health
                if (h.reachable) {
                    Banner(
                        stringResource(
                            R.string.settings_server_ok,
                            h.aiModel?.let { "${h.aiEngine} ($it)" } ?: (h.aiEngine ?: "—"),
                            stringResource(if (h.liveWeather) R.string.settings_weather_live else R.string.settings_weather_demo),
                        ),
                        BannerKind.SUCCESS,
                    )
                } else {
                    Banner(stringResource(R.string.settings_server_fail), BannerKind.WARNING)
                }
            }
        }
        if (state.settings.serverUrlOverride != null) {
            InamaButton(stringResource(R.string.settings_server_reset), vm::resetServer, kind = ButtonKind.GHOST)
        }
    }
}

// =============================================================================== Help

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val c = Inama.colors
    val helpline = container.config.helplineNumber
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.help_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InamaCard(background = c.forest, border = null, spacing = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(R.drawable.ic_phone, background = c.sprout, tint = c.ink)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.help_call_title), style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(stringResource(R.string.help_call_body, helpline), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    InamaButton(
                        stringResource(R.string.help_call_cta, helpline),
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$helpline"))) } },
                        kind = ButtonKind.SPROUT,
                        icon = R.drawable.ic_phone,
                    )
                }
            }
            item { Eyebrow(stringResource(R.string.help_faq)) }
            val faqs = listOf(
                R.string.faq_offline_q to R.string.faq_offline_a,
                R.string.faq_sure_q to R.string.faq_sure_a,
                R.string.faq_photo_q to R.string.faq_photo_a,
                R.string.faq_data_q to R.string.faq_data_a,
                R.string.faq_language_q to R.string.faq_language_a,
                R.string.faq_person_q to R.string.faq_person_a,
            )
            faqs.forEach { (q, a) -> item { FaqItem(stringResource(q), stringResource(a)) } }
            item {
                Text(stringResource(R.string.help_about), style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    val c = Inama.colors
    var open by remember { mutableStateOf(false) }
    InamaCard(padding = 14.dp, spacing = 8.dp, onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(question, style = MaterialTheme.typography.labelLarge, color = c.ink, modifier = Modifier.weight(1f))
            InamaIcon(if (open) R.drawable.ic_minus else R.drawable.ic_plus, tint = c.forest, size = 20.dp)
        }
        if (open) Text(answer, style = MaterialTheme.typography.bodyLarge, color = c.muted)
    }
}
