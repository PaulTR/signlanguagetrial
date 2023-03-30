/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mediapipe.example.sign_language

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.mediapipe.example.sign_language.HandLandmark.LEFT_HANDLANDMARK_INDEX
import com.mediapipe.example.sign_language.HandLandmark.RIGHT_HANDLANDMARK_INDEX
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    //    private var leftHand: FloatArray? = null
//    private var rightHand: FloatArray? = null
    private val dotPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 10f
    }

    private var scaleFactor: Float = 1f
    private var landMarkPoints: Array<FloatArray> = emptyArray()
    private var outputWidth: Float = 1f
    private var outputHeight: Float = 1f


    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        // draw left hand and right hand
//        leftHand?.let { landmark ->
//            val lines = mutableListOf<Float>()
//            for (i in landmark.indices step 2) {
//                canvas.drawCircle(landmark[i], landmark[i + 1], 5f, dotPaint)
//            }
//            for (i in landmarkConnections.indices) {
//                val startX =
//                    landmark[landmarkConnections[i]*2]
//                val startY =
//                    landmark[landmarkConnections[i]*2 + 1]
//                lines.add(startX)
//                lines.add(startY)
//            }
//            canvas.drawLines(lines.toFloatArray(), linePaint)
//            Log.d(">>>>", "draw left")
//        }
        // check if landmark have enough points
        if (landMarkPoints.size == 543) {
            if (!landMarkPoints[LEFT_HANDLANDMARK_INDEX][0].isNaN() && !landMarkPoints[LEFT_HANDLANDMARK_INDEX][1].isNaN()) {
                val leftHand = FloatArray(21 * 2)
                for (i in LEFT_HANDLANDMARK_INDEX until LEFT_HANDLANDMARK_INDEX + 21) {
                    val newIdx = i - LEFT_HANDLANDMARK_INDEX
                    val x = landMarkPoints[i][0] * outputWidth * scaleFactor
                    val y = landMarkPoints[i][1] * outputHeight * scaleFactor
                    leftHand[newIdx * 2] = x
                    leftHand[newIdx * 2 + 1] = y
                    canvas.drawCircle(x, y, 5f, dotPaint)
                }
                val lines = mutableListOf<Float>()
                for (i in landmarkConnections.indices) {
                    val startX =
                        leftHand[landmarkConnections[i] * 2]
                    val startY =
                        leftHand[landmarkConnections[i] * 2 + 1]
                    lines.add(startX)
                    lines.add(startY)
                }
                canvas.drawLines(lines.toFloatArray(), linePaint)
            }
            if (!landMarkPoints[RIGHT_HANDLANDMARK_INDEX][0].isNaN() && !landMarkPoints[RIGHT_HANDLANDMARK_INDEX][1].isNaN()) {
                val rightHand = FloatArray(21 * 2)
                for (i in RIGHT_HANDLANDMARK_INDEX until 543) {
                    val newIdx = i - RIGHT_HANDLANDMARK_INDEX
                    val x = landMarkPoints[i][0] * outputWidth * scaleFactor
                    val y = landMarkPoints[i][1] * outputHeight * scaleFactor
                    rightHand[newIdx * 2] = x
                    rightHand[newIdx * 2 + 1] = y
                    canvas.drawCircle(x, y, 5f, dotPaint)
                }
                val lines = mutableListOf<Float>()
                for (i in landmarkConnections.indices) {
                    val startX =
                        rightHand[landmarkConnections[i] * 2]
                    val startY =
                        rightHand[landmarkConnections[i] * 2 + 1]
                    lines.add(startX)
                    lines.add(startY)
                }
                canvas.drawLines(lines.toFloatArray(), linePaint)
            }
        }
