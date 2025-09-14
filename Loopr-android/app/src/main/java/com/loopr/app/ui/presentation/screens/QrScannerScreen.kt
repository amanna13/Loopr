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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.loopr.app.R
import com.loopr.app.ui.presentation.modules.bindCameraUseCases
import com.loopr.app.ui.theme.LooprCyan
import com.loopr.app.ui.theme.SuccessColor
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

enum class ScannerScreenState {
    SCANNING,
    AUTOPAY_SETUP,
    SUCCESS
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onNavigateToHome: () -> Unit = {}
) {
    var screenState by remember { mutableStateOf(ScannerScreenState.SCANNING) }
    var autopayData by remember { mutableStateOf(AutopayData()) }

    when (screenState) {
        ScannerScreenState.SCANNING -> {
            QrScannerCamera(
                onQrCodeScanned = { qrCode ->
                    onQrCodeScanned(qrCode)
                    val parsedData = parseQrCode(qrCode)
                    if (parsedData != null) {
                        autopayData = parsedData
                        screenState = ScannerScreenState.AUTOPAY_SETUP
                    }
                }
            )
        }

        ScannerScreenState.AUTOPAY_SETUP -> {
            AutopaySetupScreen(
                autopayData = autopayData,
                onDataChanged = { autopayData = it },
                onBackToScanner = {
                    screenState = ScannerScreenState.SCANNING
                    autopayData = AutopayData()
                },
                onSetAutopay = {
                    screenState = ScannerScreenState.SUCCESS
                }
            )
        }

        ScannerScreenState.SUCCESS -> {
            AutopaySuccessScreen(
                onBackToHome = {
                    onNavigateToHome() // Call navigation first
                    screenState = ScannerScreenState.SCANNING
                    autopayData = AutopayData()
                }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerCamera(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases(
                        cameraProvider, lifecycleOwner, previewView, onQrCodeScanned
                    )
                }, ContextCompat.getMainExecutor(context))
            }
        )

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

        // Bottom instruction card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Scan to set Autopay",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Powered by Loopr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AutopaySetupScreen(
    autopayData: AutopayData,
    onDataChanged: (AutopayData) -> Unit,
    onBackToScanner: () -> Unit,
    onSetAutopay: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Fixed Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToScanner,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Fixed Title and subtitle section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Setup Autopay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Powered by Loopr",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
            Spacer(Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Scrollable content area with visible scrollbar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = true
                )
                .padding(horizontal = 24.dp)
        ) {
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

                // Separate state for amount input to prevent keyboard dismissal
                var userAmountInput by remember { mutableStateOf("") }

                // Initialize with scanned amount if available
                if (autopayData.amount.isNotEmpty() && userAmountInput.isEmpty()) {
                    userAmountInput = autopayData.amount
                }

                val isEditable = autopayData.amount.isEmpty()
                val displayAmount = if (isEditable) userAmountInput else autopayData.amount

                BasicTextField(
                    value = displayAmount,
                    onValueChange = { value ->
                        if (isEditable) {
                            // Remove any non-numeric characters except decimal point
                            val cleanValue = value.filter { it.isDigit() || it == '.' }
                            userAmountInput = cleanValue
                            onDataChanged(autopayData.copy(amount = cleanValue))
                        }
                    },
                    enabled = isEditable,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isEditable)
                            MaterialTheme.colorScheme.onSurface
                        else
                            LooprCyan
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                ) { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (displayAmount.isEmpty()) {
                            Text(
                                text = "0.00 SOL",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        } else {
                            Text(
                                text = "$displayAmount SOL",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEditable)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        LooprCyan
                                )
                            )
                        }
                    }
                }
            }

            // Stylized Details Card with gradient background
            Card(
                modifier = Modifier
                    .fillMaxWidth()
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
                    .padding(top = 24.dp, bottom = 24.dp),
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

@Composable
fun AutopaySuccessScreen(
    onBackToHome: () -> Unit
) {
    // Load Lottie animation
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.successfullydone))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = true,
        iterations = 1 // Play once
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        LooprCyan.copy(alpha = 0.1f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Lottie animation
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Success message
            Text(
                text = "Autopay Set Successfully!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your autopay settings have been saved successfully. You will be automatically charged according to your subscription plan.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Back to Home button
            Button(
                onClick = onBackToHome,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LooprCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 32.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = "Back to Home",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun parseQrCode(qrCode: String): AutopayData? {
    if (!qrCode.startsWith("loopr://setautopay")) return null

    return try {
        val qrWithoutProtocol = qrCode.removePrefix("loopr://setautopay?")
        val parts = qrWithoutProtocol.split("?", limit = 2)
        val solanaAcc = if (parts.isNotEmpty()) parts[0] else ""

        val params = if (parts.size > 1) {
            parts[1].split("&").associate { param ->
                val keyValue = param.split("=", limit = 2)
                if (keyValue.size == 2) {
                    keyValue[0] to URLDecoder.decode(keyValue[1], "UTF-8")
                } else {
                    keyValue[0] to ""
                }
            }
        } else {
            emptyMap()
        }

        val memo = params["memo"]
        var frequency = ""
        var planId = ""

        if (memo != null) {
            try {
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

        AutopayData(
            solanaAcc = solanaAcc,
            amount = params["amount"] ?: "",
            label = params["label"] ?: "",
            message = params["message"] ?: "",
            frequency = frequency,
            planId = planId,
            todaysDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    } catch (e: Exception) {
        null
    }
}
