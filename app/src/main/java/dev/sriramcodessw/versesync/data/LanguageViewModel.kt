package dev.sriramcodessw.versesync.data

import android.annotation.SuppressLint
import android.app.Application
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class LanguageModelInfo(
    val name: String,
    val code: String,
    val url: String,
    val regions: List<String> = emptyList()
)

class LanguageViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = LanguagePreferences(application)
    val downloader = ModelDownloader(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var locationCancellationTokenSource = CancellationTokenSource()

    val isSetupComplete: StateFlow<Boolean> = prefs.isSetupComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val installedLanguages: StateFlow<Set<String>> = prefs.installedLanguages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadStatus = downloader.downloadStatus

    private val _detectedCountry = MutableStateFlow<String?>(null)
    val detectedCountry = _detectedCountry.asStateFlow()

    val availableLanguages = listOf(
        LanguageModelInfo("English", "en-us", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", listOf("US", "GB", "CA", "AU", "IN")),
        LanguageModelInfo("Hindi", "hi", "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", listOf("IN")),
        LanguageModelInfo("Sanskrit (Beta)", "sa", "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip", listOf("IN")),
        LanguageModelInfo("Tamil", "ta", "https://alphacephei.com/vosk/models/vosk-model-small-ta-0.42.zip", listOf("IN", "LK", "SG")),
        LanguageModelInfo("Telugu", "te", "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip", listOf("IN")),
        LanguageModelInfo("Marathi", "mr", "https://alphacephei.com/vosk/models/vosk-model-small-mr-0.22.zip", listOf("IN")),
        LanguageModelInfo("Bengali", "bn", "https://alphacephei.com/vosk/models/vosk-model-small-bn-0.22.zip", listOf("IN", "BD")),
        LanguageModelInfo("Kannada", "kn", "https://alphacephei.com/vosk/models/vosk-model-small-kn-0.42.zip", listOf("IN")),
        LanguageModelInfo("Malayalam", "ml", "https://alphacephei.com/vosk/models/vosk-model-small-ml-0.22.zip", listOf("IN")),
        LanguageModelInfo("Gujarati", "gu", "https://alphacephei.com/vosk/models/vosk-model-small-gu-0.42.zip", listOf("IN")),
        LanguageModelInfo("Punjabi", "pa", "https://alphacephei.com/vosk/models/vosk-model-small-pa-0.22.zip", listOf("IN", "PK")),
        LanguageModelInfo("Arabic", "ar", "https://alphacephei.com/vosk/models/vosk-model-small-ar-0.3.zip", listOf("SA", "EG", "AE", "JO")),
        LanguageModelInfo("Korean", "ko", "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip", listOf("KR")),
        LanguageModelInfo("French", "fr", "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip", listOf("FR", "CA", "BE")),
        LanguageModelInfo("Spanish", "es", "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip", listOf("ES", "MX", "AR")),
        LanguageModelInfo("German", "de", "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip", listOf("DE", "AT", "CH")),
        LanguageModelInfo("Russian", "ru", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", listOf("RU", "BY", "KZ")),
        LanguageModelInfo("Chinese", "cn", "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip", listOf("CN", "TW", "SG")),
        LanguageModelInfo("Japanese", "ja", "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip", listOf("JP")),
        LanguageModelInfo("Portuguese", "pt", "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip", listOf("PT", "BR")),
        LanguageModelInfo("Turkish", "tr", "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip", listOf("TR")),
        LanguageModelInfo("Vietnamese", "vn", "https://alphacephei.com/vosk/models/vosk-model-small-vn-0.4.zip", listOf("VN")),
        LanguageModelInfo("Italian", "it", "https://alphacephei.com/vosk/models/vosk-model-small-it-0.4.zip", listOf("IT")),
        LanguageModelInfo("Dutch", "nl", "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip", listOf("NL", "BE")),
        LanguageModelInfo("Greek", "el", "https://alphacephei.com/vosk/models/vosk-model-small-el-0.3.zip", listOf("GR")),
        LanguageModelInfo("Polish", "pl", "https://alphacephei.com/vosk/models/vosk-model-small-pl-0.22.zip", listOf("PL")),
        LanguageModelInfo("Swedish", "sv", "https://alphacephei.com/vosk/models/vosk-model-small-sv-0.15.zip", listOf("SE")),
        LanguageModelInfo("Ukrainian", "uk", "https://alphacephei.com/vosk/models/vosk-model-small-uk-0.22.zip", listOf("UA")),
        LanguageModelInfo("Farsi", "fa", "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.4.zip", listOf("IR"))
    )

    val suggestedLanguages: StateFlow<List<LanguageModelInfo>> = combine(_detectedCountry, MutableStateFlow(availableLanguages)) { country, all ->
        if (country != null) {
            all.filter { it.regions.contains(country) }
        } else {
            all.filter { listOf("en-us", "es", "hi", "fr").contains(it.code) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            downloadStatus.collect { statusMap ->
                statusMap.forEach { (lang, progress) ->
                    if (progress == 100 && !installedLanguages.value.contains(lang)) {
                        onUnpackSuccess(lang)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun detectRegion() {
        locationCancellationTokenSource.cancel()
        locationCancellationTokenSource = CancellationTokenSource()
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, locationCancellationTokenSource.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(getApplication(), Locale.getDefault())
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                                if (addresses.isNotEmpty()) {
                                    _detectedCountry.value = addresses[0].countryCode
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                _detectedCountry.value = addresses[0].countryCode
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("LanguageViewModel", "Geocoding failed", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("LanguageViewModel", "Location request failed", e)
            }
    }

    fun downloadLanguage(lang: LanguageModelInfo) {
        viewModelScope.launch {
            downloader.downloadModel(lang.code, lang.url)
        }
    }

    fun markSetupComplete() {
        viewModelScope.launch {
            prefs.setSetupComplete(true)
        }
    }

    private fun onUnpackSuccess(languageCode: String) {
        viewModelScope.launch {
            prefs.addLanguage(languageCode)
        }
    }

    fun removeLanguage(languageCode: String) {
        viewModelScope.launch {
            prefs.removeLanguage(languageCode)
            val modelDir = java.io.File(getApplication<Application>().filesDir, "models/model-$languageCode")
            if (modelDir.exists()) modelDir.deleteRecursively()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationCancellationTokenSource.cancel()
    }
}

class LanguageViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LanguageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LanguageViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
