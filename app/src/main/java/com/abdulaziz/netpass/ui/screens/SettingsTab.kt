package com.abdulaziz.netpass.ui.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulaziz.netpass.R
import com.abdulaziz.netpass.domain.GatewayHostValidator
import com.abdulaziz.netpass.domain.VoucherValidator
import com.abdulaziz.netpass.feedback.FeedbackController
import com.abdulaziz.netpass.model.AppSettings
import com.abdulaziz.netpass.ui.components.SensorTestbed
import com.abdulaziz.netpass.ui.components.SettingsSwitchRow

private const val TAG = "SettingsTab"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onSelectTtsEngine: (packageName: String, label: String) -> Unit,
    activeWifiGateway: String,
    feedback: FeedbackController,
    onOpenAbout: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedEngines = remember { feedback.getInstalledEngines() }
    var engineDropdownExpanded by remember { mutableStateOf(false) }

    var minLenInput by remember { mutableStateOf(settings.minLen.toString()) }
    var maxLenInput by remember { mutableStateOf(settings.maxLen.toString()) }
    var gatewayInput by remember { mutableStateOf(settings.gatewayIp) }

    LaunchedEffect(settings.minLen, settings.maxLen) {
        if (minLenInput.toIntOrNull() != settings.minLen) {
            minLenInput = settings.minLen.toString()
        }
        if (maxLenInput.toIntOrNull() != settings.maxLen) {
            maxLenInput = settings.maxLen.toString()
        }
    }

    LaunchedEffect(settings.gatewayIp) {
        if (gatewayInput != settings.gatewayIp) {
            gatewayInput = settings.gatewayIp
        }
    }

    val parsedMin = minLenInput.toIntOrNull()
    val parsedMax = maxLenInput.toIntOrNull()
    val isLengthRangeError = parsedMin == null ||
        parsedMax == null ||
        parsedMin !in VoucherValidator.MIN_ALLOWED_LENGTH..VoucherValidator.MAX_ALLOWED_LENGTH ||
        parsedMax !in VoucherValidator.MIN_ALLOWED_LENGTH..VoucherValidator.MAX_ALLOWED_LENGTH ||
        parsedMin > parsedMax

    val isGatewayError = gatewayInput.isNotBlank() &&
        GatewayHostValidator.sanitizeAndValidate(gatewayInput) == null

    val defaultEngineLabel = stringResource(R.string.tts_engine_system_default)
    val ttsSampleText = stringResource(R.string.tts_test_sample)
    val onboardingCardDesc = stringResource(R.string.card_onboarding_cd)
    val aboutCardDesc = stringResource(R.string.card_about_cd)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_audio),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_voice_title),
                        subtitle = stringResource(R.string.setting_voice_subtitle),
                        checked = settings.voiceAnnounce,
                        onToggle = { onSettingsChange(settings.copy(voiceAnnounce = !settings.voiceAnnounce)) }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.setting_tts_engine_label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        ExposedDropdownMenuBox(
                            expanded = engineDropdownExpanded,
                            onExpandedChange = { engineDropdownExpanded = it }
                        ) {
                            val currentEngineLabel = installedEngines
                                .find { it.packageName == settings.selectedTtsEngine }
                                ?.label
                                ?: if (settings.selectedTtsEngine.isEmpty()) defaultEngineLabel else settings.selectedTtsEngine

                            OutlinedTextField(
                                value = currentEngineLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = engineDropdownExpanded,
                                onDismissRequest = { engineDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(defaultEngineLabel, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        engineDropdownExpanded = false
                                        onSelectTtsEngine("", defaultEngineLabel)
                                    }
                                )
                                installedEngines.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(engine.label) },
                                        onClick = {
                                            engineDropdownExpanded = false
                                            onSelectTtsEngine(engine.packageName, engine.label)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                            } catch (e: Exception) {
                                Log.w(TAG, "Direct TTS settings intent unavailable; falling back to system settings", e)
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                } catch (fallbackError: Exception) {
                                    Log.w(TAG, "Unable to open system settings", fallbackError)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SettingsVoice, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_open_android_tts_settings), fontSize = 12.sp)
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_radar_title),
                        subtitle = stringResource(R.string.setting_radar_subtitle),
                        checked = settings.radarSound,
                        onToggle = { onSettingsChange(settings.copy(radarSound = !settings.radarSound)) }
                    )

                    Button(
                        onClick = {
                            feedback.speak(ttsSampleText, true, settings.selectedVoiceName)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_test_voice))
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_mikrotik),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    if (activeWifiGateway.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.label_connected_wifi_gateway))
                                Text(
                                    text = activeWifiGateway,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    SettingsSwitchRow(
                        title = stringResource(R.string.setting_auto_login_title),
                        subtitle = stringResource(R.string.setting_auto_login_subtitle),
                        checked = settings.autoLoginOnScan,
                        onToggle = { onSettingsChange(settings.copy(autoLoginOnScan = !settings.autoLoginOnScan)) }
                    )

                    OutlinedTextField(
                        value = gatewayInput,
                        onValueChange = { raw ->
                            gatewayInput = raw
                            if (raw.isBlank() || GatewayHostValidator.sanitizeAndValidate(raw) != null) {
                                onSettingsChange(settings.copy(gatewayIp = raw.trim()))
                            }
                        },
                        label = { Text(stringResource(R.string.label_custom_gateway)) },
                        isError = isGatewayError,
                        supportingText = if (isGatewayError) {
                            { Text(stringResource(R.string.error_invalid_gateway)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_pin_length),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = minLenInput,
                            onValueChange = { raw ->
                                val filtered = VoucherValidator.normalizeToAsciiDigits(raw).take(2)
                                minLenInput = filtered
                                val minVal = filtered.toIntOrNull()
                                val maxVal = maxLenInput.toIntOrNull()
                                if (minVal != null &&
                                    maxVal != null &&
                                    minVal in VoucherValidator.MIN_ALLOWED_LENGTH..VoucherValidator.MAX_ALLOWED_LENGTH &&
                                    minVal <= maxVal
                                ) {
                                    onSettingsChange(settings.copy(minLen = minVal, maxLen = maxVal))
                                }
                            },
                            label = { Text(stringResource(R.string.label_min_length)) },
                            isError = isLengthRangeError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = maxLenInput,
                            onValueChange = { raw ->
                                val filtered = VoucherValidator.normalizeToAsciiDigits(raw).take(2)
                                maxLenInput = filtered
                                val minVal = minLenInput.toIntOrNull()
                                val maxVal = filtered.toIntOrNull()
                                if (minVal != null &&
                                    maxVal != null &&
                                    maxVal in VoucherValidator.MIN_ALLOWED_LENGTH..VoucherValidator.MAX_ALLOWED_LENGTH &&
                                    minVal <= maxVal
                                ) {
                                    onSettingsChange(settings.copy(minLen = minVal, maxLen = maxVal))
                                }
                            },
                            label = { Text(stringResource(R.string.label_max_length)) },
                            isError = isLengthRangeError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (isLengthRangeError) {
                        Text(
                            text = stringResource(R.string.error_pin_length_range),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_section_hardware),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                    SensorTestbed(
                        feedback = feedback,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onReplayOnboarding)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = onboardingCardDesc
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_onboarding_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.card_onboarding_subtitle),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAbout)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = aboutCardDesc
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.card_about_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.card_about_subtitle),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onResetDefaults,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .minimumInteractiveComponentSize(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_reset_defaults))
            }
        }
    }
}
