package rw.inama.app.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import rw.inama.app.R
import rw.inama.app.domain.model.AppLanguage
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.BannerKind
import rw.inama.app.ui.components.Banner
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.ChipFlow
import rw.inama.app.ui.components.ConfidenceMeter
import rw.inama.app.ui.components.Divider
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.Illustration
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.UrgencyChip
import rw.inama.app.domain.model.Confidence
import rw.inama.app.domain.model.ConfidenceLevel
import rw.inama.app.domain.model.Urgency
import rw.inama.app.ui.theme.Inama

// ------------------------------------------------------------------------------- 01 Splash

@Composable
fun SplashScreen(returning: Boolean?, onStart: () -> Unit, onContinue: () -> Unit) {
    StatusBarIcons(light = true)
    LaunchedEffect(returning) {
        if (returning == true) {
            delay(900)
            onContinue()
        }
    }
    Box(Modifier.fillMaxSize().background(Inama.colors.forestDeep)) {
        Illustration(R.drawable.bg_aerial, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x5A0F2112), 0.45f to Color(0x000F2112), 0.62f to Color(0x8C0F2112), 1f to Color(0xF20F2112),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Inama.colors.ground), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.img_logo_mark), contentDescription = null, modifier = Modifier.size(38.dp))
                }
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Eyebrow(stringResource(R.string.splash_eyebrow), color = Inama.colors.sprout)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.splash_title), style = MaterialTheme.typography.displayMedium, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.splash_body), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.88f))
            Spacer(Modifier.height(26.dp))
            // Hidden while settings load, and for returning farmers (they go straight to Home).
            if (returning == false) {
                StartPill(stringResource(R.string.splash_cta), onStart)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.splash_footer),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** The reference's arrow pill: dark circle with an arrow inside a leaf-green pill. */
@Composable
private fun StartPill(text: String, onClick: () -> Unit) {
    val c = Inama.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(50))
            .background(c.sprout)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 7.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(c.forestDeep), contentAlignment = Alignment.Center) {
            InamaIcon(R.drawable.ic_arrow, tint = Color.White, size = 22.dp)
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f).padding(start = 12.dp))
        InamaIcon(R.drawable.ic_chev, tint = c.ink.copy(alpha = 0.4f), size = 16.dp)
        InamaIcon(R.drawable.ic_chev, tint = c.ink.copy(alpha = 0.65f), size = 16.dp)
        InamaIcon(R.drawable.ic_chev, tint = c.ink, size = 16.dp)
    }
}

// ------------------------------------------------------------------------------- 02 Language

@Composable
fun LanguageScreen(vm: OnboardingViewModel, onContinue: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    StatusBarIcons(light = false)
    Column(
        Modifier.fillMaxSize().background(Inama.colors.ground).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.Image(painterResource(R.drawable.img_logo_mark), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Inama.colors.forest)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.headlineLarge, color = Inama.colors.ink)
        Text(stringResource(R.string.language_body), style = MaterialTheme.typography.bodyLarge, color = Inama.colors.muted)
        Spacer(Modifier.height(4.dp))
        AppLanguage.entries.forEach { lang ->
            LanguageOption(lang, selected = state.language == lang, onClick = { vm.chooseLanguage(lang) })
        }
        Text(stringResource(R.string.language_note), style = MaterialTheme.typography.bodyMedium, color = Inama.colors.muted)
        Spacer(Modifier.height(12.dp))
        InamaButton(stringResource(R.string.action_continue), onContinue)
    }
}

@Composable
private fun LanguageOption(lang: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val c = Inama.colors
    val shape = RoundedCornerShape(22.dp)
    val enabled = lang.availableInThisVersion
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.sproutSoft else c.paper)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) c.forest else c.line2, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) c.forest else c.sand),
            contentAlignment = Alignment.Center,
        ) {
            Text(lang.tag.uppercase(), style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else c.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(lang.nativeName, style = MaterialTheme.typography.titleMedium, color = if (enabled) c.ink else c.muted)
            Text(
                if (enabled) stringResource(R.string.language_available) else stringResource(R.string.language_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
        }
        if (selected) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.forest), contentAlignment = Alignment.Center) {
                InamaIcon(R.drawable.ic_check, tint = Color.White, size = 16.dp)
            }
        } else if (!enabled) {
            InamaIcon(R.drawable.ic_lock, tint = c.muted, size = 20.dp)
        }
    }
}

// ------------------------------------------------------------------------------- 03–05 Intro

