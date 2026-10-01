package rw.inama.app.feature.ask

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import rw.inama.app.R
import rw.inama.app.core.speech.SpeechInput
import rw.inama.app.domain.model.Answer
import rw.inama.app.domain.model.AppSettings
import rw.inama.app.feature.onboarding.fieldColors
import rw.inama.app.feature.scan.AboutThisAnswer
import rw.inama.app.ui.LocalAppContainer
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.Banner
import rw.inama.app.ui.components.BannerKind
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.ChipFlow
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.ConfidenceMeter
import rw.inama.app.ui.components.EmptyState
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.ListenButton
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.OfflineBanner
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.components.ThreeQuestions
import rw.inama.app.ui.components.UrgencyChip
import rw.inama.app.ui.components.relativeTime
import rw.inama.app.ui.theme.Inama

@Composable
fun AskScreen(onBack: () -> Unit, onScan: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AskViewModel = viewModel {
        AskViewModel(
            container.askAdvisor, container.answerRepository, container.fieldRepository, container.catalogRepository,
            container.settingsRepository, container.taskRepository, container.addAdviceToTasks, container.connectivity.isOnline,
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val speech = container.speechInput
    val voice by speech.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Inama.colors

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.start(settings.language)
    }
    fun listen() {
        container.speechOutput.stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speech.start(settings.language)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(voice) {
        val v = voice
        if (v is SpeechInput.State.Result) {
            vm.ask(v.text)
            speech.reset()
        }
    }
    DisposableEffect(Unit) { onDispose { speech.release() } }

    val listState = rememberLazyListState()
    LaunchedEffect(state.answer?.id, state.thinking) { if (state.question != null) listState.animateScrollToItem(0) }

    Box(Modifier.fillMaxSize().background(c.ground)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            ScreenHeader(
                stringResource(R.string.ask_title),
                subtitle = stringResource(R.string.ask_subtitle),
                onBack = onBack,
                actions = {
                    if (state.question != null) CircleIconButton(R.drawable.ic_plus, stringResource(R.string.ask_new_question), vm::startOver)
                },
            )
            LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!state.online) item { OfflineBanner() }
                (voice as? SpeechInput.State.Failed)?.let { failed ->
                    item { Banner(stringResource(voiceError(failed.reason)), BannerKind.WARNING, action = stringResource(R.string.action_ok), onAction = speech::reset) }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Eyebrow(stringResource(R.string.ask_about_crop))
                        ChipFlow {
                            PillChoice(stringResource(R.string.ask_any_crop), state.cropId == null, { vm.selectCrop(null) })
                            state.crops.forEach { crop -> PillChoice(crop.name, state.cropId == crop.id, { vm.selectCrop(crop.id) }) }
                        }
                    }
                }
                val question = state.question
                if (question == null) {
                    item { MicHero(onListen = ::listen) }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Eyebrow(stringResource(R.string.ask_try))
                            listOf(R.string.ask_suggest_1, R.string.ask_suggest_2, R.string.ask_suggest_3, R.string.ask_suggest_4).forEach { res ->
                                val text = stringResource(res)
                                SuggestionRow(text) { vm.ask(text) }
                            }
                        }
                    }
                    if (state.recent.isNotEmpty()) {
                        item { Eyebrow(stringResource(R.string.ask_recent)) }
                        items(state.recent, key = { it.id }) { a ->
                            InamaCard(padding = 14.dp, onClick = { vm.show(a) }) {
                                Text(a.question, style = MaterialTheme.typography.labelLarge, color = c.ink)
                                Text("${a.headline} · ${relativeTime(a.createdAtMillis)}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                            }
                        }
                    }
                } else {
                    item { QuestionBubble(question) }
                    when {
                        state.thinking -> item { ThinkingBubble() }
                        state.failed -> item {
                            InamaCard(spacing = 10.dp) {
                                Text(stringResource(R.string.ask_failed), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                                InamaButton(stringResource(R.string.action_try_again), vm::retry, icon = R.drawable.ic_sync)
                            }
                        }
                        state.answer != null -> item {
                            AnswerContent(
                                answer = state.answer!!,
                                addedCount = state.addedCount,
                                inTasks = false,
                                onAddTasks = vm::addStepsToTasks,
                                onScan = onScan,
                            )
                        }
                    }
                }
            }
            AskInputBar(
                value = state.input,
                onValue = vm::onInput,
                onSend = vm::send,
                onMic = ::listen,
                enabled = !state.thinking,
            )
        }

        val v = voice
        if (v is SpeechInput.State.Listening) {
            ListeningOverlay(v.partial, v.level, onStop = speech::stop, onCancel = speech::cancel)
        }
    }
}

private fun voiceError(reason: SpeechInput.Reason) = when (reason) {
    SpeechInput.Reason.NOT_AVAILABLE -> R.string.voice_not_available
    SpeechInput.Reason.NO_MATCH -> R.string.voice_no_match
    SpeechInput.Reason.NO_PERMISSION -> R.string.voice_no_permission
    SpeechInput.Reason.NETWORK -> R.string.voice_network
    SpeechInput.Reason.OTHER -> R.string.voice_other
}

