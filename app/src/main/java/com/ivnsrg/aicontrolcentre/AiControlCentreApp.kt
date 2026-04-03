package com.ivnsrg.aicontrolcentre

import android.app.Application
import com.ivnsrg.aicontrolcentre.app.di.AppContainer
import com.ivnsrg.aicontrolcentre.app.di.DefaultAppContainer

class AiControlCentreApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}