//        rightHand?.let { landmark ->
//            val lines = mutableListOf<Float>()
//            for (i in landmark.indices step 2) {
//                canvas.drawCircle(landmark[i], landmark[i + 1], 8f, dotPaint)
//            }
//            for (i in landmarkConnections.indices) {
//                val startX =
//                    landmark[landmarkConnections[i] * 2]
//                val startY =
//                    landmark[landmarkConnections[i] * 2 + 1]
//                lines.add(startX)
//                lines.add(startY)
//            }
//            canvas.drawLines(lines.toFloatArray(), linePaint)
//            Log.d(">>>>", "draw right")
//        }
    }

    fun setResults(
        landMarkPoints: Array<FloatArray>,
        outputWidth: Int,
        outputHeight: Int
    ) {
//        Log.d(">>>>", "receive draw")
        if (landMarkPoints.isEmpty() && this.landMarkPoints.isEmpty()) {
            return
        }
        scaleFactor = max(width * 1f / outputWidth, height * 1f / outputHeight)
        this.landMarkPoints = landMarkPoints
        this.outputWidth = outputWidth.toFloat()
        this.outputHeight = outputHeight.toFloat()
//        leftHand = null
//        rightHand = null
//        // extract left hand and right hand positions in image.
//        if (!landMarkPoints[LEFT_HANDLANDMARK_INDEX][0].isNaN() && !landMarkPoints[LEFT_HANDLANDMARK_INDEX][1].isNaN()) {
//            Log.d(">>>>", "extract left")
//            leftHand = FloatArray(21 * 2)
//            for (i in LEFT_HANDLANDMARK_INDEX until LEFT_HANDLANDMARK_INDEX + 21) {
//                val newIdx = i - LEFT_HANDLANDMARK_INDEX
//                leftHand!![newIdx * 2] =
//                    landMarkPoints[i][0] * outputWidth.toFloat() * scaleFactor
//                leftHand!![newIdx * 2 + 1] =
//                    landMarkPoints[i][1] * outputHeight.toFloat() * scaleFactor
//            }
//        } else {
//            Log.d(">>>>", "no hand left")
//        }
//        if (!landMarkPoints[RIGHT_HANDLANDMARK_INDEX][0].isNaN() && !landMarkPoints[RIGHT_HANDLANDMARK_INDEX][1].isNaN()) {
//            Log.d(">>>>", "extract right")
//            rightHand = FloatArray(21 * 2)
//            for (i in RIGHT_HANDLANDMARK_INDEX until 543) {
//                val newIdx = i - RIGHT_HANDLANDMARK_INDEX
//                rightHand!![newIdx * 2] =
//                    landMarkPoints[i][0] * outputWidth.toFloat() * scaleFactor
//                rightHand!![newIdx * 2 + 1] =
//                    landMarkPoints[i][1] * outputHeight.toFloat() * scaleFactor
//            }
//        } else {
//            Log.d(">>>>", "no hand right")
//        }
        invalidate()
    }

    companion object {
        private val landmarkConnections = listOf(
            HandLandmark.WRIST,
            HandLandmark.THUMB_CMC,
            HandLandmark.THUMB_CMC,
            HandLandmark.THUMB_MCP,
            HandLandmark.THUMB_MCP,
            HandLandmark.THUMB_IP,
            HandLandmark.THUMB_IP,
            HandLandmark.THUMB_TIP,
            HandLandmark.WRIST,
            HandLandmark.INDEX_FINGER_MCP,
            HandLandmark.INDEX_FINGER_MCP,
            HandLandmark.INDEX_FINGER_PIP,
            HandLandmark.INDEX_FINGER_PIP,
            HandLandmark.INDEX_FINGER_DIP,
            HandLandmark.INDEX_FINGER_DIP,
            HandLandmark.INDEX_FINGER_TIP,
            HandLandmark.INDEX_FINGER_MCP,
            HandLandmark.MIDDLE_FINGER_MCP,
            HandLandmark.MIDDLE_FINGER_MCP,
            HandLandmark.MIDDLE_FINGER_PIP,
            HandLandmark.MIDDLE_FINGER_PIP,
            HandLandmark.MIDDLE_FINGER_DIP,
            HandLandmark.MIDDLE_FINGER_DIP,
            HandLandmark.MIDDLE_FINGER_TIP,
            HandLandmark.MIDDLE_FINGER_MCP,
            HandLandmark.RING_FINGER_MCP,
            HandLandmark.RING_FINGER_MCP,
            HandLandmark.RING_FINGER_PIP,
            HandLandmark.RING_FINGER_PIP,
            HandLandmark.RING_FINGER_DIP,
            HandLandmark.RING_FINGER_DIP,
            HandLandmark.RING_FINGER_TIP,
            HandLandmark.RING_FINGER_MCP,
            HandLandmark.PINKY_MCP,
            HandLandmark.WRIST,
            HandLandmark.PINKY_MCP,
            HandLandmark.PINKY_MCP,
            HandLandmark.PINKY_PIP,
            HandLandmark.PINKY_PIP,
            HandLandmark.PINKY_DIP,
            HandLandmark.PINKY_DIP,
            HandLandmark.PINKY_TIP
        )
    }
}
