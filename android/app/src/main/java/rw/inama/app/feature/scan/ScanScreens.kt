package rw.inama.app.feature.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import rw.inama.app.R
import rw.inama.app.domain.ai.AnalysisStage
import rw.inama.app.domain.model.PhotoQuality
import rw.inama.app.ui.StatusBarIcons
import rw.inama.app.ui.components.Banner
import rw.inama.app.ui.components.BannerKind
import rw.inama.app.ui.components.ButtonKind
import rw.inama.app.ui.components.Chip
import rw.inama.app.ui.components.ChipFlow
import rw.inama.app.ui.components.CircleIconButton
import rw.inama.app.ui.components.CropChoice
import rw.inama.app.ui.components.Eyebrow
import rw.inama.app.ui.components.IconTile
import rw.inama.app.ui.components.InamaButton
import rw.inama.app.ui.components.InamaCard
import rw.inama.app.ui.components.InamaIcon
import rw.inama.app.ui.components.LoadingState
import rw.inama.app.ui.components.NumberedSteps
import rw.inama.app.ui.components.PillChoice
import rw.inama.app.ui.components.ScreenHeader
import rw.inama.app.ui.theme.Inama
import java.io.File

// =============================================================================== 1 · Camera

@Composable
fun ScanScreen(vm: ScanViewModel, onClose: () -> Unit, onPhotoReady: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val c = Inama.colors
    StatusBarIcons(light = true)

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!hasCamera && !askedOnce) permission.launch(Manifest.permission.CAMERA) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.usePhoto(uri, onPhotoReady)
    }
    fun openGallery() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    var showSamples by remember { mutableStateOf(false) }
    var flashOn by rememberSaveable { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    DisposableEffect(hasCamera, lifecycleOwner) {
        // CameraX throws IllegalStateException when no camera supports the use case (e.g. no back camera).
        if (hasCamera) runCatching { controller.bindToLifecycle(lifecycleOwner) }.onFailure { vm.onCameraError() }
        onDispose { controller.unbind() }
    }

    fun capture() {
        if (state.preparing) return
        val file = vm.newCaptureFile()
        controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        try {
            controller.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = vm.usePhoto(Uri.fromFile(file), onPhotoReady)
                    override fun onError(exception: ImageCaptureException) = vm.onCameraError()
                },
            )
        } catch (e: IllegalStateException) {
            // Tapped before CameraX finished initialising, or the camera could not be bound.
            vm.onCameraError()
        }
    }

    Box(Modifier.fillMaxSize().background(c.forestDeep)) {
        if (hasCamera) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            ViewfinderGuide(Modifier.align(Alignment.Center))
        } else {
            CameraPermissionPanel(
                modifier = Modifier.align(Alignment.Center),
                onAllow = {
                    if (askedOnce) {
                        // After a refusal Android may not ask again; the app settings page always works.
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    permission.launch(Manifest.permission.CAMERA)
                },
                onGallery = ::openGallery,
                onSample = { showSamples = true },
            )
        }

        // ---- top bar
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIconButton(R.drawable.ic_x, stringResource(R.string.action_close), onClose, background = Color.Black.copy(alpha = 0.35f), tint = Color.White, bordered = false)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    state.field?.let { Text(stringResource(R.string.scan_for_field, it.name), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f)) }
                }
                if (hasCamera) {
                    CircleIconButton(
                        R.drawable.ic_flash,
                        stringResource(if (flashOn) R.string.cd_flash_on else R.string.cd_flash_off),
                        { flashOn = !flashOn },
                        background = if (flashOn) c.sprout else Color.Black.copy(alpha = 0.35f),
                        tint = if (flashOn) c.ink else Color.White,
                        bordered = false,
                    )
                }
            }
            if (hasCamera) {
                Row(
                    Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InamaIcon(R.drawable.ic_leaf, tint = c.sprout, size = 18.dp)
                    Text(stringResource(R.string.scan_tip), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
            state.error?.let { err ->
                Banner(
                    stringResource(if (err == ScanError.CAMERA_FAILED) R.string.scan_error_camera else R.string.scan_error_open),
                    BannerKind.WARNING,
                    action = stringResource(R.string.action_ok),
                    onAction = vm::clearError,
                )
            }
        }

        // ---- bottom controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideAction(R.drawable.ic_image, stringResource(R.string.scan_gallery), ::openGallery)
            Box(
                Modifier.size(84.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (hasCamera && !state.preparing) Color.White else Color.White.copy(alpha = 0.35f))
                    .clickable(enabled = hasCamera && !state.preparing, role = Role.Button, onClick = ::capture),
                contentAlignment = Alignment.Center,
            ) { InamaIcon(R.drawable.ic_camera, tint = c.forest, size = 30.dp, contentDescription = stringResource(R.string.cd_take_photo)) }
            SideAction(R.drawable.ic_sparkle, stringResource(R.string.scan_samples)) { showSamples = true }
        }

        if (state.preparing) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(color = c.sprout)
                    Text(stringResource(R.string.scan_preparing), style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
            }
        }

        if (showSamples) {
            SampleSheet(
                onPick = { sample -> showSamples = false; vm.useSample(sample, onPhotoReady) },
                onDismiss = { showSamples = false },
            )
        }
    }
}

