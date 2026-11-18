package com.abdulaziz.netpass.ui

import com.abdulaziz.netpass.model.AppSettings
import com.abdulaziz.netpass.model.AppTab
import com.abdulaziz.netpass.model.VoucherRecord

enum class AppDestination {
    Welcome,
    Main,
    About
}

data class MainUiState(
    val isInitialized: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val history: List<VoucherRecord> = emptyList(),
    val destination: AppDestination = AppDestination.Main,
    val currentTab: AppTab = AppTab.SCAN,
    val pinText: String = "",
    val statusText: String = "",
    val isScanning: Boolean = false,
    val isLoggingIn: Boolean = false,
    val torchEnabled: Boolean = false,
    val currentZoom: Float = 2.2f,
    val instantCaptureCounter: Int = 0,
    val activeWifiGateway: String = "",
    val showPermissionRationaleDialog: Boolean = false
) {
    val canHandleBack: Boolean
        get() = destination == AppDestination.About ||
            isScanning ||
            (destination == AppDestination.Main && currentTab != AppTab.SCAN)
}
