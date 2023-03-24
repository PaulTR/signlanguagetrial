package com.mediapipe.example.sign_language

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SignLanguageHelper(private val context: Context) {
    private lateinit var interpreter: Interpreter
    private lateinit var associatedAxisLabels: List<String>
    private lateinit var listener: SignLanguageListener
    fun createInterpreter(listener: SignLanguageListener) {
        interpreter = Interpreter(
            loadModelFile("sign_language.tflite", context),
            Interpreter.Options()
        )

        associatedAxisLabels =
            FileUtil.loadLabels(context, "sign_language_labels.txt");

        this.listener = listener
    }

    fun runInterpreter(list: MutableList<Array<FloatArray>>) {

        // prepare input
        val inputs = HashMap<String, Any>()
        inputs["inputs"] = list.toTypedArray()

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
//        "${associatedAxisLabels[indexOne]} (${
//            String.format(
//                "%.2f",
//                order[0]
//            )
//        })",
//        "${associatedAxisLabels[indexTwo]} (${
//            String.format(
//                "%.2f",
//                order[1]
//            )
//        })}",
//        "${associatedAxisLabels[indexThree]} (${
//            String.format(
//                "%.2f",
//                order[2]
//            )
//        })}"
//        Log.d(">>>>: result 1", "${associatedAxisLabels[indexOne]} ${order[0]}")
//        Log.d(">>>>: result 2", "${associatedAxisLabels[indexTwo]} ${order[1]}")
//        Log.d(
//            ">>>>: result 3",
//            "${associatedAxisLabels[indexThree]} ${order[2]}"
//        )
    }

    fun close() {
        interpreter.close()
    }

    private fun loadModelFile(
        path: String,
        context: Context
    ): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
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