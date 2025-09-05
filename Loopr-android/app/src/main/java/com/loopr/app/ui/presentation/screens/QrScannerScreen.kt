package com.loopr.app.ui.presentation.screens

import android.Manifest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.loopr.app.ui.presentation.modules.bindCameraUseCases
import com.loopr.app.ui.theme.SuccessColor
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

data class AutopayData(
    val solanaAcc: String = "",
    val amount: String = "",
    val label: String = "",
    val frequency: String = "",
    val planId: String = "",
    val userId: String = "",
    val message: String = "",
    val todaysDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(onQrCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    rememberPermissionState(Manifest.permission.CAMERA)

    var autopayData by remember { mutableStateOf(AutopayData()) }
    var isBottomSheetExpanded by remember { mutableStateOf(false) }
    var scannedQrData by remember { mutableStateOf("") }

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    fun parseQrCode(qrCode: String) {
        if (qrCode.startsWith("loopr://setautopay")) {
            try {
                val uri = java.net.URI(qrCode)
                val path = uri.path?.removePrefix("/") ?: ""
                val solanaAcc = path

                val params = uri.query?.split("&")?.associate { param ->
                    val (key, value) = param.split("=", limit = 2)
                    key to URLDecoder.decode(value, "UTF-8")
                } ?: emptyMap()

                // Parse memo JSON for frequency and other data
                val memo = params["memo"]
                var frequency = ""
                var planId = ""
                var userId = ""

                if (memo != null) {
                    try {
                        // Simple JSON parsing for the memo field
                        if (memo.contains("\"frequency\"")) {
                            val freqStart = memo.indexOf("\"frequency\":\"") + 13
                            val freqEnd = memo.indexOf("\"", freqStart)
                            if (freqEnd > freqStart) {
                                frequency = memo.substring(freqStart, freqEnd)
                            }
                        }
                        if (memo.contains("\"planId\"")) {
                            val planStart = memo.indexOf("\"planId\":\"") + 10
                            val planEnd = memo.indexOf("\"", planStart)
                            if (planEnd > planStart) {
                                planId = memo.substring(planStart, planEnd)
                            }
                        }
                        if (memo.contains("\"userId\"")) {
                            val userStart = memo.indexOf("\"userId\":\"") + 10
                            val userEnd = memo.indexOf("\"", userStart)
                            if (userEnd > userStart) {
                                userId = memo.substring(userStart, userEnd)
                            }
                        }
                    } catch (e: Exception) {
                        // Handle JSON parsing errors
                    }
                }

                autopayData = AutopayData(
                    solanaAcc = solanaAcc,
                    amount = params["amount"] ?: "",
                    label = params["label"] ?: "",
                    message = params["message"] ?: "",
                    frequency = frequency,
                    planId = planId,
                    userId = userId
                )

                isBottomSheetExpanded = true
                scannedQrData = qrCode
            } catch (e: Exception) {
                // Handle parsing errors
            }
        }
        onQrCodeScanned(qrCode)
    }

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(factory = { ctx ->
            PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }, modifier = Modifier.fillMaxSize(), update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(
                    cameraProvider, lifecycleOwner, previewView, ::parseQrCode
                )
            }, ContextCompat.getMainExecutor(context))
        })

        // Canvas drawing for QR scanner box
        Canvas(modifier = Modifier.fillMaxSize()) {
            val borderWidth = 4.dp.toPx()
            val cornerLength = 40.dp.toPx()
            val boxSizeRatio = 0.7f
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxWidth = canvasWidth * boxSizeRatio
            val boxHeight = canvasWidth * boxSizeRatio
            val offsetX = (canvasWidth - boxWidth) / 2
            val offsetY = (canvasHeight - boxHeight) / 2

            val path = Path()

            // Drawing corners for the scanning box
            path.moveTo(offsetX, offsetY + cornerLength)
            path.lineTo(offsetX, offsetY)
            path.lineTo(offsetX + cornerLength, offsetY)
            path.moveTo(offsetX + boxWidth - cornerLength, offsetY)
            path.lineTo(offsetX + boxWidth, offsetY)
            path.lineTo(offsetX + boxWidth, offsetY + cornerLength)
            path.moveTo(offsetX + boxWidth, offsetY + boxHeight - cornerLength)
            path.lineTo(offsetX + boxWidth, offsetY + boxHeight)
            path.lineTo(offsetX + boxWidth - cornerLength, offsetY + boxHeight)
            path.moveTo(offsetX + cornerLength, offsetY + boxHeight)
            path.lineTo(offsetX, offsetY + boxHeight)
            path.lineTo(offsetX, offsetY + boxHeight - cornerLength)

            drawPath(path, color = Color(0xFFffcc90), style = Stroke(width = borderWidth))

            // Overlay outside scanning box (dimmed background)
            drawRect(
                color = Color.Black.copy(alpha = 0.5f), size = Size(canvasWidth, canvasHeight)
            )

            // Transparent area in the middle (clear scanning area)
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(offsetX, offsetY),
                size = Size(boxWidth, boxHeight)
            )
        }

        // Bottom Sheet - Collapsed by default
        if (!isBottomSheetExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan to set Autopay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Powered by Loopr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Expanded Bottom Sheet Modal
        if (isBottomSheetExpanded) {
            ModalBottomSheet(
                onDismissRequest = {
                    isBottomSheetExpanded = false
                    autopayData = AutopayData()
                },
                sheetState = bottomSheetState,
                modifier = Modifier.fillMaxHeight(),
                dragHandle = null
            ) {
                AutopaySetupContent(
                    autopayData = autopayData,
                    onDataChanged = { autopayData = it },
                    onDismiss = {
                        isBottomSheetExpanded = false
                        autopayData = AutopayData()
                    },
                    onSetAutopay = {
                        // Handle autopay setup
                        isBottomSheetExpanded = false
                        autopayData = AutopayData()
                    }
                )
            }
        }
    }
}

