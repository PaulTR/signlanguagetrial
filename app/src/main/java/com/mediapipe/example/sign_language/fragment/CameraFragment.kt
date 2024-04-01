package com.mediapipe.example.sign_language.fragment

import CameraViewModel
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import com.mediapipe.example.sign_language.HolisticLandmarkerHelper
import com.mediapipe.example.sign_language.LandmarkIndex
import com.mediapipe.example.sign_language.R
import com.mediapipe.example.sign_language.ResultsAdapter
import com.mediapipe.example.sign_language.SignLanguageHelper
import com.mediapipe.example.sign_language.databinding.FragmentCameraBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), HolisticLandmarkerHelper.LandmarkerListener {
    companion object {
        private const val TAG = "Hand gesture recognizer"
    }

    // Camera and view
    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
    private val resultsAdapter: ResultsAdapter by lazy {
        ResultsAdapter()
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    // recording button
    private var animationI = 0
    private val al: ArrayList<Int> = ArrayList()
    private val al2: ArrayList<Int> = ArrayList()
    private var handler: Handler? = null
    private var runnable: Runnable? = null

    private var isRecording = false

    private var inputArray: MutableList<Array<FloatArray>> = mutableListOf()
    private lateinit var signLanguageHelper: SignLanguageHelper

    private lateinit var holisticLandmarkerHelper: HolisticLandmarkerHelper

    private val viewModel: CameraViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        holisticLandmarkerHelper.clearHolisticLandmarker()
        signLanguageHelper.close()
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(fragmentCameraBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultsAdapter
        }

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Attach listeners to UI control widgets
        initButtonRecording()

        signLanguageHelper = SignLanguageHelper(requireContext())
        signLanguageHelper.createInterpreter(object : SignLanguageHelper.SignLanguageListener {
            override fun onResult(results: List<Pair<String, Float>>) {
                resultsAdapter.updateResults(results)
            }
        })
        viewModel.helperState.observe(viewLifecycleOwner) {
            updateBottomSheetControlsUi(helperState = it)
        }
        setUpBottomSheetListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initButtonRecording() {
        fragmentCameraBinding.btnRecording.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    inputArray.clear()
                    isRecording = true
                    startAnimationOfSquare()
                    fragmentCameraBinding.btnRecording.animateRadius(
                        fragmentCameraBinding.btnRecording.getmMaxRadius(),
                        fragmentCameraBinding.btnRecording.getmMinStroke()
                    )
                    runnable?.let { handler?.postDelayed(it, 80) }
                    return@setOnTouchListener true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isRecording = false
                    fragmentCameraBinding.btnRecording.animateRadius(
                        fragmentCameraBinding.btnRecording.getmMinRadius(),
                        fragmentCameraBinding.btnRecording.getmMinStroke()
                    )
                    stopAnimationOfSquare()
                    runnable?.let { handler?.removeCallbacks(it) }
                    resetAnimation()
                    runClassification()
                    return@setOnTouchListener true
                }
            }
            return@setOnTouchListener true
        }
        resetAnimation()
        handler = Handler()
        runnable = Runnable {

            //to make smooth stroke width animation I increase and decrease value step by step
            val random: Int
            if (al.isNotEmpty()) {
                random = al[animationI++]
                if (animationI >= al.size) {
                    for (j in al.indices.reversed()) {
                        al2.add(al[j])
                    }
                    al.clear()
                    animationI = 0
                }
            } else {
                random = al2[animationI++]
                if (animationI >= al2.size) {
                    for (j in al2.indices.reversed()) {
                        al.add(al2[j])
                    }
                    al2.clear()
                    animationI = 0
                }
            }
            fragmentCameraBinding.btnRecording.animateRadius(
                fragmentCameraBinding.btnRecording.getmMaxRadius(), random.toFloat()
            )
            handler?.postDelayed(runnable!!, 130)
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun setUpBottomSheetListener() {
        with(fragmentCameraBinding.bottomSheetLayout) {
            // When clicked, lower face presence score threshold floor
            facePresenceThresholdMinus.setOnClickListener {
                var minFacePresenceConfidence =
                    viewModel.helperState.value!!.minFacePresenceConfidence
                if (minFacePresenceConfidence > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minFacePresenceConfidence =
                        (minFacePresenceConfidence - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinFacePresenceConfidence(minFacePresenceConfidence)
                }
            }

            // When clicked, raise face presence score threshold floor
            facePresenceThresholdPlus.setOnClickListener {
                var minFacePresenceConfidence =
                    viewModel.helperState.value!!.minFacePresenceConfidence
                if (minFacePresenceConfidence < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minFacePresenceConfidence =
                        (minFacePresenceConfidence + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinFacePresenceConfidence(minFacePresenceConfidence)
                }
            }

            // When clicked, lower pose presence score threshold floor
            posePresenceThresholdMinus.setOnClickListener {
                var minPosePresenceConfidence =
                    viewModel.helperState.value!!.minPosePresenceConfidence
                if (minPosePresenceConfidence > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minPosePresenceConfidence =
                        (minPosePresenceConfidence - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinPosePresenceConfidence(minPosePresenceConfidence)
                }
            }

            // When clicked, raise pose presence score threshold floor
            posePresenceThresholdPlus.setOnClickListener {
                var minPosePresenceConfidence =
                    viewModel.helperState.value!!.minPosePresenceConfidence
                if (minPosePresenceConfidence < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minPosePresenceConfidence =
                        (minPosePresenceConfidence + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinPosePresenceConfidence(minPosePresenceConfidence)
                }
            }

            // When clicked, lower hand detection score threshold floor
            handLandmarksThresholdMinus.setOnClickListener {
                var minHandLandmarkConfidence =
                    viewModel.helperState.value!!.minHandLandmarkConfidence
                if (minHandLandmarkConfidence > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minHandLandmarkConfidence =
                        (minHandLandmarkConfidence - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinHandLandmarkConfidence(minHandLandmarkConfidence)
                }
            }

            // When clicked, raise hand detection score threshold floor
            handLandmarksThresholdPlus.setOnClickListener {
                var minHandLandmarkConfidence =
                    viewModel.helperState.value!!.minHandLandmarkConfidence
                if (minHandLandmarkConfidence < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minHandLandmarkConfidence =
                        (minHandLandmarkConfidence + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinHandLandmarkConfidence(minHandLandmarkConfidence)
                }
            }

            // When clicked, lower face detection score threshold floor
            faceDetectionThresholdMinus.setOnClickListener {
                var minFaceDetectionConfidence =
                    viewModel.helperState.value!!.minFaceDetectionConfidence
                if (minFaceDetectionConfidence > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minFaceDetectionConfidence =
                        (minFaceDetectionConfidence - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                }
            }

            // When clicked, raise face detection score threshold floor
            faceDetectionThresholdPlus.setOnClickListener {
                var minFaceDetectionConfidence =
                    viewModel.helperState.value!!.minFaceDetectionConfidence
                if (minFaceDetectionConfidence < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minFaceDetectionConfidence =
                        (minFaceDetectionConfidence + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                }
            }

            // When clicked, lower pose detection score threshold floor
            poseDetectionThresholdMinus.setOnClickListener {
                var minPoseDetectionConfidence =
                    viewModel.helperState.value!!.minPoseDetectionConfidence
                if (minPoseDetectionConfidence > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minPoseDetectionConfidence =
                        (minPoseDetectionConfidence - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                }
            }

            // When clicked, raise pose detection score threshold floor
            poseDetectionThresholdPlus.setOnClickListener {
                var minPoseDetectionConfidence =
                    viewModel.helperState.value!!.minPoseDetectionConfidence
                if (minPoseDetectionConfidence < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minPoseDetectionConfidence =
                        (minPoseDetectionConfidence + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                }
            }

            // When clicked, lower face suppression score threshold floor
            faceSuppressionMinus.setOnClickListener {
                var minFaceSuppressionThreshold =
                    viewModel.helperState.value!!.minFaceSuppressionThreshold
                if (minFaceSuppressionThreshold > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minFaceSuppressionThreshold =
                        (minFaceSuppressionThreshold - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinFaceSuppressionThreshold(minFaceSuppressionThreshold)
                }
            }

            // When clicked, raise face suppression score threshold floor
            faceSuppressionPlus.setOnClickListener {
                var minFaceSuppressionThreshold =
                    viewModel.helperState.value!!.minFaceSuppressionThreshold
                if (minFaceSuppressionThreshold < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minFaceSuppressionThreshold =
                        (minFaceSuppressionThreshold + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinFaceSuppressionThreshold(minFaceSuppressionThreshold)
                }
            }

            // When clicked, lower pose suppression score threshold floor
            poseSuppressionMinus.setOnClickListener {
                var minPoseSuppressionThreshold =
                    viewModel.helperState.value!!.minPoseSuppressionThreshold
                if (minPoseSuppressionThreshold > HolisticLandmarkerHelper.MIN_CONFIDENCE) {
                    minPoseSuppressionThreshold =
                        (minPoseSuppressionThreshold - 0.1f).coerceAtLeast(HolisticLandmarkerHelper.MIN_CONFIDENCE)
                    viewModel.setMinPoseSuppressionThreshold(minPoseSuppressionThreshold)
                }
            }

            // When clicked, raise pose suppression score threshold floor
            poseSuppressionPlus.setOnClickListener {
                var minPoseSuppressionThreshold =
                    viewModel.helperState.value!!.minPoseSuppressionThreshold
                if (minPoseSuppressionThreshold < HolisticLandmarkerHelper.MAX_CONFIDENCE) {
                    minPoseSuppressionThreshold =
                        (minPoseSuppressionThreshold + 0.1f).coerceAtMost(HolisticLandmarkerHelper.MAX_CONFIDENCE)
                    viewModel.setMinPoseSuppressionThreshold(minPoseSuppressionThreshold)
                }
            }
        }
    }

    // Update the values displayed in the bottom sheet & Reset HolisticLandmarkerHelper
    private fun updateBottomSheetControlsUi(helperState: HelperState) {
        // Update bottom sheet settings
        with(fragmentCameraBinding.bottomSheetLayout) {
            facePresenceThresholdValue.text = String.format(
                Locale.US, "%.1f", helperState.minFacePresenceConfidence
            )

            posePresenceThresholdValue.text = String.format(
                Locale.US, "%.1f", helperState.minPosePresenceConfidence
            )
            handLandmarksThresholdValue.text = String.format(
                Locale.US, "%.1f", helperState.minHandLandmarkConfidence
            )
            faceDetectionThresholdValue.text = String.format(
                Locale.US, "%.1f", helperState.minFaceDetectionConfidence
            )
            poseDetectionThresholdValue.text = String.format(
                Locale.US, "%.1f", helperState.minPoseDetectionConfidence
            )
            faceSuppressionValue.text = String.format(
                Locale.US, "%.1f", helperState.minFaceSuppressionThreshold
            )
            poseSuppressionValue.text = String.format(
                Locale.US, "%.1f", helperState.minPoseSuppressionThreshold
            )
        }
        // Create the HolisticLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            // Clear it and recreate with new settings
            holisticLandmarkerHelper = HolisticLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minFacePresenceConfidence = helperState.minFacePresenceConfidence,
                minHandLandmarksConfidence = helperState.minHandLandmarkConfidence,
                minPosePresenceConfidence = helperState.minPosePresenceConfidence,
                minFaceDetectionConfidence = helperState.minFaceDetectionConfidence,
                minPoseDetectionConfidence = helperState.minPoseDetectionConfidence,
                minFaceSuppressionThreshold = helperState.minFaceSuppressionThreshold,
                minPoseSuppressionThreshold = helperState.minPoseSuppressionThreshold,
                landmarkerHelperListener = this
            )
        }
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation).build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
            // The analyzer can then be assigned to the instance
            .also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    if (isRecording) {
                        extractHolistic(image)
                    }
                    image.close()
                }
            }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = fragmentCameraBinding.viewFinder.display.rotation
    }

    private var settingPopupVisibilityDuration: Long = 0
    private var currentAnimator: AnimatorSet? = null

    private fun startAnimationOfSquare() {
        settingPopupVisibilityDuration =
            resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        if (currentAnimator != null) {
            currentAnimator?.cancel()
        }
        val finalBounds = Rect()
        val globalOffset = Point()
        fragmentCameraBinding.btnRecording.getGlobalVisibleRect(
            finalBounds, globalOffset
        )
        TransitionManager.beginDelayedTransition(
            fragmentCameraBinding.ivSquare,
            TransitionSet().addTransition(ChangeBounds())
                .setDuration(settingPopupVisibilityDuration)
        )
        val params: ViewGroup.LayoutParams = fragmentCameraBinding.ivSquare.layoutParams
        params.height = dpToPx(40f)
        params.width = dpToPx(40f)
        fragmentCameraBinding.ivSquare.layoutParams = params
        val set = AnimatorSet()
        set.play(
            ObjectAnimator.ofFloat(
                fragmentCameraBinding.ivSquare, "radius", dpToPx(8f).toFloat()
            )
        )
        set.duration = settingPopupVisibilityDuration
        set.interpolator = DecelerateInterpolator()
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finishAnimation()
            }

            override fun onAnimationCancel(animation: Animator) {
                finishAnimation()
            }

            private fun finishAnimation() {
                currentAnimator = null
            }
        })
        set.start()
        currentAnimator = set
    }

    private fun stopAnimationOfSquare() {
        if (currentAnimator != null) {
            currentAnimator?.cancel()
        }
        TransitionManager.beginDelayedTransition(
            fragmentCameraBinding.ivSquare,
            TransitionSet().addTransition(ChangeBounds())
                .setDuration(settingPopupVisibilityDuration)
        )
        val params: ViewGroup.LayoutParams = fragmentCameraBinding.ivSquare.layoutParams
        params.width = dpToPx(80f)
        params.height = dpToPx(80f)
        fragmentCameraBinding.ivSquare.layoutParams = params
        val set1 = AnimatorSet()
        set1.play(
            ObjectAnimator.ofFloat(
                fragmentCameraBinding.ivSquare, "radius", dpToPx(40f).toFloat()
            )
        ) //radius = height/2 to make it round
        set1.duration = settingPopupVisibilityDuration
        set1.interpolator = DecelerateInterpolator()
        set1.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finishAnimation()
            }

            override fun onAnimationCancel(animation: Animator) {
                finishAnimation()
            }

            private fun finishAnimation() {
                currentAnimator = null
            }
        })
        set1.start()
        currentAnimator = set1
    }

    private fun dpToPx(valueInDp: Float): Int {
        val metrics = resources.displayMetrics
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics
        ).toInt()
    }

    private fun resetAnimation() {
        animationI = 0
        al.clear()
        al2.clear()
        al.add(25)
        al.add(30)
        al.add(35)
        al.add(40)
        al.add(45)
        fragmentCameraBinding.btnRecording.endAnimation()
    }

    private fun runClassification() {
        signLanguageHelper.runInterpreter(inputArray)
    }

    private fun extractHolistic(imageProxy: ImageProxy) {
        holisticLandmarkerHelper.detectLiveStreamCamera(imageProxy, true)
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e(
            TAG, "HolisticLandmarkerHelper error: $error, code: $errorCode"
        )
    }

    override fun onResults(result: HolisticLandmarkerResult) {
        // 543 landmarks (33 pose landmarks, 468 face landmarks, and 21 hand landmarks per hand)
        val data = Array(543) { FloatArray(3) { Float.NaN } }
        result.faceLandmarks().forEachIndexed { index, normalizedLandmark ->
            if (index < LandmarkIndex.LEFT_HANDLANDMARK_INDEX) {
                data[index + LandmarkIndex.FACE_LANDMARK_INDEX][0] = normalizedLandmark.x()
                data[index + LandmarkIndex.FACE_LANDMARK_INDEX][1] = normalizedLandmark.y()
                data[index + LandmarkIndex.FACE_LANDMARK_INDEX][2] = normalizedLandmark.z()
            }
        }
        result.leftHandLandmarks().forEachIndexed { index, normalizedLandmark ->
            data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][0] = normalizedLandmark.x()
            data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][1] = normalizedLandmark.y()
            data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][2] = normalizedLandmark.z()
        }
        result.poseLandmarks().forEachIndexed { index, normalizedLandmark ->
            data[index + LandmarkIndex.POSE_LANDMARK_INDEX][0] = normalizedLandmark.x()
            data[index + LandmarkIndex.POSE_LANDMARK_INDEX][1] = normalizedLandmark.y()
            data[index + LandmarkIndex.POSE_LANDMARK_INDEX][2] = normalizedLandmark.z()
        }
        result.rightHandLandmarks().forEachIndexed { index, normalizedLandmark ->
            data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][0] = normalizedLandmark.x()
            data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][1] = normalizedLandmark.y()
            data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][2] = normalizedLandmark.z()
        }
        inputArray.add(data)
    }
}
