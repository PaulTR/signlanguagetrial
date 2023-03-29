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
import android.view.View

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    private val listPoints: MutableList<Pair<Float, Float>> = mutableListOf()
    private var leftHand: FloatArray? = null
    private var rightHand: FloatArray? = null
    private val dotPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 10f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        // draw left hand and right hand
        leftHand?.let {
            for (i in it.indices step 2) {
                canvas.drawCircle(it[i], it[i + 1], 5f, dotPaint)
            }
            canvas.drawLines(it, linePaint)
        }
        rightHand?.let {
            for (i in it.indices step 2) {
                canvas.drawCircle(it[i], it[i + 1], 5f, dotPaint)
            }
            canvas.drawLines(it, linePaint)
        }
    }

    fun setResults(
        landMarkPoints: Array<FloatArray>,
        outputWidth: Int,
        outputHeight: Int
    ) {
        leftHand = null
        rightHand = null
        // extract left hand and right hand positions in image.
        if (!landMarkPoints[468][0].isNaN() && !landMarkPoints[468][1].isNaN()) {
            for (i in 468 until 468 + 21) {
                leftHand = FloatArray(21 * 2)
                leftHand!![i * 2] = landMarkPoints[i][0] * outputWidth
                leftHand!![i * 2 + 1] = landMarkPoints[i][1] * outputHeight
            }
        }
        if (!landMarkPoints[522][0].isNaN() && !landMarkPoints[522][1].isNaN()) {
            for (i in 522 until 543) {
                rightHand = FloatArray(21 * 2)
                rightHand!![i * 2] = landMarkPoints[i][0] * outputWidth
                rightHand!![i * 2 + 1] = landMarkPoints[i][1] * outputHeight
            }
        }
        invalidate()
    }
}
