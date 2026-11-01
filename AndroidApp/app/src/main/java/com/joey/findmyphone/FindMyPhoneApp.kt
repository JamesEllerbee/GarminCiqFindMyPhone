package com.joey.findmyphone

import android.app.Application

class FindMyPhoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorReporter.install(this)
    }
}
