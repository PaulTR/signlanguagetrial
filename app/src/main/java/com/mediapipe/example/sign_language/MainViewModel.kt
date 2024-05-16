package com.mediapipe.example.sign_language/*
 * Copyright 2024 The TensorFlow Authors. All Rights Reserved.
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

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.common.collect.ImmutableList
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import com.mediapipe.example.sign_language.helper.HolisticLandMarkerHelper
import com.mediapipe.example.sign_language.helper.SignLanguageHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val holisticLandMarkerHelper: HolisticLandMarkerHelper,
    private val signLanguageHelper: SignLanguageHelper,
) : ViewModel() {
    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val holisticLandMarkerHelper = HolisticLandMarkerHelper(
                    context = context,
                )
                val signLanguageHelper = SignLanguageHelper(context)
                return MainViewModel(holisticLandMarkerHelper, signLanguageHelper) as T
            }
        }
    }


    private val _classification = MutableStateFlow<List<ClassificationResult>>(listOf()).apply {
        onEach {
            inputArray.clear()
        }
    }

    private val _threshold = MutableStateFlow(Threshold())
    private val inputArray: MutableList<Array<FloatArray>> = mutableListOf()

    init {
        signLanguageHelper.createInterpreter()

        viewModelScope.launch {
            _threshold
                .map {
                    holisticLandMarkerHelper.apply {
                        minFacePresenceConfidence = it.minFacePresenceConfidence
                        minFaceDetectionConfidence = it.minFaceDetectionConfidence
                        minFaceSuppressionThreshold = it.minFaceSuppressionThreshold
                        minHandLandmarksConfidence = it.minHandLandmarkConfidence
                        minPoseSuppressionThreshold = it.minPoseSuppressionThreshold
                        minPosePresenceConfidence = it.minPosePresenceConfidence
                        minPoseDetectionConfidence = it.minPoseDetectionConfidence
                    }
                }
                .flatMapLatest {
                    it.setUpHolisticLandMarker()
                }
                .filter { it.isSuccess }
                .map { it.getOrThrow() }
                .filterNotNull()
                .map {
                    inputArray.add(processData(it))
                    inputArray
                }
                .flatMapConcat {
                    signLanguageHelper.runInterpreter(it)
                }.map { list ->
                    list.map { pair ->
                        ClassificationResult(label = pair.first, value = pair.second)
                    }
                }
                .collect(_classification)
        }
    }

    val uiState: StateFlow<UiState> = combine(_threshold, _classification) { threshold, results ->
        UiState(threshold = threshold, results = ImmutableList.copyOf(results))
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = UiState()
    )

    fun setMinHandLandmarkConfidence(confidence: Float) {
        _threshold.update { it.copy(minHandLandmarkConfidence = confidence) }
    }

    fun setMinFaceDetectionConfidence(confidence: Float) {
        _threshold.update { it.copy(minFaceDetectionConfidence = confidence) }
    }

    fun setMinPoseDetectionConfidence(confidence: Float) {
        _threshold.update { it.copy(minPoseDetectionConfidence = confidence) }
    }

    fun setMinFaceSuppressionThreshold(threshold: Float) {
        _threshold.update { it.copy(minFaceSuppressionThreshold = threshold) }
    }

    fun setMinPoseSuppressionThreshold(threshold: Float) {
        _threshold.update { it.copy(minPoseSuppressionThreshold = threshold) }
    }

    fun setMinFacePresenceConfidence(confidence: Float) {
        _threshold.update {
            val a = it.copy(minFacePresenceConfidence = confidence)
            Log.i("xxx", "setMinFacePresenceConfidence: $a")
            a
        }
    }

    fun setMinPosePresenceConfidence(confidence: Float) {
        _threshold.update { it.copy(minPosePresenceConfidence = confidence) }
    }

    fun detectLiveStreamImage(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        holisticLandMarkerHelper.detectLiveStreamImage(imageProxy, isFrontCamera)
    }

    fun clearInputData() {
        inputArray.clear()
    }

    private fun processData(result: HolisticLandmarkerResult): Array<FloatArray> {
        val data = Array(543) { FloatArray(3) { Float.NaN } }
        result.faceLandmarks().forEachIndexed { index, normalizedLandmark ->
            if (index < LandmarkIndex.LEFT_HANDLANDMARK_INDEX) {
                data[index + LandmarkIndex.FACE_LANDMARK_INDEX][0] = normalizedLandmark.x()
                data[index + LandmarkIndex.FACE_LANDMARK_INDEX][1] = normalizedLandmark.y()
                data[index + LandmarkIndex.FACE_LANDMARK_INDEX][2] = normalizedLandmark.z()
            }
        }
        result.leftHandLandmarks().forEachIndexed { index, normalizedLandmark ->
            data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][0] = normalizedLandmark.x()
            data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][1] = normalizedLandmark.y()
            data[index + LandmarkIndex.LEFT_HANDLANDMARK_INDEX][2] = normalizedLandmark.z()
        }
        result.poseLandmarks().forEachIndexed { index, normalizedLandmark ->
            data[index + LandmarkIndex.POSE_LANDMARK_INDEX][0] = normalizedLandmark.x()
            data[index + LandmarkIndex.POSE_LANDMARK_INDEX][1] = normalizedLandmark.y()
            data[index + LandmarkIndex.POSE_LANDMARK_INDEX][2] = normalizedLandmark.z()
        }
        result.rightHandLandmarks().forEachIndexed { index, normalizedLandmark ->
            data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][0] = normalizedLandmark.x()
            data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][1] = normalizedLandmark.y()
            data[index + LandmarkIndex.RIGHT_HANDLANDMARK_INDEX][2] = normalizedLandmark.z()
        }
        return data
    }

    override fun onCleared() {
        super.onCleared()
        holisticLandMarkerHelper.clearHolisticLandMarker()
        signLanguageHelper.close()
    }
}
