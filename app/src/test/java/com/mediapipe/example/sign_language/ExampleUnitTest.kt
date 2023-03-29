package com.mediapipe.example.sign_language

import org.junit.Test

import org.junit.Assert.*
import kotlin.math.max
import kotlin.math.min

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val test = listOf(
            Double.NaN,
            Double.NaN,
            4,
            5,
            6,
            Double.NaN,
            4,
            5,
            6,
            2,
            1
        )
        testList(test as List<Double>)
    }

    fun testList(list: List<Double>) {
        var newList = list
        var firstFrame = -1
        var endFrame = -1
        list.forEachIndexed { index, floatArrays ->
            if (!floatArrays.isNaN() || !floatArrays.isNaN()) {
                if (firstFrame == -1) {
                    firstFrame = index
                } else {
                    endFrame = index
                }
            }
        }

        if (firstFrame != -1 && endFrame != -1) {
            newList = list.subList(firstFrame, endFrame + 1)
        }
        if (newList.size<10){
            list
        }
        assertEquals(2, firstFrame)
        assertEquals(10, endFrame)
    }

    @Test
    fun abc(){
        var lastFrame = 42
        var firstFrame = 35

        if (lastFrame - firstFrame < 10) {
            firstFrame = min(lastFrame - 10, firstFrame)
            lastFrame = max(firstFrame + 10, lastFrame)
        }

        assertEquals(firstFrame, 0)
    }


    @Test
    fun sample(){
        var lastFrame = 40
        var firstFrame = 35
        var listSize = 42
        if (firstFrame != -1 && lastFrame != -1) {
           val a =  firstFrame >= 0 && (lastFrame + 1 <= listSize)
            assertEquals(true,a)
        }
    }
}