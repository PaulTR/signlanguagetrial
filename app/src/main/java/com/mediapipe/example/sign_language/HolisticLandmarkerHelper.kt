package com.mediapipe.example.sign_language

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult

class HolisticLandmarkerHelper(
    var runningMode: RunningMode = RunningMode.IMAGE, val context: Context,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val landmarkerHelperListener: LandmarkerListener? = null
) {

    companion object {
        private const val MP_HOLISTIC_LANDMARKER_TASK =
            "tasks/holistic_landmarker.task"
        const val TAG = "HolisticLandmarkerHelper"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val DEFAULT_MIN_FACE_LANDMARKS_CONFIDENCE = 0.1F
        const val DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE = 0.0F
        const val DEFAULT_MIN_POSE_LANDMARKS_CONFIDENCE = 0.1F
        const val DEFAULT_MIN_FACE_DETECTION_CONFIDENCE = 0.0F
        const val DEFAULT_MIN_POSE_DETECTION_CONFIDENCE = 0.0F
        const val DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD = 0.1F
        const val DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD = 0.1F
    }

    private var holisticLandmarker: HolisticLandmarker? = null

    init {
        setUpHolisticLandmarker()
    }

    fun setUpHolisticLandmarker() {
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
                    .setMinFaceLandmarksConfidence(
                        DEFAULT_MIN_FACE_LANDMARKS_CONFIDENCE
                    ).setMinHandLandmarksConfidence(
                        DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE
                    ).setMinPoseLandmarksConfidence(
                        DEFAULT_MIN_POSE_LANDMARKS_CONFIDENCE
                    ).setMinFaceDetectionConfidence(
                        DEFAULT_MIN_FACE_DETECTION_CONFIDENCE
                    ).setMinPoseDetectionConfidence(
                        DEFAULT_MIN_POSE_DETECTION_CONFIDENCE
                    ).setMinFaceSuppressionThreshold(
                        DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD
                    ).setMinPoseSuppressionThreshold(
                        DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD
                    )

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
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


    fun detectVideoFile(
        bitmap: Bitmap, timestampMs: Long
    ): HolisticLandmarkerResult? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" + " while not using RunningMode.VIDEO"
            )
        }
        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Run holistic landmarker using MediaPipe Holistic Landmarker API
        return holisticLandmarker?.detectForVideo(mpImage, timestampMs)
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