@Composable
fun IntroScreen(onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = listOf(
        Triple(R.string.intro1_eyebrow, R.string.intro1_title, R.string.intro1_body),
        Triple(R.string.intro2_eyebrow, R.string.intro2_title, R.string.intro2_body),
        Triple(R.string.intro3_eyebrow, R.string.intro3_title, R.string.intro3_body),
    )
    StatusBarIcons(light = false)
    Column(Modifier.fillMaxSize().background(Inama.colors.ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).semantics { contentDescription = "${page + 1} / 3" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(Modifier.height(8.dp).width(if (i == page) 28.dp else 8.dp).clip(RoundedCornerShape(4.dp)).background(if (i == page) Inama.colors.forest else Inama.colors.line2))
                }
            }
            TextButton(onClick = onDone) { Text(stringResource(R.string.action_skip), style = MaterialTheme.typography.labelLarge, color = Inama.colors.forest) }
        }
        Crossfade(targetState = page, label = "intro", modifier = Modifier.weight(1f)) { p ->
            val (eyebrow, title, body) = pages[p]
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(360.dp).clip(RoundedCornerShape(32.dp)).background(Inama.colors.sand),
                ) { IntroArt(p) }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Eyebrow(stringResource(eyebrow), color = Inama.colors.leaf)
                    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, color = Inama.colors.ink)
                    val bodyText = stringResource(body)
                    Text(bodyText, style = MaterialTheme.typography.bodyLarge, color = Inama.colors.muted)
                    ListenButton("intro_$p", stringResource(title) + ". " + bodyText)
                }
            }
        }
        InamaButton(
            text = stringResource(if (page < 2) R.string.action_next else R.string.action_get_started),
            onClick = { if (page < 2) page++ else onDone() },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun IntroArt(page: Int) {
    val c = Inama.colors
    when (page) {
        0 -> Box(Modifier.fillMaxSize()) {
            Illustration(R.drawable.img_hills_day, Modifier.fillMaxSize())
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.align(Alignment.End).width(250.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)).background(c.forest).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InamaIcon(R.drawable.ic_mic, tint = c.sprout, size = 18.dp)
                    Text(stringResource(R.string.intro1_sample_q), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
                Row(
                    Modifier.width(280.dp).clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).background(c.paper).padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.img_logo_mark), contentDescription = null, modifier = Modifier.size(32.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.intro1_sample_a), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InamaIcon(R.drawable.ic_speaker, tint = c.forest, size = 16.dp)
                            Text(stringResource(R.string.intro1_playing), style = MaterialTheme.typography.labelSmall, color = c.forest)
                        }
                    }
                }
            }
        }
        1 -> Box(Modifier.fillMaxSize()) {
            Illustration(R.drawable.img_cassava_marked, Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.paper).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UrgencyChip(Urgency.THIS_WEEK)
                    Chip(stringResource(R.string.crop_cassava_short), background = c.claySoft, foreground = c.clayInk, icon = R.drawable.ic_alert)
                }
                Text(stringResource(R.string.intro2_sample), style = MaterialTheme.typography.titleSmall, color = c.ink)
                ConfidenceMeter(Confidence(ConfidenceLevel.HIGH, 0.86, "none"), showReason = false)
            }
        }
        else -> Box(Modifier.fillMaxSize()) {
            Illustration(R.drawable.img_hills_plain, Modifier.fillMaxSize())
            Row(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.paper).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(R.drawable.ic_storm, background = c.skySoft, tint = c.sky)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UrgencyChip(Urgency.THIS_WEEK, text = stringResource(R.string.intro3_sample_day))
                    Text(stringResource(R.string.intro3_sample_title), style = MaterialTheme.typography.titleSmall, color = c.ink)
                    Text(stringResource(R.string.intro3_sample_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------- 06 Privacy

@Composable
fun PrivacyScreen(vm: OnboardingViewModel, onBack: () -> Unit, onAgree: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.privacy_header), onBack = onBack, actions = {
            ListenButton("privacy", stringResource(R.string.privacy_title) + ". " + stringResource(R.string.privacy_keep_body) + " " + stringResource(R.string.privacy_why_body) + " " + stringResource(R.string.privacy_where_body) + " " + stringResource(R.string.privacy_who_body))
        })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(R.drawable.ic_shield, background = c.sprout, tint = c.forest, size = 64.dp)
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineMedium, color = c.ink)
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            InamaCard(spacing = 12.dp) {
                PrivacyRow(R.drawable.ic_user, R.string.privacy_keep, R.string.privacy_keep_body)
                Divider()
                PrivacyRow(R.drawable.ic_target, R.string.privacy_why, R.string.privacy_why_body)
                Divider()
                PrivacyRow(R.drawable.ic_pin, R.string.privacy_where, R.string.privacy_where_body)
                Divider()
                PrivacyRow(R.drawable.ic_users, R.string.privacy_who, R.string.privacy_who_body)
            }
            InamaCard(spacing = 12.dp) {
                ToggleLine(stringResource(R.string.consent_share), stringResource(R.string.consent_share_body), state.shareAnonymously) { vm.setConsent(shareAnonymously = it) }
                Divider()
                ToggleLine(stringResource(R.string.consent_promoter), stringResource(R.string.consent_promoter_body), state.promoterCanSeeFields) { vm.setConsent(promoter = it) }
            }
        }
        InamaButton(
            stringResource(R.string.privacy_agree),
            onClick = { vm.saveConsent(); onAgree() },
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PrivacyRow(icon: Int, title: Int, body: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = Inama.colors.ink)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = Inama.colors.muted)
        }
    }
}

