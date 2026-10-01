package rw.inama.app.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rw.inama.app.R
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.Field
import rw.inama.app.ui.LocalAppContainer

/** Loads the new farmer and fields for the "Your farm is ready" screen. */
@Composable
fun SetupDoneRoute(onGoHome: () -> Unit) {
    val container = LocalAppContainer.current
    val farmer by container.profileRepository.farmer.collectAsStateWithLifecycle(initialValue = null as Farmer?)
    val fields by container.fieldRepository.fields.collectAsStateWithLifecycle(initialValue = emptyList<Field>())
    val summaries = fields.map { f -> f.cropId to stringResource(R.string.done_field_line, f.name, f.areaAres) }
    SetupDoneScreen(firstName = farmer?.firstName.orEmpty(), fieldSummaries = summaries, onGoHome = onGoHome)
}