@Composable
private fun SideAction(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            InamaIcon(icon, tint = Color.White, size = 24.dp)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Corner brackets showing where to place the leaf (PhotoAnalyzer measures the centre area). */
@Composable
private fun ViewfinderGuide(modifier: Modifier = Modifier) {
    val sprout = Inama.colors.sprout
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(270.dp)) {
            val len = size.minDimension * 0.18f
            val stroke = 5.dp.toPx()
            val w = size.width
            val h = size.height
            fun corner(a: Offset, b: Offset, cc: Offset) {
                drawLine(sprout, a, b, stroke, StrokeCap.Round)
                drawLine(sprout, b, cc, stroke, StrokeCap.Round)
            }
            corner(Offset(0f, len), Offset(0f, 0f), Offset(len, 0f))
            corner(Offset(w - len, 0f), Offset(w, 0f), Offset(w, len))
            corner(Offset(0f, h - len), Offset(0f, h), Offset(len, h))
            corner(Offset(w - len, h), Offset(w, h), Offset(w, h - len))
        }
        Text(stringResource(R.string.scan_guide), style = MaterialTheme.typography.labelLarge, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CameraPermissionPanel(modifier: Modifier, onAllow: () -> Unit, onGallery: () -> Unit, onSample: () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(R.drawable.ic_camera, background = Color.White.copy(alpha = 0.14f), tint = Color.White, size = 72.dp)
        Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
        Text(stringResource(R.string.scan_permission_body), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        InamaButton(stringResource(R.string.scan_permission_allow), onAllow, kind = ButtonKind.SPROUT, icon = R.drawable.ic_camera)
        InamaButton(stringResource(R.string.scan_use_gallery), onGallery, kind = ButtonKind.OUTLINE_LIGHT, icon = R.drawable.ic_image)
        InamaButton(stringResource(R.string.scan_use_sample), onSample, kind = ButtonKind.OUTLINE_LIGHT, icon = R.drawable.ic_sparkle)
    }
}

@Composable
private fun SampleSheet(onPick: (SamplePhoto) -> Unit, onDismiss: () -> Unit) {
    val c = Inama.colors
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.ground)
                // Swallow taps so they don't reach the dismiss scrim behind the sheet.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.samples_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(stringResource(R.string.samples_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            SamplePhoto.entries.forEach { sample ->
                InamaCard(padding = 10.dp, onClick = { onPick(sample) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = "file:///android_asset/samples/${sample.asset}",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(sampleTitle(sample)), style = MaterialTheme.typography.labelLarge, color = c.ink)
                            Text(stringResource(sampleBody(sample)), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                        }
                        InamaIcon(R.drawable.ic_chev, tint = c.muted, size = 20.dp)
                    }
                }
            }
        }
    }
}

