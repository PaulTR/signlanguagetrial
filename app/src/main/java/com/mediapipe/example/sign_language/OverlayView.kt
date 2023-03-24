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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.nio.ByteBuffer

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    val listPoints: MutableList<Pair<Float, Float>> = mutableListOf()
    var bitmap: Bitmap? = null
    val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap!!, 0f, 0f, null)
        }
        listPoints.forEach {
            canvas.drawCircle(it.first, it.second, 5f, paint)
        }
    }

    fun setResults(
        list: Array<FloatArray>,
        outputWidth: Int,
        outputHeight: Int,
        bitmap: Bitmap
    ) {
        listPoints.clear()

        for (i in 0 until 543) {
            val x = list[0][i] * outputWidth
            val y = list[1][i] * outputHeight
            listPoints.add(Pair(x, y))
        }
        this.bitmap = bitmap

        invalidate()
    }
}
