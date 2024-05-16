package com.mediapipe.example.sign_language

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomSheetScaffold
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FabPosition
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraScreen()
        }
    }


    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    fun CameraScreen(
        viewModel: MainViewModel = viewModel(
            factory = MainViewModel.getFactory(
                this.applicationContext
            )
        )
    ) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Do nothing
            } else {
                // Permission Denied
                Toast.makeText(this, "Camera permission is denied", Toast.LENGTH_SHORT).show()
            }
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        var recordStatus by remember {
            mutableStateOf(RecordStatus.Stopped)
        }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Do nothing
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }

        BottomSheetScaffold(
            sheetPeekHeight = if (uiState.results.isNotEmpty()) 100.dp else 40.dp,
            sheetShape = RoundedCornerShape(
                topStart = 15.dp,
                topEnd = 15.dp
            ),
            floatingActionButton = {
                RecordButton(modifier = Modifier.padding(bottom = if (uiState.results.isNotEmpty()) 100.dp else 12.dp),
                    recordStatus = recordStatus,
                    onRecordStatusUpdated = {
                        if (it == RecordStatus.Stopped) {
                            viewModel.clearInputData()
                        }
                        recordStatus = it
                    })
            },
            floatingActionButtonPosition = FabPosition.Center,
            sheetContent = {
                Column {
                    Image(
                        modifier = Modifier
                            .size(35.dp)
                            .align(Alignment.CenterHorizontally),
                        painter = painterResource(id = R.drawable.icn_chevron_up),
                        contentDescription = ""
                    )
                    if (uiState.results.isNotEmpty()) {
                        ClassificationResultView(
                            results = uiState.results
                        )
                    }
                    ThresholdItem(name = stringResource(id = R.string.tv_face_presence_threshold),
                        value = uiState.threshold.minFacePresenceConfidence,
                        onMinusClicked = {
                            // When clicked, lower face presence score threshold floor
                            var minFacePresenceConfidence =
                                viewModel.uiState.value.threshold.minFacePresenceConfidence
                            if (minFacePresenceConfidence > 0.3f) {
                                minFacePresenceConfidence =
                                    (minFacePresenceConfidence - 0.1f).coerceAtLeast(
                                        0.3f
                                    )
                                viewModel.setMinFacePresenceConfidence(minFacePresenceConfidence)
                            }
                        },
                        onPlusClicked = {
                            // When clicked, raise face presence score threshold floor
                            var minFacePresenceConfidence =
                                viewModel.uiState.value.threshold.minFacePresenceConfidence
                            if (minFacePresenceConfidence < 0.9f) {
                                minFacePresenceConfidence =
                                    (minFacePresenceConfidence + 0.1f).coerceAtMost(
                                        0.9f
                                    )
                                viewModel.setMinFacePresenceConfidence(minFacePresenceConfidence)
                            }
                        })
                    ThresholdItem(name = stringResource(id = R.string.tv_pose_presence_threshold),
                        value = uiState.threshold.minPosePresenceConfidence,
                        onMinusClicked = {
                            var minPosePresenceConfidence =
                                viewModel.uiState.value.threshold.minPosePresenceConfidence
                            if (minPosePresenceConfidence > 0.3f) {
                                minPosePresenceConfidence =
                                    (minPosePresenceConfidence - 0.1f).coerceAtLeast(0.3f)
                                viewModel.setMinPosePresenceConfidence(minPosePresenceConfidence)
                            }
                        },
                        onPlusClicked = {
                            var minPosePresenceConfidence =
                                viewModel.uiState.value.threshold.minPosePresenceConfidence
                            if (minPosePresenceConfidence < 0.9f) {
                                minPosePresenceConfidence =
                                    (minPosePresenceConfidence + 0.1f).coerceAtMost(0.9f)
                                viewModel.setMinPosePresenceConfidence(minPosePresenceConfidence)
                            }
                        })
                    ThresholdItem(name = stringResource(id = R.string.tv_hand_landmarks_threshold),
                        value = uiState.threshold.minHandLandmarkConfidence,
                        onMinusClicked = {
                            var minHandLandmarkConfidence =
                                viewModel.uiState.value.threshold.minHandLandmarkConfidence
                            if (minHandLandmarkConfidence > 0.1f) {
                                minHandLandmarkConfidence =
                                    (minHandLandmarkConfidence - 0.1f).coerceAtLeast(0.1f)
                                viewModel.setMinHandLandmarkConfidence(minHandLandmarkConfidence)
                            }
                        },
                        onPlusClicked = {
                            var minHandLandmarkConfidence =
                                viewModel.uiState.value.threshold.minHandLandmarkConfidence
                            if (minHandLandmarkConfidence < 0.9f) {
                                minHandLandmarkConfidence =
                                    (minHandLandmarkConfidence + 0.1f).coerceAtMost(0.9f)
                                viewModel.setMinHandLandmarkConfidence(minHandLandmarkConfidence)
                            }
                        })
                    ThresholdItem(name = stringResource(id = R.string.tv_face_detection_threshold),
                        value = uiState.threshold.minFaceDetectionConfidence,
                        onMinusClicked = {
                            var minFaceDetectionConfidence =
                                viewModel.uiState.value.threshold.minFaceDetectionConfidence
                            if (minFaceDetectionConfidence > 0.1f) {
                                minFaceDetectionConfidence =
                                    (minFaceDetectionConfidence - 0.1f).coerceAtLeast(0.1f)
                                viewModel.setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                            }
                        },
                        onPlusClicked = {
                            var minFaceDetectionConfidence =
                                viewModel.uiState.value.threshold.minFaceDetectionConfidence
                            if (minFaceDetectionConfidence < 0.9f) {
                                minFaceDetectionConfidence =
                                    (minFaceDetectionConfidence + 0.1f).coerceAtMost(0.9f)
                                viewModel.setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                            }
                        })
                    ThresholdItem(name = stringResource(id = R.string.tv_pose_detection_threshold),
                        value = uiState.threshold.minPoseDetectionConfidence,
                        onMinusClicked = {
                            var minPoseDetectionConfidence =
                                viewModel.uiState.value.threshold.minPoseDetectionConfidence
                            if (minPoseDetectionConfidence > 0.1f) {
                                minPoseDetectionConfidence =
                                    (minPoseDetectionConfidence - 0.1f).coerceAtLeast(0.1f)
                                viewModel.setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                            }
                        },
                        onPlusClicked = {
                            var minPoseDetectionConfidence =
                                viewModel.uiState.value.threshold.minPoseDetectionConfidence
                            if (minPoseDetectionConfidence < 0.9f) {
                                minPoseDetectionConfidence =
                                    (minPoseDetectionConfidence + 0.1f).coerceAtMost(0.9f)
                                viewModel.setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                            }
                        })
                    ThresholdItem(name = stringResource(id = R.string.tv_face_suppression_threshold),
                        value = uiState.threshold.minFaceSuppressionThreshold,
                        onMinusClicked = {
                            var minFaceSuppressionThreshold =
                                viewModel.uiState.value.threshold.minFaceSuppressionThreshold
                            if (minFaceSuppressionThreshold > 0.1f) {
                                minFaceSuppressionThreshold =
                                    (minFaceSuppressionThreshold - 0.1f).coerceAtLeast(0.1f)
                                viewModel.setMinFaceSuppressionThreshold(minFaceSuppressionThreshold)
                            }
                        },
                        onPlusClicked = {
                            var minFaceSuppressionThreshold =
                                viewModel.uiState.value.threshold.minFaceSuppressionThreshold
                            if (minFaceSuppressionThreshold < 0.9f) {
                                minFaceSuppressionThreshold =
                                    (minFaceSuppressionThreshold + 0.1f).coerceAtMost(0.9f)
                                viewModel.setMinFaceSuppressionThreshold(minFaceSuppressionThreshold)
                            }
                        })
                    ThresholdItem(name = stringResource(id = R.string.tv_pose_suppression_threshold),
                        value = uiState.threshold.minPoseSuppressionThreshold,
                        onMinusClicked = {
                            var minPoseSuppressionThreshold =
                                viewModel.uiState.value.threshold.minPoseSuppressionThreshold
                            if (minPoseSuppressionThreshold > 0.1f) {
                                minPoseSuppressionThreshold =
                                    (minPoseSuppressionThreshold - 0.1f).coerceAtLeast(0.1f)
                                viewModel.setMinPoseSuppressionThreshold(minPoseSuppressionThreshold)
                            }
                        },
                        onPlusClicked = {
                            var minPoseSuppressionThreshold =
                                viewModel.uiState.value.threshold.minPoseSuppressionThreshold
                            if (minPoseSuppressionThreshold < 0.9f) {
                                minPoseSuppressionThreshold =
                                    (minPoseSuppressionThreshold + 0.1f).coerceAtMost(0.9f)
                                viewModel.setMinPoseSuppressionThreshold(minPoseSuppressionThreshold)
                            }
                        })
                }
            },
        ) {
            Box {
                Column {
                    Header()
                    CameraPreview { imageProxy ->
                        if (recordStatus == RecordStatus.Recording) {
                            viewModel.detectLiveStreamImage(
                                imageProxy = imageProxy, isFrontCamera = true
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Header(modifier: Modifier = Modifier) {
        TopAppBar(
            modifier = modifier,
            backgroundColor = Color.LightGray,
            title = {
                Image(
                    modifier = Modifier.fillMaxSize(),
                    painter = painterResource(id = R.drawable.media_pipe_banner),
                    contentDescription = null,
                )
            },
        )
    }

    @Composable
    fun CameraPreview(
        onImageAnalyzed: (ImageProxy) -> Unit
    ) {
        val context = LocalContext.current

        val lifecycleOwner = LocalLifecycleOwner.current

        val cameraProviderFuture by remember {
            mutableStateOf(ProcessCameraProvider.getInstance(context))
        }

        // Initialize CameraX, and prepare to bind the camera use cases
        AndroidView(factory = {
            val previewView = PreviewView(it).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_START
            }

            val executor = Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
                // Build and bind the camera use cases
                bindCameraUseCases(
                    lifecycleOwner = lifecycleOwner,
                    cameraProviderFuture = cameraProviderFuture,
                    executor = executor,
                    previewView = previewView,
                    onImageAnalyzed = onImageAnalyzed
                )
            }, ContextCompat.getMainExecutor(context))

            previewView
        })
    }

    @Composable
    fun RecordButton(
        recordStatus: RecordStatus,
        modifier: Modifier = Modifier,
        onRecordStatusUpdated: (RecordStatus) -> Unit
    ) {
        Box(
            modifier = modifier
                .size(70.dp) // Adjust size as needed
                .clip(CircleShape)
                .background(
                    color = Color.Gray.copy(alpha = 0.9f)
                )
                .clickable {
                    val status =
                        if (recordStatus == RecordStatus.Recording) RecordStatus.Stopped else RecordStatus.Recording
                    onRecordStatusUpdated(status)
                },
            contentAlignment = Alignment.Center,
        ) {
            // Animate pulsating circle based on recording state
            val pulseScale by animateFloatAsState(
                targetValue = if (recordStatus == RecordStatus.Recording) 1.2f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1000),
                ),
                label = ""
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (recordStatus == RecordStatus.Recording) {
                    drawCircle(
                        color = Color.Red,
                        radius = size.minDimension / 3 * pulseScale, // Adjust circle size
                    )
                } else {
                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(x = size.width / 4, y = size.height / 4),
                        size = Size(width = size.width * 2 / 4, height = size.height * 2 / 4),
                    )
                }
            }
        }
    }

    @Composable
    fun ClassificationResultView(
        results: List<ClassificationResult>,
        modifier: Modifier = Modifier
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 5.dp)
                .background(color = Color.White),
            verticalArrangement = Arrangement.spacedBy(3.dp),

            ) {
            items(results.size) {
                Row {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = results[it].label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = String.format(
                            Locale.US, "%.2f", results[it].value
                        ), fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }


    @Composable
    fun ThresholdItem(
        name: String,
        value: Float,
        modifier: Modifier = Modifier,
        onMinusClicked: () -> Unit,
        onPlusClicked: () -> Unit,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(modifier = Modifier.weight(0.5f), text = name, fontSize = 17.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    onMinusClicked()
                }) {
                    Text(text = "-", fontSize = 17.sp)
                }
                Text(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    text = String.format(Locale.US, "%.1f", value),
                    fontSize = 17.sp
                )
                Button(onClick = {
                    onPlusClicked()
                }) {
                    Text(text = "+", fontSize = 17.sp)
                }
            }
        }
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
        executor: ExecutorService,
        previewView: PreviewView,
        onImageAnalyzed: (ImageProxy) -> Unit,
    ) {
        val preview: Preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val cameraSelector: CameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        val imageAnalysis = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            onImageAnalyzed(imageProxy)
            imageProxy.close()
        }
        val cameraProvider = cameraProviderFuture.get()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis, preview)
    }

    enum class RecordStatus {
        Recording, Stopped
    }

}
