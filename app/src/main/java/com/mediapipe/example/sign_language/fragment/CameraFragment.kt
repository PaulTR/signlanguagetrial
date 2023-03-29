package com.mediapipe.example.sign_language.fragment

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
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
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.framework.AndroidPacketCreator
import com.google.mediapipe.framework.Graph
import com.google.mediapipe.framework.PacketGetter
import com.google.mediapipe.glutil.EglManager
import com.mediapipe.example.sign_language.MainActivity
import com.mediapipe.example.sign_language.R
import com.mediapipe.example.sign_language.ResultsAdapter
import com.mediapipe.example.sign_language.SignLanguageHelper
import com.mediapipe.example.sign_language.databinding.FragmentCameraBinding
import java.util.*
import java.util.concurrent.*


class CameraFragment : Fragment() {
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
    private var i = 0
    private val al: ArrayList<Int> = ArrayList()
    private val al2: ArrayList<Int> = ArrayList()
    private var handler: Handler? = null
    private var runnable: Runnable? = null

    private var isRecording = false

    // MediaPipe graph
    private lateinit var graph: Graph
    private lateinit var packetCreator: AndroidPacketCreator
    private var inputArray: MutableList<Array<FloatArray>> = mutableListOf()
    private lateinit var signLanguageHelper: SignLanguageHelper
    private val adapter: ResultsAdapter by lazy {
        ResultsAdapter()
    }
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
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
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

        // Create the Hand Gesture Recognition Helper that will handle the
        // inference
        backgroundExecutor.execute {

        }

        // Attach listeners to UI control widgets
        initButtonRecording()
        val eglManager = EglManager(null)
        graph = Graph()
        graph.loadBinaryGraph(
            AndroidAssetUtil.getAssetBytes(
                requireActivity().assets,
                MainActivity.BINARY_GRAPH_NAME
            )
        )
        packetCreator = AndroidPacketCreator(graph)
        graph.setParentGlContext(eglManager.nativeContext)
        graph.addPacketCallback(MainActivity.OUTPUT_VIDEO_STREAM_NAME) { packet ->
            val matrixLandmark = PacketGetter.getMatrixData(packet)
            val rows = PacketGetter.getMatrixRows(packet)
            val cols = PacketGetter.getMatrixCols(packet)
            val data = Array(cols) { FloatArray(rows) }
            val nums = 3
            for (i in 0 until cols) {
                data[i][0] = matrixLandmark[i * nums]
                data[i][1] = matrixLandmark[i * nums + 1]
                data[i][2] = matrixLandmark[i * nums + 2]
            }
            inputArray.add(data)
        }
        graph.startRunningGraph()

        fragmentCameraBinding.recyclerviewResults.layoutManager =
            LinearLayoutManager(requireContext())
        fragmentCameraBinding.recyclerviewResults.adapter = adapter

        signLanguageHelper = SignLanguageHelper(requireContext())
        signLanguageHelper.createInterpreter(object :
            SignLanguageHelper.SignLanguageListener {
            override fun onResult(results: List<Pair<String, Float>>) {
//                Log.d(">>>","done")
                activity?.runOnUiThread {
                    adapter.updateResults(results)
                }
            }
        })
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
                random = al[i++]
                if (i >= al.size) {
                    for (j in al.indices.reversed()) {
                        al2.add(al[j])
                    }
                    al.clear()
                    i = 0
                }
            } else {
                random = al2[i++]
                if (i >= al2.size) {
                    for (j in al2.indices.reversed()) {
                        al.add(al2[j])
                    }
                    al2.clear()
                    i = 0
                }
            }
            fragmentCameraBinding.btnRecording.animateRadius(
                fragmentCameraBinding.btnRecording.getmMaxRadius(),
                random.toFloat()
            )
            handler?.postDelayed(runnable!!, 130)
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
//                        recognizeHand(image)
                        if (isRecording) {
                            extractHolistic(image)
//                            Log.d(">>>", "video")
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
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    private var settingPopupVisibilityDuration: Long = 0
    private var currentAnimator: AnimatorSet? = null

    private fun startAnimationOfSquare() {
        settingPopupVisibilityDuration =
            resources.getInteger(android.R.integer.config_shortAnimTime)
                .toLong()
        if (currentAnimator != null) {
            currentAnimator?.cancel()
        }
        val finalBounds = Rect()
        val globalOffset = Point()
        fragmentCameraBinding.btnRecording.getGlobalVisibleRect(
            finalBounds,
            globalOffset
        )
        TransitionManager.beginDelayedTransition(
            fragmentCameraBinding.ivSquare, TransitionSet()
                .addTransition(ChangeBounds())
                .setDuration(settingPopupVisibilityDuration)
        )
        val params: ViewGroup.LayoutParams =
            fragmentCameraBinding.ivSquare.layoutParams
        params.height = dpToPx(40f)
        params.width = dpToPx(40f)
        fragmentCameraBinding.ivSquare.layoutParams = params
        val set = AnimatorSet()
        set.play(
            ObjectAnimator.ofFloat(
                fragmentCameraBinding.ivSquare,
                "radius",
                dpToPx(8f).toFloat()
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
            fragmentCameraBinding.ivSquare, TransitionSet()
                .addTransition(ChangeBounds())
                .setDuration(settingPopupVisibilityDuration)
        )
        val params: ViewGroup.LayoutParams =
            fragmentCameraBinding.ivSquare.layoutParams
        params.width = dpToPx(80f)
        params.height = dpToPx(80f)
        fragmentCameraBinding.ivSquare.layoutParams = params
        val set1 = AnimatorSet()
        set1.play(
            ObjectAnimator.ofFloat(
                fragmentCameraBinding.ivSquare,
                "radius",
                dpToPx(40f).toFloat()
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
            TypedValue.COMPLEX_UNIT_DIP,
            valueInDp,
            metrics
        ).toInt()
    }

    private fun resetAnimation() {
        i = 0
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
//        Log.d(">>>", "classificatiom")
        signLanguageHelper.runInterpreter(inputArray)
    }

    private fun extractHolistic(imageProxy: ImageProxy) {
//        Log.d(">>>", "extract")
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image since we only support front camera
            postScale(
                -1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat()
            )
        }

        // Rotate bitmap to match what our model expects
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        )

        val packet = packetCreator.createRgbImageFrame(rotatedBitmap)
        graph.addConsumablePacketToInputStream(
            MainActivity.INPUT_VIDEO_STREAM_NAME,
            packet,
            SystemClock.uptimeMillis()
        )
    }
}