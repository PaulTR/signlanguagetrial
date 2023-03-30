package com.mediapipe.example.sign_language.fragment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
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
import com.google.mediapipe.components.FrameProcessor
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.framework.AndroidPacketCreator
import com.google.mediapipe.framework.Graph
import com.google.mediapipe.framework.PacketGetter
import com.google.mediapipe.glutil.EglManager
import com.mediapipe.example.sign_language.MainActivity
import com.mediapipe.example.sign_language.ResultsAdapter
import com.mediapipe.example.sign_language.SignLanguageHelper
import com.mediapipe.example.sign_language.databinding.FragmentGalleryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.locks.*
import kotlin.concurrent.withLock

class GalleryFragment : Fragment() {
    private var backgroundScope: CoroutineScope? = null
    private lateinit var graph: Graph
    private lateinit var packetCreator: AndroidPacketCreator
    private var inputArray: MutableList<Array<FloatArray>> = mutableListOf()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
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
                Log.d("PhotoPicker", "No media selected")
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
        val eglManager = EglManager(null)
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
            lock.withLock { condition.signal() }
        }
        graph.startRunningGraph()
    }

    override fun onDestroy() {
        super.onDestroy()
        graph.cancelGraph()
        signLanguageHelper.close()
    }

//        private fun runHolisticOnVideo(uri: Uri) {
//        inputArray.clear()
//        fragmentGalleryBinding.progress.visibility = View.VISIBLE
//        backgroundScope = CoroutineScope(Dispatchers.IO)
//        backgroundScope?.launch {
//
//            // Load frames from the video.
//            val retriever = MediaMetadataRetriever()
//            retriever.setDataSource(requireContext(), uri)
//            val videoLengthMs =
//                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
//                    ?.toLong()
//
//            // Note: We need to read width/height from frame instead of getting the width/height
//            // of the video directly because MediaRetriever returns frames that are smaller than the
//            // actual dimension of the video file.
//            val firstFrame = retriever.getFrameAtTime(0)
//            val width = firstFrame?.width
//            val height = firstFrame?.height
//
//            // If the video is invalid, returns a null
//            if ((videoLengthMs == null) || (width == null) || (height == null)) return@launch
//
//            // Next, we'll get one frame every frameInterval ms
//            val numberOfFrameToRead =
//                videoLengthMs.div(MainActivity.VIDEO_INTERVAL_MS)
//            var time = SystemClock.uptimeMillis()
//            var frameCount = 0
//
//            for (i in 0..numberOfFrameToRead) {
//                val timestampMs = i * MainActivity.VIDEO_INTERVAL_MS // ms
//                retriever.getFrameAtTime(
//                    timestampMs * 1000, // convert from ms to micro-s
//                    MediaMetadataRetriever.OPTION_CLOSEST
//                )?.let { frame ->
//                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
//                    val argb8888Frame =
//                        if (frame.config == Bitmap.Config.ARGB_8888) frame
//                        else frame.copy(Bitmap.Config.ARGB_8888, false)
//
//                    // Convert the input Bitmap object to an MPImage object to run inference
//                    addNewFrame(argb8888Frame, SystemClock.uptimeMillis())
//                    frameCount++
//                    lock.withLock {
//                        condition.await()
//                    }
//                }
//            }
//            time = SystemClock.uptimeMillis() - time
//            retriever.release()
//            // run interpreter
//            signLanguageHelper.runInterpreter(inputArray)
//        }
//    }

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
                allFiles?.forEach {
                    it.toBitmap()?.let { bitmap ->
                        timePerFrame += 400
                        addNewFrame(bitmap, timePerFrame)
                        lock.withLock {
                            condition.await()
                        }
                    }
                }
            }
            // run interpreter
            signLanguageHelper.runInterpreter(inputArray)
        }
    }

    private fun addNewFrame(bitmap: Bitmap, timeStamp: Long) {
        val packet = packetCreator.createRgbImageFrame(bitmap)
        graph.addConsumablePacketToInputStream(
            MainActivity.INPUT_VIDEO_STREAM_NAME, packet, timeStamp
        )
        packet.release()
    }
}

fun File.toBitmap(): Bitmap? {
    return BitmapFactory.decodeFile(path)
}
