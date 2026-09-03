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

    @OptIn(DelicateCoroutinesApi::class)
    fun handleApiError(code: Int, message: String = "") {
        if (code == 429 || message.contains("Quota") || message.contains("429")) {
            triggerRateLimit(60) // 60 secondes d'attente par défaut pour 429
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
        }
    }
}