@Composable
private fun MicHero(onListen: () -> Unit) {
    val c = Inama.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(132.dp).clip(CircleShape).background(c.sproutSoft).clickable(role = Role.Button, onClick = onListen),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(c.sprout), contentAlignment = Alignment.Center) {
                InamaIcon(R.drawable.ic_mic, tint = c.ink, size = 44.dp, contentDescription = stringResource(R.string.cd_ask_by_voice))
            }
        }
        Text(stringResource(R.string.ask_tap_to_speak), style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        Text(stringResource(R.string.ask_language_note), style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    val c = Inama.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.paper).clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InamaIcon(R.drawable.ic_msg, tint = c.forest, size = 20.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
        InamaIcon(R.drawable.ic_arrow, tint = c.muted, size = 18.dp)
    }
}

@Composable
fun QuestionBubble(text: String) {
    val c = Inama.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(c.forest).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ThinkingBubble() {
    val c = Inama.colors
    Row(
        Modifier.clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)).background(c.paper).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(color = c.forest, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.ask_thinking), style = MaterialTheme.typography.bodyLarge, color = c.muted)
    }
}

/** The structured answer: headline, confidence, the three questions, then actions and provenance. */
@Composable
fun AnswerContent(answer: Answer, addedCount: Int?, inTasks: Boolean, onAddTasks: () -> Unit, onScan: (() -> Unit)?) {
    val c = Inama.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InamaCard(spacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UrgencyChip(answer.urgency)
                Box(Modifier.weight(1f))
                ListenButton("answer_${answer.id}", listOf(answer.headline, answer.whatsHappening, answer.whyItMatters, answer.steps.joinToString(". ")).joinToString(". "))
            }
            Text(answer.headline, style = MaterialTheme.typography.titleLarge, color = c.ink)
            ConfidenceMeter(answer.confidence)
            ThreeQuestions(answer.whatsHappening, answer.whyItMatters, answer.steps, compact = true)
        }
        when {
            addedCount != null && addedCount > 0 -> Banner(stringResource(R.string.diagnosis_tasks_added, addedCount), BannerKind.SUCCESS)
            inTasks || addedCount == 0 -> Banner(stringResource(R.string.diagnosis_tasks_already), BannerKind.SUCCESS)
            else -> InamaButton(stringResource(R.string.diagnosis_add_tasks), onAddTasks, kind = ButtonKind.SECONDARY, icon = R.drawable.ic_tasks)
        }
        answer.followUp?.let { follow ->
            InamaCard(background = c.skySoft, border = null, spacing = 10.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InamaIcon(R.drawable.ic_info, tint = c.skyInk, size = 20.dp)
                    Text(follow, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                }
                if (onScan != null) InamaButton(stringResource(R.string.ask_check_with_photo), onScan, icon = R.drawable.ic_camera)
            }
        }
        AboutThisAnswer(answer.engine, answer.sources)
    }
}

@Composable
private fun AskInputBar(value: String, onValue: (String) -> Unit, onSend: () -> Unit, onMic: () -> Unit, enabled: Boolean) {
    val c = Inama.colors
    Row(
        Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text(stringResource(R.string.ask_placeholder)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = fieldColors(),
            maxLines = 4,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        if (value.isBlank()) {
            CircleIconButton(R.drawable.ic_mic, stringResource(R.string.cd_ask_by_voice), onMic, background = c.sprout, tint = c.ink, bordered = false, size = 52.dp)
        } else {
            CircleIconButton(R.drawable.ic_send, stringResource(R.string.cd_send), onSend, background = c.forest, tint = Color.White, bordered = false, size = 52.dp)
        }
    }
}

@Composable
private fun ListeningOverlay(partial: String, level: Float, onStop: () -> Unit, onCancel: () -> Unit) {
    val c = Inama.colors
    StatusBarIcons(light = true)
    BackHandler(onBack = onCancel)
    val pulse by animateFloatAsState(1f + level * 0.35f, label = "level")
    Column(
        Modifier.fillMaxSize().background(c.forestDeep.copy(alpha = 0.97f))
            // Swallow taps so nothing underneath reacts while listening.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(170.dp).scale(pulse).clip(CircleShape).background(c.sprout.copy(alpha = 0.18f)))
                    Box(Modifier.size(120.dp).clip(CircleShape).background(c.sprout), contentAlignment = Alignment.Center) {
                        InamaIcon(R.drawable.ic_mic, tint = c.ink, size = 52.dp)
                    }
                }
                Text(stringResource(R.string.ask_listening), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    partial.ifBlank { stringResource(R.string.ask_listening_hint) },
                    style = MaterialTheme.typography.titleLarge,
                    color = if (partial.isBlank()) Color.White.copy(alpha = 0.6f) else Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
        InamaButton(stringResource(R.string.ask_done_speaking), onStop, kind = ButtonKind.SPROUT, icon = R.drawable.ic_check)
        InamaButton(stringResource(R.string.action_cancel), onCancel, kind = ButtonKind.OUTLINE_LIGHT)
    }
}

// =============================================================================== Answer detail

@Composable
fun AnswerDetailScreen(id: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: AnswerDetailViewModel = viewModel(key = "answer_$id") {
        AnswerDetailViewModel(id, container.answerRepository, container.taskRepository, container.addAdviceToTasks)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.answer_title), subtitle = state.answer?.let { relativeTime(it.createdAtMillis) }, onBack = onBack)
        val answer = state.answer
        when {
            state.loading -> LoadingState()
            answer == null -> EmptyState(R.drawable.ic_msg, stringResource(R.string.answer_missing_title), stringResource(R.string.answer_missing_body), action = stringResource(R.string.action_back), onAction = onBack)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { QuestionBubble(answer.question) }
                item { AnswerContent(answer, state.addedCount, state.inTasks, vm::addStepsToTasks, onScan = null) }
            }
        }
    }
}