private fun sampleTitle(s: SamplePhoto) = when (s) {
    SamplePhoto.CASSAVA_SPOTS -> R.string.sample_cassava_spots
    SamplePhoto.CASSAVA_HEALTHY -> R.string.sample_cassava_healthy
    SamplePhoto.BEANS_BLURRY -> R.string.sample_beans_blurry
}

private fun sampleBody(s: SamplePhoto) = when (s) {
    SamplePhoto.CASSAVA_SPOTS -> R.string.sample_cassava_spots_body
    SamplePhoto.CASSAVA_HEALTHY -> R.string.sample_cassava_healthy_body
    SamplePhoto.BEANS_BLURRY -> R.string.sample_beans_blurry_body
}

// =============================================================================== 2 · Review

@Composable
fun PhotoReviewScreen(vm: ScanViewModel, onBack: () -> Unit, onRetake: () -> Unit, onAnalyse: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    val kb = state.kb
    val photo = state.photo
    if (photo == null || kb == null) {
        // Process death or an unexpected entry: there is no photo to review.
        Column(Modifier.fillMaxSize().background(c.ground)) {
            ScreenHeader(stringResource(R.string.review_title), onBack = onBack)
            LoadingState()
        }
        LaunchedEffect(photo) { if (photo == null) onRetake() }
        return
    }
    val poor = state.quality.isProblem && !state.acceptedPoorPhoto
    Column(Modifier.fillMaxSize().background(c.ground)) {
        ScreenHeader(stringResource(R.string.review_title), subtitle = stringResource(R.string.review_subtitle), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoPreview(photo, state.quality)
            if (state.quality.isProblem) {
                val text = kb.photoProblems.forQuality(state.quality)
                InamaCard(background = c.amberSoft, border = c.amber, spacing = 10.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        InamaIcon(R.drawable.ic_alert, tint = c.amberInk, size = 22.dp)
                        Text(text.title, style = MaterialTheme.typography.titleSmall, color = c.amberInk)
                    }
                    Text(text.body, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                    NumberedSteps(kb.photoProblems.tips)
                    if (!state.acceptedPoorPhoto) {
                        InamaButton(stringResource(R.string.review_retake), onRetake, icon = R.drawable.ic_camera)
                        InamaButton(stringResource(R.string.review_use_anyway), vm::acceptPoorPhoto, kind = ButtonKind.GHOST)
                    }
                }
            } else if (state.quality == PhotoQuality.OK) {
                Banner(stringResource(R.string.review_photo_ok), BannerKind.SUCCESS)
            }

            Eyebrow(stringResource(R.string.review_which_crop))
            kb.crops.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { crop ->
                        CropChoice(crop, selected = crop.id == state.cropId, onClick = { vm.selectCrop(crop.id) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (state.fields.isNotEmpty()) {
                Eyebrow(stringResource(R.string.review_which_field))
                ChipFlow {
                    state.fields.forEach { f ->
                        PillChoice(f.name, selected = f.id == state.fieldId, onClick = { vm.selectField(f.id) }, icon = R.drawable.ic_farm)
                    }
                    PillChoice(stringResource(R.string.review_no_field), selected = state.fieldId == null, onClick = { vm.selectField(null) })
                }
            }

            val symptoms = kb.symptomsFor(state.cropId)
            if (state.cropId != null && symptoms.isNotEmpty()) {
                Eyebrow(stringResource(R.string.review_signs))
                Text(stringResource(R.string.review_signs_body), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                ChipFlow {
                    symptoms.forEach { s ->
                        PillChoice(s.label, selected = s.id in state.symptoms, onClick = { vm.toggleSymptom(s.id) }, role = Role.Checkbox)
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.cropId == null) {
                Text(stringResource(R.string.review_choose_crop_first), style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            InamaButton(
                stringResource(R.string.review_cta),
                onClick = onAnalyse,
                enabled = state.canAnalyse && !poor,
                icon = R.drawable.ic_sparkle,
            )
        }
    }
}

@Composable
private fun PhotoPreview(photo: File, quality: PhotoQuality) {
    val c = Inama.colors
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp)).background(c.sand)) {
        AsyncImage(model = photo, contentDescription = stringResource(R.string.cd_your_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(
                stringResource(R.string.review_photo_size, (photo.length() / 1024).toInt()),
                background = Color.Black.copy(alpha = 0.55f),
                foreground = Color.White,
                icon = R.drawable.ic_lock,
            )
            if (quality == PhotoQuality.OK) Chip(stringResource(R.string.review_clear), background = c.sprout, foreground = c.ink, icon = R.drawable.ic_check)
        }
    }
}

// =============================================================================== 3 · Analysing

@Composable
fun AnalysingScreen(vm: ScanViewModel, onDone: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = Inama.colors
    StatusBarIcons(light = true)
    LaunchedEffect(Unit) { if (!state.analysing && state.resultId == null && state.analysisError == null) vm.analyse() }
    LaunchedEffect(state.resultId) { state.resultId?.let(onDone) }
    BackHandler {
        vm.cancelAnalysis()
        onBack()
    }

    Column(
        Modifier.fillMaxSize().background(c.forestDeep).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        ScanningPhoto(state.photo, scanning = state.analysing)
        if (state.analysisError != null) {
            Text(stringResource(R.string.analysing_failed_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Text(
                stringResource(if (state.analysisError in PHOTO_REJECTION_CODES) R.string.analysing_failed_image else R.string.analysing_failed_body),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            InamaButton(stringResource(R.string.action_try_again), vm::analyse, kind = ButtonKind.SPROUT, icon = R.drawable.ic_sync)
            InamaButton(stringResource(R.string.analysing_other_photo), onBack, kind = ButtonKind.OUTLINE_LIGHT)
        } else {
            Text(stringResource(R.string.analysing_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val current = state.stage?.ordinal ?: 0
                listOf(
                    AnalysisStage.RECEIVED to R.string.stage_received,
                    AnalysisStage.CHECKING_PHOTO to R.string.stage_checking_photo,
                    AnalysisStage.LOOKING_FOR_SIGNS to R.string.stage_looking,
                    AnalysisStage.MATCHING_GUIDE to R.string.stage_matching,
                ).forEach { (stage, label) ->
                    StageLine(stringResource(label), done = stage.ordinal < current || state.stage == AnalysisStage.DONE, active = stage.ordinal == current && state.stage != AnalysisStage.DONE)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.analysing_note), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
            InamaButton(stringResource(R.string.action_cancel), { vm.cancelAnalysis(); onBack() }, kind = ButtonKind.OUTLINE_LIGHT)
        }
    }
}

@Composable
private fun ScanningPhoto(photo: File?, scanning: Boolean) {
    val c = Inama.colors
    val transition = rememberInfiniteTransition(label = "scan")
    val y by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse), label = "scanY")
    BoxWithConstraints(Modifier.size(220.dp).clip(RoundedCornerShape(28.dp)).background(c.forest).border(2.dp, c.sprout.copy(alpha = 0.6f), RoundedCornerShape(28.dp))) {
        if (photo != null) AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (scanning) {
            Box(
                Modifier.fillMaxWidth().height(36.dp).offset(y = (maxHeight - 36.dp) * y)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, c.sprout.copy(alpha = 0.55f), Color.Transparent))),
            )
        }
    }
}

@Composable
private fun StageLine(text: String, done: Boolean, active: Boolean) {
    val c = Inama.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.heightIn(min = 36.dp)) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Box(Modifier.fillMaxSize().clip(CircleShape).background(c.sprout), contentAlignment = Alignment.Center) { InamaIcon(R.drawable.ic_check, tint = c.ink, size = 18.dp) }
                active -> CircularProgressIndicator(color = c.sprout, strokeWidth = 3.dp, modifier = Modifier.fillMaxSize())
                else -> Box(Modifier.fillMaxSize().clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape))
            }
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (done || active) Color.White else Color.White.copy(alpha = 0.55f))
    }
}

/** Server rejection codes (server/src/v1/handlers) that mean "this file can't be used as a photo". */
private val PHOTO_REJECTION_CODES = setOf("image_type", "image_too_big", "image_required")
