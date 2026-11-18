package com.abdulaziz.netpass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abdulaziz.netpass.R
import com.abdulaziz.netpass.camera.CameraPreview

@Composable
fun CameraScanOverlay(
    torchEnabled: Boolean,
    zoomRatio: Float,
    instantCaptureTrigger: Int,
    radarEnabled: Boolean,
    minLen: Int,
    maxLen: Int,
    onPinDetected: (String) -> Unit,
    onGuidanceNeeded: (String) -> Unit,
    onRadarPulse: () -> Unit,
    onClose: () -> Unit,
    onToggleZoom: () -> Unit,
    onToggleTorch: () -> Unit,
    onInstantCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val closeDesc = stringResource(R.string.cd_close_camera)
    val zoomDesc = if (zoomRatio >= 2.0f) {
        stringResource(R.string.cd_zoom_macro_active)
    } else {
        stringResource(R.string.cd_zoom_normal_active)
    }
    val torchDesc = if (torchEnabled) {
        stringResource(R.string.cd_torch_off)
    } else {
        stringResource(R.string.cd_torch_on)
    }

    Box(modifier = modifier.fillMaxSize()) {
        CameraPreview(
            torchEnabled = torchEnabled,
            zoomRatio = zoomRatio,
            instantCaptureTrigger = instantCaptureTrigger,
            radarEnabled = radarEnabled,
            minLen = minLen,
            maxLen = maxLen,
            onPinDetected = onPinDetected,
            onGuidanceNeeded = onGuidanceNeeded,
            onRadarPulse = onRadarPulse
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .height(115.dp)
                    .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.camera_frame_hint),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .semantics { contentDescription = closeDesc }
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
            }

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(44.dp)
                    .clickable(onClick = onToggleZoom)
                    .semantics {
                        role = Role.Button
                        contentDescription = zoomDesc
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (zoomRatio >= 2.0f) {
                            stringResource(R.string.zoom_label_macro)
                        } else {
                            stringResource(R.string.zoom_label_normal)
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (torchEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                        CircleShape
                    )
                    .semantics { contentDescription = torchDesc }
            ) {
                Icon(
                    if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = onInstantCapture,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.btn_instant_capture),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
