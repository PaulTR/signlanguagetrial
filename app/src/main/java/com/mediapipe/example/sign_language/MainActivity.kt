package com.mediapipe.example.sign_language

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.framework.AndroidPacketCreator
import com.google.mediapipe.framework.Graph
import com.google.mediapipe.framework.PacketGetter
import com.google.mediapipe.glutil.EglManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.*
import kotlin.concurrent.withLock
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BINARY_GRAPH_NAME = "sign_language_cpu.binarypb"
        private const val INPUT_VIDEO_STREAM_NAME = "input_video"
        private const val OUTPUT_VIDEO_STREAM_NAME = "sign_language_matrix"
        private const val VIDEO_INTERVAL_MS = 33L
    }

    private var backgroundScope: CoroutineScope? = null
    private lateinit var graph: Graph
    private lateinit var packetCreator: AndroidPacketCreator
    private var inputArray: MutableList<Array<FloatArray>> = mutableListOf()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()
    val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Callback is invoked after the user selects a media item or closes the
            // photo picker.
            if (uri != null) {
                runHolisticOnVideo(uri)
                with(videoView) {
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
    private lateinit var progress: FrameLayout
    private val adapter: ResultsAdapter by lazy {
        ResultsAdapter()
    }
    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // allow mediapipe access asset.
        AndroidAssetUtil.initializeNativeAssetManager(this)
        val eglManager = EglManager(null)
        setContentView(R.layout.activity_main)
        val btn = findViewById<FloatingActionButton>(R.id.btnPick)
        btn.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
        val recyclerview = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerview.layoutManager = LinearLayoutManager(this)
        recyclerview.adapter = adapter
        videoView = findViewById(R.id.videoView)
        progress = findViewById(R.id.progress)
        signLanguageHelper = SignLanguageHelper(this)
        signLanguageHelper.createInterpreter(object :
            SignLanguageHelper.SignLanguageListener {
            override fun onResult(results: List<Pair<String, Float>>) {
                runBlocking {
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                        adapter.updateResults(results)
                        videoView.start()
                    }
                }
            }
        })

        graph = Graph()
        graph.loadBinaryGraph(
            AndroidAssetUtil.getAssetBytes(
                assets,
                BINARY_GRAPH_NAME
            )
        )
        packetCreator = AndroidPacketCreator(graph)
        graph.setParentGlContext(eglManager.nativeContext)
        graph.addPacketCallback(OUTPUT_VIDEO_STREAM_NAME) { packet ->
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

    private fun runHolisticOnVideo(uri: Uri) {
        inputArray.clear()
        progress.visibility = View.VISIBLE
        backgroundScope = CoroutineScope(Dispatchers.IO)
        backgroundScope?.launch {

            // Load frames from the video.
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this@MainActivity, uri)
            val videoLengthMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLong()

            // Note: We need to read width/height from frame instead of getting the width/height
            // of the video directly because MediaRetriever returns frames that are smaller than the
            // actual dimension of the video file.
            val firstFrame = retriever.getFrameAtTime(0)
            val width = firstFrame?.width
            val height = firstFrame?.height

            // If the video is invalid, returns a null
            if ((videoLengthMs == null) || (width == null) || (height == null)) return@launch

            // Next, we'll get one frame every frameInterval ms
            val numberOfFrameToRead = videoLengthMs.div(VIDEO_INTERVAL_MS)
            var time = SystemClock.uptimeMillis()
            var frameCount = 0

            for (i in 0..numberOfFrameToRead) {
                val timestampMs = i * VIDEO_INTERVAL_MS // ms
                retriever.getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s
                    MediaMetadataRetriever.OPTION_CLOSEST
                )?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    // Convert the input Bitmap object to an MPImage object to run inference
                    addNewFrame(argb8888Frame, SystemClock.uptimeMillis())
                    frameCount++
                    lock.withLock {
                        condition.await()
                    }
                }
            }
            time = SystemClock.uptimeMillis() - time
            Log.d("time consume", "$time   $frameCount")
            retriever.release()
            // run interpreter
            signLanguageHelper.runInterpreter(inputArray)
        }
    }

    private fun addNewFrame(bitmap: Bitmap, timeStamp: Long) {
        val packet = packetCreator.createRgbImageFrame(bitmap)
        graph.addConsumablePacketToInputStream(
            INPUT_VIDEO_STREAM_NAME, packet, timeStamp
        )
    }
}
