package com.example.ai

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
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
    
    private var backoffFactor = 1

    @OptIn(DelicateCoroutinesApi::class)
    fun handleApiError(code: Int, message: String = "") {
        if (code == 429 || message.contains("Quota") || message.contains("429") || message.contains("limit")) {
            _lastErrorMessage.value = message
            
            // Backoff exponentiel : 65, 130, 260, 520, 1040s
            val waitSeconds = (65 * backoffFactor).coerceAtMost(1040)
            
            triggerRateLimit(waitSeconds)
            
            // Augmenter le backoff pour la prochaine fois
            backoffFactor = (backoffFactor * 2).coerceAtMost(16)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun triggerRateLimit(seconds: Int) {
        if (_rateLimitCountdown.value > 0) return // Déjà en cours
        _rateLimitCountdown.value = seconds
        GlobalScope.launch(Dispatchers.IO) {
            while (_rateLimitCountdown.value > 0) {
                delay(1000)
                _rateLimitCountdown.update { it - 1 }
            }
            _lastErrorMessage.value = null
            // Le backoff ne se reset que si l'utilisateur attend la fin complète sans réessayer prématurément
        }
    }
    
    fun resetBackoff() {
        backoffFactor = 1
    }
}
