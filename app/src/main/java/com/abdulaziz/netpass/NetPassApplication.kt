package com.abdulaziz.netpass

import android.app.Application
import com.abdulaziz.netpass.di.AppContainer
import com.abdulaziz.netpass.di.DefaultAppContainer

class NetPassApplication : Application() {
    val appContainer: AppContainer by lazy { DefaultAppContainer(this) }
}
