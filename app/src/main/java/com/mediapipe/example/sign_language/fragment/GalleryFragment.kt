package com.mediapipe.example.sign_language.fragment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.mediapipe.example.sign_language.LandmarkIndex
import com.mediapipe.example.sign_language.HolisticLandmarkerHelper
import com.mediapipe.example.sign_language.ResultsAdapter
import com.mediapipe.example.sign_language.SignLanguageHelper
import com.mediapipe.example.sign_language.databinding.FragmentGalleryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class GalleryFragment : Fragment() {
    private var backgroundScope: CoroutineScope? = null
    private var inputArray: MutableList<Array<FloatArray>> = mutableListOf()
    private lateinit var holisticLandmarkerHelper: HolisticLandmarkerHelper
    private var timePerFrame = SystemClock.uptimeMillis()
    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                runHolisticOnVideo(uri)
                with(fragmentGalleryBinding.videoView) {
                    stopPlayback()
                    setVideoURI(uri)
                    setOnPreparedListener { it.setVolume(0f, 0f) }
                    requestFocus()
                }
            } else {
                Log.d("VideoPicker", "No media selected")
            }
        }

    private lateinit var signLanguageHelper: SignLanguageHelper
    private val adapter: ResultsAdapter by lazy {
        ResultsAdapter()
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // allow mediapipe access asset.
        fragmentGalleryBinding.btnPick.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        fragmentGalleryBinding.recyclerview.layoutManager =
            LinearLayoutManager(requireContext())
        fragmentGalleryBinding.recyclerview.adapter = adapter

        signLanguageHelper = SignLanguageHelper(requireContext())
        signLanguageHelper.createInterpreter(object :
            SignLanguageHelper.SignLanguageListener {
            override fun onResult(results: List<Pair<String, Float>>) {
                runBlocking {
                    withContext(Dispatchers.Main) {
                        fragmentGalleryBinding.progress.visibility = View.GONE
                        adapter.updateResults(results)
                        fragmentGalleryBinding.videoView.start()
                    }
                }
            }
        })
        holisticLandmarkerHelper = HolisticLandmarkerHelper(RunningMode.VIDEO,
            requireContext(),
            object : HolisticLandmarkerHelper.LandmarkerListener {
                override fun onError(error: String, errorCode: Int) {
                    Log.e(
                        "TAG",
                        "HolisticLandmarkerHelper error: $error, code: $errorCode"
                    )
                }

                override fun onResults(resultBundle: HolisticLandmarkerHelper.ResultBundle) {

                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        holisticLandmarkerHelper.clearHolisticLandmarker()
        signLanguageHelper.close()
    }

    private fun runHolisticOnVideo(uri: Uri) {
        inputArray.clear()
        fragmentGalleryBinding.progress.visibility = View.VISIBLE
        backgroundScope = CoroutineScope(Dispatchers.IO)
        val cacheDir = requireActivity().cacheDir
        val folder = File(cacheDir.absolutePath + "/sign_frames/")
        // delete folder and all child images if it is exist and create a new one
        if (folder.exists()) {
            folder.deleteRecursively()
            folder.mkdirs()
        } else {

            // create folder
            folder.mkdirs()
        }
        backgroundScope?.launch {
            // extract all images from video and save it to cache
            val inputVideoPath =
                FFmpegKitConfig.getSafParameterForRead(requireContext(), uri)
            FFmpegKit.execute("-i " + inputVideoPath + " -vf fps=30 " + cacheDir.absolutePath + "/sign_frames/" + "%04d.jpg")
            // load all images from cache and convert it bitmap. also run holistic on each image.
            if (folder.exists()) {
                val allFiles =
                    folder.listFiles { pathname -> pathname?.name?.endsWith(".jpg") == true }

                // convert and run holistic
                allFiles?.forEachIndexed { index, file ->
                    file.toBitmap()?.let { bitmap ->
                        timePerFrame += 400 * index
                        addNewFrame(bitmap, timePerFrame)
                    }
                }
            }
            // run interpreter
            signLanguageHelper.runInterpreter(inputArray)
        }
    }

    private fun addNewFrame(bitmap: Bitmap, timeStamp: Long) {

        val result = holisticLandmarkerHelper.detectVideoFile(bitmap, timeStamp)
        // 543 landmarks (33 pose landmarks, 468 face landmarks, and 21 hand landmarks per hand)
        val data = Array(543) { FloatArray(3) { Float.NaN } }
        result?.faceLandmarks()?.forEachIndexed { index, normalizedLandmark ->
                if (index < LandmarkIndex.LEFT_HANDLANDMARK_INDEX) {
                    data[index + LandmarkIndex.FACE_LANDMARK_INDEX][0] =
                        normalizedLandmark.x()
                    data[index + LandmarkIndex.FACE_LANDMARK_INDEX][1] =
                        normalizedLandmark.y()
                    data[index + LandmarkIndex.FACE_LANDMARK_INDEX][2] =
                        normalizedLandmark.z()
                }
            }
        result?.leftHandLandmarks()
            ?.forEachIndexed { index, normalizedLandmark ->
                data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][0] =
                    normalizedLandmark.x()
                data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][1] =
                    normalizedLandmark.y()
                data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][2] =
                    normalizedLandmark.z()
            }
        result?.poseLandmarks()?.forEachIndexed { index, normalizedLandmark ->
                data[index + LandmarkIndex.POSE_LANDMARK_INDEX][0] =
                    normalizedLandmark.x()
                data[index + LandmarkIndex.POSE_LANDMARK_INDEX][1] =
                    normalizedLandmark.y()
                data[index + LandmarkIndex.POSE_LANDMARK_INDEX][2] =
                    normalizedLandmark.z()
            }
        result?.rightHandLandmarks()
            ?.forEachIndexed { index, normalizedLandmark ->
                data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][0] =
                    normalizedLandmark.x()
                data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][1] =
                    normalizedLandmark.y()
                data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][2] =
                    normalizedLandmark.z()
            }
        inputArray.add(data)
    }
}

fun File.toBitmap(): Bitmap? {
    return BitmapFactory.decodeFile(path)
}
