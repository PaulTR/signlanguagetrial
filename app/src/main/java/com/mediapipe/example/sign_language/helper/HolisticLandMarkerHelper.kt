package com.mediapipe.example.sign_language.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class HolisticLandMarkerHelper(
    private val context: Context,
    private val runningMode: RunningMode = RunningMode.LIVE_STREAM,
    var minFacePresenceConfidence: Float = DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE,
    var minHandLandmarksConfidence: Float = DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE,
    var minFaceDetectionConfidence: Float = DEFAULT_MIN_FACE_DETECTION_CONFIDENCE,
    var minPoseDetectionConfidence: Float = DEFAULT_MIN_POSE_DETECTION_CONFIDENCE,
    var minFaceSuppressionThreshold: Float = DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD,
    var minPoseSuppressionThreshold: Float = DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD,
) {

    companion object {
        private const val MP_HOLISTIC_LANDMARKER_TASK =
            "holistic_landmarker.task"
        const val TAG = "HolisticLandmarkerHelper"
        const val DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE = 0.3F
        const val DEFAULT_MIN_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_POSE_DETECTION_CONFIDENCE = 0.3F
        const val DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD = 0.5F
        const val DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD = 0.3F
    }

    private var holisticLandMarker: HolisticLandmarker? = null

    fun setUpHolisticLandMarker(): Flow<Result<HolisticLandmarkerResult>> {
        return callbackFlow {
            val baseOptionBuilder = BaseOptions.builder()

            baseOptionBuilder.setDelegate(Delegate.CPU)

            try {
                baseOptionBuilder.setModelAssetPath(MP_HOLISTIC_LANDMARKER_TASK)
                val baseOptions = baseOptionBuilder.build()
                val optionsBuilder =
                    HolisticLandmarker.HolisticLandmarkerOptions.builder()
                        .setBaseOptions(baseOptions).setRunningMode(runningMode)
                        .setMinHandLandmarksConfidence(minHandLandmarksConfidence)
                        .setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                        .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                        .setMinFaceSuppressionThreshold(minFaceSuppressionThreshold)
                        .setMinPoseSuppressionThreshold(minPoseSuppressionThreshold)
                        .setMinFacePresenceConfidence(minFacePresenceConfidence)
                        .setMinPosePresenceConfidence(minPosePresenceConfidence)

                // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
                if (runningMode == RunningMode.LIVE_STREAM) {
                    optionsBuilder
                        .setResultListener { value, _ ->
                            trySend(Result.success(value))
                        }
                        .setErrorListener {
                            trySend(Result.failure(it))
                        }
                }

                val options = optionsBuilder.build()
                holisticLandMarker =
                    HolisticLandmarker.createFromOptions(context, options)

            } catch (e: Exception) {
                trySend(Result.failure(e))
                Log.e(
                    TAG,
                    "MediaPipe failed to load the task with error: " + e.message
                )
            }

            awaitClose {}
        }
    }


    fun detectLiveStreamImage(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" + " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    private fun detectAsync(mpImage: MPImage?, frameTime: Long) {
        holisticLandMarker?.detectAsync(mpImage, frameTime)
    }


    fun clearHolisticLandMarker() {
        holisticLandMarker?.close()
        holisticLandMarker = null
    }
}