@Composable
fun AutopaySetupContent(
    autopayData: AutopayData,
    onDataChanged: (AutopayData) -> Unit,
    onDismiss: () -> Unit,
    onSetAutopay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Top bar with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Title and subtitle
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scan to set Autopay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Powered by Loopr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        // Horizontal divider
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 24.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )

        // Form fields
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Amount field
            OutlinedTextField(
                value = autopayData.amount,
                onValueChange = {
                    if (autopayData.amount.isEmpty()) {
                        onDataChanged(autopayData.copy(amount = it))
                    }
                },
                label = { Text("Amount") },
                enabled = autopayData.amount.isEmpty(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Label field
            OutlinedTextField(
                value = autopayData.label,
                onValueChange = {
                    if (autopayData.label.isEmpty()) {
                        onDataChanged(autopayData.copy(label = it))
                    }
                },
                label = { Text("Label") },
                enabled = autopayData.label.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            // Frequency field
            OutlinedTextField(
                value = autopayData.frequency,
                onValueChange = {
                    if (autopayData.frequency.isEmpty()) {
                        onDataChanged(autopayData.copy(frequency = it))
                    }
                },
                label = { Text("Frequency") },
                enabled = autopayData.frequency.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            // Plan ID field
            OutlinedTextField(
                value = autopayData.planId,
                onValueChange = {
                    if (autopayData.planId.isEmpty()) {
                        onDataChanged(autopayData.copy(planId = it))
                    }
                },
                label = { Text("Plan ID") },
                enabled = autopayData.planId.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            // User ID field
            OutlinedTextField(
                value = autopayData.userId,
                onValueChange = {
                    if (autopayData.userId.isEmpty()) {
                        onDataChanged(autopayData.copy(userId = it))
                    }
                },
                label = { Text("User ID") },
                enabled = autopayData.userId.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            // Today's date field (read-only)
            OutlinedTextField(
                value = autopayData.todaysDate,
                onValueChange = { },
                label = { Text("Date") },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Set Autopay button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Button(
                onClick = onSetAutopay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessColor,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Set Autopay",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
