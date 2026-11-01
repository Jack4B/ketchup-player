package pl.ketchupplayer.tv.feature.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import pl.ketchupplayer.tv.core.designsystem.KetchupButton
import pl.ketchupplayer.tv.core.designsystem.KetchupLogoWide
import pl.ketchupplayer.tv.core.designsystem.KetchupTheme
import pl.ketchupplayer.tv.core.designsystem.ThemedBackground
import pl.ketchupplayer.tv.core.designsystem.requestFocusWhenPlaced

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(state, viewModel::onEvent, onFinished)
}

/**
 * Kreator pierwszego uruchomienia (makieta v3 01): 1 Witaj, 2 Dodaj listę (formularz jak w Ustawieniach),
 * 3 Gotowe. „Pomiń” kończy kreator w każdym kroku. Wszystko pilotem: strzałki, OK, BACK (wstecz o krok).
 */
@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    state: ProvidersUiState,
    onEvent: (ProvidersEvent) -> Unit,
    onFinished: () -> Unit,
) {
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Wysłanie formularza liczy się zawsze: przyciskiem „Dalej” i ✓ na klawiaturze ekranowej.
    val send: (ProvidersEvent) -> Unit = { event ->
        if (event is ProvidersEvent.Submit) submitted = true
        onEvent(event)
    }
    Onboarding(state, send, submitted, { submitted = false }, onFinished)
}

@Composable
private fun Onboarding(
    state: ProvidersUiState,
    onEvent: (ProvidersEvent) -> Unit,
    submitted: Boolean,
    onHandled: () -> Unit,
    onFinished: () -> Unit,
) {
    val c = KetchupTheme.colors
    var step by rememberSaveable { mutableIntStateOf(1) }
    androidx.activity.compose.BackHandler(enabled = step > 1) { step-- }
    if (step == 2 && state.form == null && !submitted) LaunchedEffect(Unit) { onEvent(ProvidersEvent.StartAdd) }
    // Po wysłaniu formularza: sukces (jest dostawca, brak błędu) -> krok 3; błąd -> formularz wraca z komunikatem.
    LaunchedEffect(submitted, state.busy, state.form, state.providers.size, state.message) {
        if (submitted && !state.busy && state.form == null) {
            onHandled()
            if (state.providers.isNotEmpty() && state.message == null) step = 3 else onEvent(ProvidersEvent.RetryAdd)
        }
    }
    ThemedBackground {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(Modifier.width(360.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                KetchupLogoWide(Modifier.fillMaxWidth().height(260.dp).padding(bottom = 16.dp))
                listOf(R.string.onb_step1, R.string.onb_step2, R.string.onb_step3).forEachIndexed { i, label ->
                    Text(
                        stringResource(label),
                        fontSize = 16.sp,
                        fontWeight = if (step == i + 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (step == i + 1) c.textPrimary else c.textSecondary,
                    )
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.onb_step_of, step),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textSecondary,
                )
                when (step) {
                    1 -> Welcome(onNext = { step = 2 }, onSkip = onFinished)
                    2 ->
                        AddStep(
                            state,
                            onEvent,
                            onNext = { onEvent(ProvidersEvent.Submit) },
                            onBack = { step = 1 },
                            onSkip = onFinished,
                        )
                    else -> Done(state, onFinished)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Welcome(
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = KetchupTheme.colors
    Text(
        stringResource(R.string.onb_welcome_title),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        color = c.textPrimary,
    )
    Text(stringResource(R.string.onb_welcome_text), fontSize = 16.sp, color = c.textPrimary)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        KetchupButton(stringResource(R.string.onb_next), onNext, Modifier.requestFocusWhenPlaced(), primary = true)
        KetchupButton(stringResource(R.string.onb_skip), onSkip)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddStep(
    state: ProvidersUiState,
    onEvent: (ProvidersEvent) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = KetchupTheme.colors
    Text(
        stringResource(R.string.onb_add_title),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        color = c.textPrimary,
    )
    Text(stringResource(R.string.onb_add_text), fontSize = 16.sp, color = c.textPrimary)
    state.message?.let {
        Text(stringResource(R.string.onb_error, it), fontSize = 16.sp, color = ERROR_COLOR)
    }
    val form = state.form
    // Postęp nad formularzem: klawiatura ekranowa zasłania dół ekranu.
    if (state.busy) ImportProgress(state.status, state.downloadedBytes, fontSizeSp = 18)
    if (form != null) AddForm(form, state.busy, onEvent, showActions = false)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        KetchupButton(stringResource(R.string.onb_back), onBack)
        KetchupButton(stringResource(R.string.onb_next), { if (!state.busy) onNext() }, primary = true)
        KetchupButton(stringResource(R.string.onb_skip), onSkip)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Done(
    state: ProvidersUiState,
    onFinished: () -> Unit,
) {
    val c = KetchupTheme.colors
    Text(
        stringResource(R.string.onb_done_title),
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        color = c.textPrimary,
    )
    Text(stringResource(R.string.onb_done_text), fontSize = 16.sp, color = c.textPrimary)
    state.providers.firstOrNull()?.let {
        Text(stringResource(R.string.providers_channels, it.channelCount), fontSize = 16.sp, color = c.textSecondary)
    }
    KetchupButton(
        stringResource(R.string.onb_start),
        onFinished,
        Modifier.padding(top = 16.dp).requestFocusWhenPlaced(),
        primary = true,
    )
}
