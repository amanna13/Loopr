package com.loopr.app.ui.presentation.screens

import android.Manifest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.loopr.app.ui.presentation.modules.bindCameraUseCases
import com.loopr.app.ui.theme.LooprCyan
import com.loopr.app.ui.theme.SuccessColor
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    rememberPermissionState(Manifest.permission.CAMERA)

    var autopayData by remember { mutableStateOf(AutopayData()) }
    var scannedQrData by remember { mutableStateOf("") }
    var isBottomSheetExpanded by remember { mutableStateOf(false) }

    // Bottom sheet configuration - non-draggable
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = SheetState(
            skipPartiallyExpanded = true, // This prevents the partially expanded state
            initialValue = SheetValue.Hidden, // Start with hidden/collapsed
            density = density,
            skipHiddenState = false
        )
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
                    planId = planId
                )

                // Expand the bottom sheet when valid QR is scanned
                isBottomSheetExpanded = true
                coroutineScope.launch {
                    bottomSheetScaffoldState.bottomSheetState.expand()
                }
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

        // Collapsed Bottom Sheet (always visible at bottom)
        if (!isBottomSheetExpanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
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

        // Expanded Bottom Sheet (full screen modal)
        if (isBottomSheetExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AutopaySetupContent(
                    autopayData = autopayData,
                    onDataChanged = { autopayData = it },
                    onDismiss = {
                        isBottomSheetExpanded = false
                        autopayData = AutopayData()
                        coroutineScope.launch {
                            bottomSheetScaffoldState.bottomSheetState.hide()
                        }
                    },
                    onSetAutopay = {
                        // Handle autopay setup
                        isBottomSheetExpanded = false
                        autopayData = AutopayData()
                        coroutineScope.launch {
                            bottomSheetScaffoldState.bottomSheetState.hide()
                        }
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
            modifier = Modifier.padding(bottom = 32.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )

        // Centered Amount Field with clean design
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Clean amount input field with proper cursor handling
            var amountInput by remember { mutableStateOf("") }

            LaunchedEffect(autopayData.amount) {
                if (autopayData.amount.isNotEmpty() && amountInput.isEmpty()) {
                    amountInput = autopayData.amount
                }
            }

            BasicTextField(
                value = amountInput,
                onValueChange = { value ->
                    if (autopayData.amount.isEmpty()) {
                        // Remove any non-numeric characters except decimal point
                        val cleanValue = value.filter { it.isDigit() || it == '.' }
                        amountInput = cleanValue
                        onDataChanged(autopayData.copy(amount = cleanValue))
                    }
                },
                enabled = autopayData.amount.isEmpty(),
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (autopayData.amount.isEmpty())
                        MaterialTheme.colorScheme.onSurface
                    else
                        LooprCyan
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "SOL ",
                            style = MaterialTheme.typography.displaySmall.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (amountInput.isEmpty())
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else if (autopayData.amount.isEmpty())
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    LooprCyan
                            )
                        )
                        if (amountInput.isEmpty() && autopayData.amount.isEmpty()) {
                            Text(
                                text = "0.00",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        } else {
                            innerTextField()
                        }
                    }
                }
            }
        }

        // Stylized Details Card with gradient background similar to home screen
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                LooprCyan.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Card Header with Loopr branding
                Column {
                    Text(
                        text = "Subscription Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Review your autopay configuration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                // Modern input fields
                ModernTextField(
                    value = autopayData.label,
                    onValueChange = {
                        if (autopayData.label.isEmpty()) {
                            onDataChanged(autopayData.copy(label = it))
                        }
                    },
                    label = "Plan Name",
                    enabled = autopayData.label.isEmpty()
                )

                ModernTextField(
                    value = autopayData.frequency,
                    onValueChange = {
                        if (autopayData.frequency.isEmpty()) {
                            onDataChanged(autopayData.copy(frequency = it))
                        }
                    },
                    label = "Billing Frequency",
                    enabled = autopayData.frequency.isEmpty()
                )

                ModernTextField(
                    value = autopayData.planId,
                    onValueChange = {
                        if (autopayData.planId.isEmpty()) {
                            onDataChanged(autopayData.copy(planId = it))
                        }
                    },
                    label = "Plan ID",
                    enabled = autopayData.planId.isEmpty()
                )

                ModernTextField(
                    value = autopayData.todaysDate,
                    onValueChange = { },
                    label = "Start Date",
                    enabled = false
                )
            }
        }

        // Enhanced Set Autopay button with Loopr styling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Button(
                onClick = onSetAutopay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LooprCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .height(56.dp)
                    .widthIn(min = 160.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Set Autopay",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 4.dp)
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth()
        ) { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (enabled) Color.Transparent
                               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = if (enabled) 0.dp else 12.dp)
            ) {
                if (value.isEmpty() && enabled) {
                    Text(
                        text = "Enter $label",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        }

        // Bottom border for enabled fields
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        color = if (value.isNotEmpty()) SuccessColor.copy(alpha = 0.5f)
                               else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
            )
        }
    }
}
