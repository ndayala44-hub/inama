package rw.inama.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rw.inama.app.data.remote.ApiException
import rw.inama.app.domain.model.AppLanguage
import rw.inama.app.domain.model.District
import rw.inama.app.domain.model.Farmer
import rw.inama.app.domain.model.OtpChallenge
import rw.inama.app.domain.repository.CatalogRepository
import rw.inama.app.domain.repository.ProfileRepository
import rw.inama.app.domain.repository.SessionRepository
import rw.inama.app.domain.repository.SettingsRepository

/** State for the sign-up flow: language → consent → phone → code → profile. */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val session: SessionRepository,
    private val profile: ProfileRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    data class UiState(
        val language: AppLanguage = AppLanguage.ENGLISH,
        val shareAnonymously: Boolean = true,
        val promoterCanSeeFields: Boolean = true,
        val phoneInput: String = "",
        val phoneError: Boolean = false,
        val sendingCode: Boolean = false,
        val challenge: OtpChallenge? = null,
        val codeInput: String = "",
        val codeError: String? = null,
        val verifying: Boolean = false,
        val name: String = "",
        val districts: List<District> = emptyList(),
        val districtId: String? = null,
        val sector: String = "",
        val keepsCrops: Boolean = true,
        val keepsAnimals: Boolean = false,
    ) {
        /** Local part of a Rwandan mobile number: 9 digits starting with 7. */
        val phoneDigits: String get() = phoneInput.filter { it.isDigit() }.removePrefix("250").removePrefix("0")
        val phoneValid: Boolean get() = Regex("^7[2-9]\\d{7}$").matches(phoneDigits)
        val fullPhone: String get() = "+250$phoneDigits"
        val profileValid: Boolean get() = name.isNotBlank() && districtId != null
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.current()
            val districts = runCatching { catalog.districts() }.getOrDefault(emptyList())
            _state.update { it.copy(language = s.language, shareAnonymously = s.shareAnonymously, promoterCanSeeFields = s.promoterCanSeeFields, districts = districts) }
        }
    }

    fun chooseLanguage(language: AppLanguage) {
        if (!language.availableInThisVersion) return
        _state.update { it.copy(language = language) }
        viewModelScope.launch { settings.update { s -> s.copy(language = language) } }
    }

    fun setConsent(shareAnonymously: Boolean? = null, promoter: Boolean? = null) {
        _state.update { it.copy(shareAnonymously = shareAnonymously ?: it.shareAnonymously, promoterCanSeeFields = promoter ?: it.promoterCanSeeFields) }
    }

    fun saveConsent() {
        val st = _state.value
        viewModelScope.launch { settings.update { it.copy(shareAnonymously = st.shareAnonymously, promoterCanSeeFields = st.promoterCanSeeFields) } }
    }

    fun onPhoneChange(value: String) {
        _state.update { it.copy(phoneInput = value.filter { c -> c.isDigit() || c == ' ' || c == '+' }.take(16), phoneError = false) }
    }

    fun sendCode(onSent: () -> Unit) {
        val st = _state.value
        if (!st.phoneValid) {
            _state.update { it.copy(phoneError = true) }
            return
        }
        _state.update { it.copy(sendingCode = true) }
        viewModelScope.launch {
            try {
                val challenge = session.requestCode(st.fullPhone)
                _state.update { it.copy(challenge = challenge, sendingCode = false, codeInput = "", codeError = null) }
                onSent()
            } catch (e: ApiException) {
                _state.update { it.copy(sendingCode = false, phoneError = true) }
            }
        }
    }

    fun onCodeChange(value: String) {
        _state.update { it.copy(codeInput = value.filter { it.isDigit() }.take(6), codeError = null) }
    }

    fun useDevCode() {
        _state.value.challenge?.devCode?.let { code -> _state.update { it.copy(codeInput = code, codeError = null) } }
    }

    fun verify(onVerified: () -> Unit) {
        val st = _state.value
        if (st.codeInput.length != 6) return
        _state.update { it.copy(verifying = true, codeError = null) }
        viewModelScope.launch {
            try {
                session.verifyCode(st.fullPhone, st.codeInput)
                val existing = profile.current()
                if (existing != null) {
                    _state.update { it.copy(verifying = false, name = existing.name, districtId = existing.districtId, sector = existing.sector) }
                } else {
                    _state.update { it.copy(verifying = false) }
                }
                onVerified()
            } catch (e: ApiException) {
                _state.update { it.copy(verifying = false, codeError = e.code) }
            } catch (e: Exception) {
                _state.update { it.copy(verifying = false, codeError = "network") }
            }
        }
    }

    /** Fills the profile form from the saved farmer (Me › Edit profile reuses the set-up screen). */
    fun loadForEdit() {
        viewModelScope.launch {
            val f = profile.current() ?: return@launch
            _state.update {
                it.copy(
                    phoneInput = f.phone, name = f.name, districtId = f.districtId, sector = f.sector,
                    keepsCrops = f.keepsCrops, keepsAnimals = f.keepsAnimals,
                )
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v.take(60)) }
    fun onDistrict(id: String) = _state.update { it.copy(districtId = id) }
    fun onSector(v: String) = _state.update { it.copy(sector = v.take(60)) }
    fun onKeeps(crops: Boolean? = null, animals: Boolean? = null) =
        _state.update { it.copy(keepsCrops = crops ?: it.keepsCrops, keepsAnimals = animals ?: it.keepsAnimals) }

    fun saveProfile(onSaved: () -> Unit) {
        val st = _state.value
        if (!st.profileValid) return
        viewModelScope.launch {
            val existing = profile.current()
            val updated = if (existing != null) existing.copy(
                phone = if (st.phoneValid) st.fullPhone else existing.phone,
                name = st.name.trim(),
                districtId = st.districtId,
                sector = st.sector.trim(),
                keepsCrops = st.keepsCrops,
                keepsAnimals = st.keepsAnimals,
            ) else Farmer(
                id = "farmer_${System.currentTimeMillis()}",
                phone = st.fullPhone,
                name = st.name.trim(),
                districtId = st.districtId,
                sector = st.sector.trim(),
                keepsCrops = st.keepsCrops,
                keepsAnimals = st.keepsAnimals,
            )
            profile.save(updated)
            onSaved()
        }
    }

    fun finishOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.update { it.copy(onboardingComplete = true) }
            onDone()
        }
    }
}