@Composable
fun ToggleLine(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Inama.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.forest, checkedThumbColor = Color.White, uncheckedTrackColor = c.line2, uncheckedThumbColor = Color.White, uncheckedBorderColor = c.line2),
        )
    }
}

// ------------------------------------------------------------------------------- 07 Phone

@Composable
fun PhoneScreen(vm: OnboardingViewModel, onBack: () -> Unit, onCodeSent: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        ScreenHeader("", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.phone_title), style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Text(stringResource(R.string.phone_body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            OutlinedTextField(
                value = state.phoneInput,
                onValueChange = vm::onPhoneChange,
                label = { Text(stringResource(R.string.phone_label)) },
                prefix = { Text("+250 ", style = MaterialTheme.typography.titleMedium, color = c.ink) },
                placeholder = { Text("7XX XXX XXX") },
                isError = state.phoneError,
                supportingText = { if (state.phoneError) Text(stringResource(R.string.phone_error)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            InamaButton(
                if (state.sendingCode) stringResource(R.string.phone_sending) else stringResource(R.string.phone_send),
                onClick = { vm.sendCode(onCodeSent) },
                enabled = !state.sendingCode,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Divider(Modifier.weight(1f))
                Text(stringResource(R.string.or), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                Divider(Modifier.weight(1f))
            }
            InfoCard(R.drawable.ic_hash, stringResource(R.string.phone_ussd_title), stringResource(R.string.phone_ussd_body))
            InfoCard(R.drawable.ic_users, stringResource(R.string.phone_promoter_title), stringResource(R.string.phone_promoter_body))
        }
    }
}

@Composable
fun InfoCard(icon: Int, title: String, body: String) {
    InamaCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, background = Inama.colors.sand, tint = Inama.colors.ink)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = Inama.colors.ink)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Inama.colors.muted)
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Inama.colors.forest,
    unfocusedBorderColor = Inama.colors.line2,
    focusedContainerColor = Inama.colors.paper,
    unfocusedContainerColor = Inama.colors.paper,
    focusedLabelColor = Inama.colors.forest,
    cursorColor = Inama.colors.forest,
)

// ------------------------------------------------------------------------------- 08 Verify

@Composable
fun VerifyScreen(vm: OnboardingViewModel, onBack: () -> Unit, onVerified: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    var secondsLeft by remember { mutableIntStateOf(45) }
    LaunchedEffect(state.challenge) {
        secondsLeft = 45
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }
    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        ScreenHeader("", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Text(stringResource(R.string.verify_body, state.challenge?.maskedPhone ?: state.fullPhone), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            OutlinedTextField(
                value = state.codeInput,
                onValueChange = vm::onCodeChange,
                label = { Text(stringResource(R.string.verify_label)) },
                singleLine = true,
                isError = state.codeError != null,
                supportingText = {
                    when (state.codeError) {
                        null -> Unit
                        "expired_code" -> Text(stringResource(R.string.verify_expired))
                        "network" -> Text(stringResource(R.string.error_network))
                        else -> Text(stringResource(R.string.verify_wrong))
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 10.sp),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            state.challenge?.devCode?.let { code ->
                Banner(stringResource(R.string.verify_demo_code, code), BannerKind.INFO, action = stringResource(R.string.verify_use_code), onAction = vm::useDevCode)
            }
            InamaButton(
                if (state.verifying) stringResource(R.string.verify_checking) else stringResource(R.string.verify_cta),
                onClick = { vm.verify(onVerified) },
                enabled = state.codeInput.length == 6 && !state.verifying,
            )
            if (secondsLeft > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InamaIcon(R.drawable.ic_clock, tint = c.muted, size = 18.dp)
                    Text(stringResource(R.string.verify_resend_in, secondsLeft), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            } else {
                InamaButton(stringResource(R.string.verify_resend), onClick = { vm.sendCode {} }, kind = ButtonKind.GHOST)
            }
        }
    }
}

// ------------------------------------------------------------------------------- 11 Profile

@Composable
fun ProfileSetupScreen(vm: OnboardingViewModel, onBack: () -> Unit, onSaved: () -> Unit, editing: Boolean = false) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    var pickDistrict by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.ground).imePadding()) {
        ScreenHeader(
            stringResource(if (editing) R.string.profile_edit_title else R.string.profile_title),
            subtitle = if (editing) null else stringResource(R.string.step_of, 1, 2),
            onBack = onBack,
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onName,
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Eyebrow(stringResource(R.string.profile_where))
            val district = state.districts.firstOrNull { it.id == state.districtId }
            InamaCard(padding = 14.dp, onClick = { pickDistrict = true }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(R.drawable.ic_pin, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(district?.name ?: stringResource(R.string.profile_choose_district), style = MaterialTheme.typography.labelLarge, color = c.ink)
                        Text(district?.let { stringResource(R.string.profile_province, it.province) } ?: stringResource(R.string.profile_district_hint), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                    InamaIcon(R.drawable.ic_chevdown, tint = c.muted, size = 20.dp)
                }
            }
            OutlinedTextField(
                value = state.sector,
                onValueChange = vm::onSector,
                label = { Text(stringResource(R.string.profile_sector)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Eyebrow(stringResource(R.string.profile_keeps))
            ChipFlow {
                PillChoice(stringResource(R.string.profile_crops), state.keepsCrops, { vm.onKeeps(crops = !state.keepsCrops) }, icon = R.drawable.ic_farm, role = Role.Checkbox)
                PillChoice(stringResource(R.string.profile_animals), state.keepsAnimals, { vm.onKeeps(animals = !state.keepsAnimals) }, icon = R.drawable.ic_cow, role = Role.Checkbox)
            }
        }
        InamaButton(
            stringResource(if (editing) R.string.action_save else R.string.action_continue),
            onClick = { vm.saveProfile(onSaved) },
            enabled = state.profileValid,
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
    if (pickDistrict) {
        AlertDialog(
            onDismissRequest = { pickDistrict = false },
            confirmButton = { TextButton(onClick = { pickDistrict = false }) { Text(stringResource(R.string.action_close)) } },
            title = { Text(stringResource(R.string.profile_choose_district)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.districts, key = { it.id }) { d ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.onDistrict(d.id); pickDistrict = false }.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.labelLarge, color = c.ink)
                                Text(d.province, style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            if (d.id == state.districtId) InamaIcon(R.drawable.ic_check, tint = c.forest, size = 20.dp)
                        }
                    }
                }
            },
        )
    }
}

// ------------------------------------------------------------------------------- 14 Done

@Composable
fun SetupDoneScreen(firstName: String, fieldSummaries: List<Pair<String, String>>, onGoHome: () -> Unit) {
    val c = Inama.colors
    StatusBarIcons(light = true)
    Column(Modifier.fillMaxSize().background(c.ground)) {
        Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
            Illustration(R.drawable.bg_aerial_header, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x4015301A)))
            Box(
                Modifier.align(Alignment.Center).size(108.dp).border(10.dp, Color.White.copy(alpha = 0.6f), CircleShape).clip(CircleShape).background(c.sprout),
                contentAlignment = Alignment.Center,
            ) { InamaIcon(R.drawable.ic_check, tint = c.forest, size = 56.dp) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.done_title, firstName), style = MaterialTheme.typography.headlineLarge, color = c.ink)
            Text(stringResource(R.string.done_body), style = MaterialTheme.typography.bodyLarge, color = c.muted)
            if (fieldSummaries.isNotEmpty()) {
                InamaCard(spacing = 10.dp) {
                    fieldSummaries.forEachIndexed { i, (cropId, text) ->
                        if (i > 0) Divider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rw.inama.app.ui.components.CropImage(cropId, 40.dp)
                            Text(text, style = MaterialTheme.typography.labelLarge, color = c.ink)
                        }
                    }
                }
            }
            InamaCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(R.drawable.ic_sunrise, background = c.amberSoft, tint = c.amberInk)
                    Text(stringResource(R.string.done_briefing), style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        InamaButton(stringResource(R.string.done_cta), onGoHome, modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp))
    }
}
