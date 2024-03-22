package com.mediapipe.example.sign_language

import android.content.Context
import com.mediapipe.example.sign_language.LandmarkIndex.LEFT_HANDLANDMARK_INDEX
import com.mediapipe.example.sign_language.LandmarkIndex.RIGHT_HANDLANDMARK_INDEX
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class SignLanguageHelper(private val context: Context) {
    private lateinit var interpreter: Interpreter
    private lateinit var associatedAxisLabels: List<String>
    private lateinit var listener: SignLanguageListener
    fun createInterpreter(listener: SignLanguageListener) {
        interpreter = Interpreter(
            loadModelFile(context),
            Interpreter.Options()
        )

        associatedAxisLabels =
            FileUtil.loadLabels(context, "sign_language_labels.txt")

        this.listener = listener
    }

    fun runInterpreter(landmarks: MutableList<Array<FloatArray>>) {
        // trim the input
        if (landmarks.isEmpty()) return
        val minFrame = 10
        var firstFrame = -1
        var lastFrame = -1

        // check first and last occurrences of hand landmarks
        landmarks.forEachIndexed { index, floatArrays ->
            if (!floatArrays[LEFT_HANDLANDMARK_INDEX][0].isNaN() || !floatArrays[RIGHT_HANDLANDMARK_INDEX][0].isNaN()) {
                if (firstFrame == -1) {
                    firstFrame = index
                }
                lastFrame = index
            }
        }
        val trimLandMark: MutableList<Array<FloatArray>>
        if (lastFrame - firstFrame >= minFrame) {
            trimLandMark = landmarks.subList(firstFrame, lastFrame + 1)
        } else {
            firstFrame = min(lastFrame - (minFrame / 2), firstFrame)
            lastFrame = max(firstFrame + (minFrame / 2), lastFrame)
            if (firstFrame < 0) {
                firstFrame = 0
            }
            if (lastFrame < 0 || lastFrame + 1 >= landmarks.size) {
                lastFrame = landmarks.size
            }
            trimLandMark = landmarks.subList(firstFrame, lastFrame)
        }

        val inputs = HashMap<String, Any>()
        // prepare input
        inputs["inputs"] = trimLandMark.toTypedArray()
        // prepare output
        val output = FloatArray(250)
        val outPutMap = HashMap<String, Any>()
        outPutMap["outputs"] = output
        interpreter.runSignature(inputs, outPutMap, "serving_default")
        val order = output.sortedByDescending { it }.take(3)

        val indexOne = output.indexOfFirst { it == order[0] }
        val indexTwo = output.indexOfFirst { it == order[1] }
        val indexThree = output.indexOfFirst { it == order[2] }
        this.listener.onResult(
            listOf(
                Pair(associatedAxisLabels[indexOne], order[0]),
                Pair(associatedAxisLabels[indexTwo], order[1]),
                Pair(associatedAxisLabels[indexThree], order[2]),
            )
        )
    }

    fun close() {
        interpreter.close()
    }

    private fun loadModelFile(
        context: Context
    ): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("sign_language.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    interface SignLanguageListener {
        fun onResult(results: List<Pair<String, Float>>)
    }
}
