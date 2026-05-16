package com.example.connect.ui.theme.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.connect.data.firebase.DeviceInfo
import com.example.connect.ui.theme.Charcoal600
import com.example.connect.ui.theme.Charcoal800
import com.example.connect.ui.theme.Charcoal900
import com.example.connect.ui.theme.ElectricCyan
import com.example.connect.ui.theme.TextPrimary
import com.example.connect.ui.theme.TextSecondary
import com.example.connect.viewmodel.ConnectUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private const val TAG = "ScanScreen"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    uiState: ConnectUiState,
    onDeviceScanned: (String) -> Unit,
    onDeviceTapped: (DeviceInfo) -> Unit,
    onRefreshDevices: () -> Unit
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Refresh device list whenever this screen is shown
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
        onRefreshDevices()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal900),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Camera / QR section ───────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // square camera preview area
            ) {
                when {
                    cameraPermission.status.isGranted ->
                        ScannerContent(onDeviceScanned = onDeviceScanned)
                    cameraPermission.status.shouldShowRationale ->
                        PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
                    else ->
                        PermissionRationale(onRequest = { cameraPermission.launchPermissionRequest() })
                }
            }
        }

        // ── "Or connect to" header ────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text       = "Available Devices",
                    color      = TextPrimary,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick  = onRefreshDevices,
                    enabled  = !uiState.isLoadingDevices
                ) {
                    if (uiState.isLoadingDevices) {
                        CircularProgressIndicator(
                            modifier  = Modifier.size(20.dp),
                            color     = ElectricCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector        = Icons.Filled.Refresh,
                            contentDescription = "Refresh devices",
                            tint               = ElectricCyan
                        )
                    }
                }
            }
        }

        // ── Device list ───────────────────────────────────────────────────
        if (uiState.onlineDevices.isEmpty() && !uiState.isLoadingDevices) {
            item {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text      = "No devices online",
                        color     = TextSecondary,
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(uiState.onlineDevices, key = { it.deviceId }) { device ->
                DeviceCard(
                    device    = device,
                    onTap     = { onDeviceTapped(device) },
                    modifier  = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Device card ───────────────────────────────────────────────────────────────

@Composable
private fun DeviceCard(
    device:   DeviceInfo,
    onTap:    () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal800)
            .border(1.dp, Charcoal600, RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Online indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ElectricCyan)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = device.deviceId,
                color      = TextPrimary,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text       = device.url.removePrefix("https://"),
                color      = TextSecondary,
                fontSize   = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }

        Text(
            text     = "Connect →",
            color    = ElectricCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Camera scanner ────────────────────────────────────────────────────────────

@Composable
private fun ScannerContent(onDeviceScanned: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned   by remember { mutableStateOf(false) }
    var scannedId by remember { mutableStateOf<String?>(null) }

    val sweepAnim     = rememberInfiniteTransition(label = "sweep")
    val sweepFraction by sweepAnim.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepFraction"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                val previewView          = PreviewView(ctx)
                val executor             = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                processImageProxy(imageProxy) { deviceId ->
                                    if (!scanned) {
                                        scanned   = true
                                        scannedId = deviceId
                                        onDeviceScanned(deviceId)
                                    }
                                }
                            }
                        }
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val reticleSize = size.width * 0.65f
            val left   = (size.width  - reticleSize) / 2f
            val top    = (size.height - reticleSize) / 2f

            drawRect(color = Color(0xAA000000))
            drawRoundRect(
                color        = Color.Transparent,
                topLeft      = Offset(left, top),
                size         = Size(reticleSize, reticleSize),
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode    = BlendMode.Clear
            )
            drawRoundRect(
                color        = ElectricCyan,
                topLeft      = Offset(left, top),
                size         = Size(reticleSize, reticleSize),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style        = Stroke(width = 2.dp.toPx())
            )
            val sweepY = top + sweepFraction * reticleSize
            drawLine(
                color       = ElectricCyan.copy(alpha = 0.7f),
                start       = Offset(left + 8.dp.toPx(), sweepY),
                end         = Offset(left + reticleSize - 8.dp.toPx(), sweepY),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        Column(
            modifier              = Modifier.fillMaxSize(),
            verticalArrangement   = Arrangement.SpaceBetween,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text     = "Scan Device QR Code",
                color    = TextPrimary,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 32.dp)
            )
            val id = scannedId
            if (id != null) {
                Text(
                    text       = id,
                    color      = ElectricCyan,
                    fontSize   = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.padding(bottom = 16.dp)
                )
            } else {
                Text(
                    text      = "Point at the QR code shown in your terminal",
                    color     = TextSecondary,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(bottom = 16.dp, start = 32.dp, end = 32.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(imageProxy: ImageProxy, onFound: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image   = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.valueType == Barcode.TYPE_TEXT }
                    ?.rawValue
                    ?.let { onFound(it) }
            }
            .addOnFailureListener { Log.e(TAG, "Barcode scan failed", it) }
            .addOnCompleteListener { imageProxy.close() }
    } else {
        imageProxy.close()
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Charcoal900),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Camera permission is required to scan QR codes",
                color     = TextSecondary,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onRequest) {
                Text("Grant Permission")
            }
        }
    }
}