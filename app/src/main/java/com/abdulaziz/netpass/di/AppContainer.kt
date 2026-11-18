package com.abdulaziz.netpass.di

import android.content.Context
import com.abdulaziz.netpass.data.local.NetPassPreferencesRepository
import com.abdulaziz.netpass.data.local.PreferencesRepository
import com.abdulaziz.netpass.feedback.FeedbackController
import com.abdulaziz.netpass.feedback.FeedbackManager
import com.abdulaziz.netpass.network.HotspotAuthClient
import com.abdulaziz.netpass.network.MikrotikGatewayClient

interface AppContainer {
    val preferencesRepository: PreferencesRepository
    val authClient: HotspotAuthClient
    fun createFeedbackController(initialEngine: String = ""): FeedbackController
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    override val preferencesRepository: PreferencesRepository by lazy {
        NetPassPreferencesRepository.create(appContext)
    }

    override val authClient: HotspotAuthClient by lazy {
        MikrotikGatewayClient(appContext)
    }

    override fun createFeedbackController(initialEngine: String): FeedbackController {
        return FeedbackManager(appContext, initialEngine)
    }
}
