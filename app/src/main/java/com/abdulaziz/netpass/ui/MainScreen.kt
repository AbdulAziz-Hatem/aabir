package com.abdulaziz.netpass.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.abdulaziz.netpass.R
import com.abdulaziz.netpass.model.AppTab
import com.abdulaziz.netpass.ui.components.CameraScanOverlay
import com.abdulaziz.netpass.ui.screens.AboutScreen
import com.abdulaziz.netpass.ui.screens.HistoryTab
import com.abdulaziz.netpass.ui.screens.ScanTab
import com.abdulaziz.netpass.ui.screens.SettingsTab

private const val TAG = "MainScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onCameraPermissionResult(isGranted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.stopScanning()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = uiState.canHandleBack) {
        viewModel.handleBackPress()
    }

    if (uiState.destination == AppDestination.About) {
        AboutScreen(onBack = viewModel::closeAbout)
        return
    }

    val themeToggleDescription = if (uiState.settings.isDarkTheme) {
        stringResource(R.string.cd_switch_to_light_theme)
    } else {
        stringResource(R.string.cd_switch_to_dark_theme)
    }

    Scaffold(
        topBar = {
            if (!uiState.isScanning) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Text(
                                text = stringResource(R.string.app_tagline),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = viewModel::toggleDarkTheme,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = themeToggleDescription }
                        ) {
                            Icon(
                                imageVector = if (uiState.settings.isDarkTheme) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!uiState.isScanning) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        val label = when (tab) {
                            AppTab.SCAN -> stringResource(R.string.tab_scan)
                            AppTab.HISTORY -> stringResource(R.string.tab_history)
                            AppTab.SETTINGS -> stringResource(R.string.tab_settings)
                        }
                        NavigationBarItem(
                            selected = uiState.currentTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                when (tab) {
                                    AppTab.SCAN -> Icon(Icons.Default.CameraAlt, contentDescription = null)
                                    AppTab.HISTORY -> Icon(Icons.Default.History, contentDescription = null)
                                    AppTab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            },
                            label = { Text(label, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (uiState.isScanning) PaddingValues(0.dp) else paddingValues)
        ) {
            if (uiState.isScanning) {
                CameraScanOverlay(
                    torchEnabled = uiState.torchEnabled,
                    zoomRatio = uiState.currentZoom,
                    instantCaptureTrigger = uiState.instantCaptureCounter,
                    radarEnabled = uiState.settings.radarSound,
                    minLen = uiState.settings.minLen,
                    maxLen = uiState.settings.maxLen,
                    onPinDetected = viewModel::onPinDetected,
                    onGuidanceNeeded = viewModel::onGuidanceNeeded,
                    onRadarPulse = viewModel::onRadarPulse,
                    onClose = viewModel::stopScanning,
                    onToggleZoom = viewModel::toggleZoom,
                    onToggleTorch = viewModel::toggleTorch,
                    onInstantCapture = viewModel::requestInstantCapture
                )
            } else {
                when (uiState.currentTab) {
                    AppTab.SCAN -> ScanTab(
                        pinText = uiState.pinText,
                        onPinChange = viewModel::onPinChange,
                        statusText = uiState.statusText,
                        isLoggingIn = uiState.isLoggingIn,
                        onStartScan = {
                            val isGranted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (isGranted) {
                                viewModel.startScanning()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onExecuteLogin = { pin -> viewModel.executeLogin(pin) },
                        onCopySuccess = viewModel::onCopySuccess
                    )

                    AppTab.HISTORY -> HistoryTab(
                        historyList = uiState.history,
                        onClearHistory = viewModel::clearHistory,
                        onReLogin = viewModel::reLoginFromHistory,
                        onCopySuccess = viewModel::onCopySuccess
                    )

                    AppTab.SETTINGS -> SettingsTab(
                        settings = uiState.settings,
                        onSettingsChange = { updated -> viewModel.updateSettings { updated } },
                        onSelectTtsEngine = viewModel::selectTtsEngine,
                        activeWifiGateway = uiState.activeWifiGateway,
                        feedback = viewModel.feedback,
                        onOpenAbout = viewModel::openAbout,
                        onReplayOnboarding = viewModel::openOnboarding,
                        onResetDefaults = viewModel::resetDefaults
                    )
                }
            }
        }

        if (uiState.showPermissionRationaleDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissPermissionRationale,
                title = {
                    Text(
                        text = stringResource(R.string.dialog_camera_permission_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.dialog_camera_permission_body),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissPermissionRationale()
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.w(TAG, "Unable to open application settings", e)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.btn_open_phone_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissPermissionRationale) {
                        Text(stringResource(R.string.btn_manual_entry))
                    }
                }
            )
        }
    }
}
