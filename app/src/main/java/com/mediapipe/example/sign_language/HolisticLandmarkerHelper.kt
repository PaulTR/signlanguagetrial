package com.mediapipe.example.sign_language

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

class HolisticLandmarkerHelper(
    private var runningMode: RunningMode = RunningMode.IMAGE, private val context: Context,
    val minFacePresenceConfidence: Float,
    val minHandLandmarksConfidence: Float,
    val minPosePresenceConfidence: Float,
    val minFaceDetectionConfidence: Float,
    val minPoseDetectionConfidence: Float,
    val minFaceSuppressionThreshold: Float,
    val minPoseSuppressionThreshold: Float,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    private val landmarkerHelperListener: LandmarkerListener? = null
) {

    companion object {
        private const val MP_HOLISTIC_LANDMARKER_TASK =
            "holistic_landmarker.task"
        const val TAG = "HolisticLandmarkerHelper"
        const val OTHER_ERROR = 0
        const val DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE = 0F
        const val DEFAULT_MIN_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_POSE_DETECTION_CONFIDENCE = 0F
        const val DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD = 0.5F
        const val DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD = 0F

        const val MIN_CONFIDENCE = 0F
        const val MAX_CONFIDENCE = 1F
    }

    private var holisticLandmarker: HolisticLandmarker? = null

    init {
        setUpHolisticLandmarker()
    }

    private fun setUpHolisticLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()

        baseOptionBuilder.setDelegate(Delegate.CPU)

        // Check if runningMode is consistent with landmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (landmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "holisticLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }

            else -> {
                // no-op
            }
        }
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
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            holisticLandmarker =
                HolisticLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            landmarkerHelperListener?.onError(
                "Holistic Landmarker failed to initialize. See error logs for " + "details"
            )
            Log.e(
                TAG,
                "MediaPipe failed to load the task with error: " + e.message
            )
        } catch (e: RuntimeException) {
            landmarkerHelperListener?.onError(
                "Holistic Landmarker failed to initialize. See error logs for " + "details"
            )
            Log.e(
                TAG,
                "MediaPipe failed to load the task with error: " + e.message
            )
        }
    }


    fun detectLiveStreamCamera(imageProxy: ImageProxy, isFrontCamera: Boolean) {
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
        holisticLandmarker?.detectAsync(mpImage, frameTime)
    }


    private fun returnLivestreamResult(
        result: HolisticLandmarkerResult, input: MPImage
    ) {
        landmarkerHelperListener?.onResults(result)
    }

    // Return errors thrown during detection to this HolisticLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        landmarkerHelperListener?.onError(
            error = error.message ?: "Unknown error"
        )
    }

    fun clearHolisticLandmarker() {
        holisticLandmarker?.close()
        holisticLandmarker = null
    }

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(result: HolisticLandmarkerResult)
    }
}