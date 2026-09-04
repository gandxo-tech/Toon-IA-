package com.example

import android.app.Application
import com.example.util.ApiKeyManager

class ToonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiKeyManager.init(this)
    }
}
