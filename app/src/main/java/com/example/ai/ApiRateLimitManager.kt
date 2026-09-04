package com.example.ai

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object ApiRateLimitManager {
    private val _rateLimitCountdown = MutableStateFlow(0)
    val rateLimitCountdown: StateFlow<Int> = _rateLimitCountdown.asStateFlow()
    
    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    @OptIn(DelicateCoroutinesApi::class)
    fun handleApiError(code: Int, message: String = "") {
        if (code == 429 || message.contains("Quota") || message.contains("429") || message.contains("limit")) {
            _lastErrorMessage.value = message
            // Tenter d'extraire le temps d'attente suggéré (ex: "Please retry in 23.46s")
            val regex = Regex("retry in ([\\d.]+)")
            val match = regex.find(message)
            val suggestedSeconds = match?.groupValues?.get(1)?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toInt() }
            
            val waitSeconds = if (suggestedSeconds != null) {
                suggestedSeconds + 2 // Marge de sécurité
            } else {
                65 // Défaut
            }
            
            triggerRateLimit(waitSeconds)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun triggerRateLimit(seconds: Int = 60) {
        if (_rateLimitCountdown.value > 0) return // Déjà en cours
        _rateLimitCountdown.value = seconds
        GlobalScope.launch {
            while (_rateLimitCountdown.value > 0) {
                delay(1000)
                _rateLimitCountdown.update { it - 1 }
            }
            _lastErrorMessage.value = null
        }
    }
}
