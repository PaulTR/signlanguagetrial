/*
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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mediapipe.example.sign_language.fragment.HelperState

class CameraViewModel : ViewModel() {
    private val _helperState: MutableLiveData<HelperState> = MutableLiveData(
        HelperState()
    )
    val helperState: LiveData<HelperState> = _helperState


    fun setMinHandLandmarkConfidence(confidence: Float) {
        _helperState.value = _helperState.value?.copy(minHandLandmarkConfidence = confidence)
    }

    fun setMinFaceDetectionConfidence(confidence: Float) {
        _helperState.value = _helperState.value?.copy(minFaceDetectionConfidence = confidence)
    }

    fun setMinPoseDetectionConfidence(confidence: Float) {
        _helperState.value = _helperState.value?.copy(minPoseDetectionConfidence = confidence)
    }

    fun setMinFaceSuppressionThreshold(threshold: Float) {
        _helperState.value = _helperState.value?.copy(minFaceSuppressionThreshold = threshold)
    }

    fun setMinPoseSuppressionThreshold(threshold: Float) {
        _helperState.value = _helperState.value?.copy(minPoseSuppressionThreshold = threshold)
    }

    fun setMinFacePresenceConfidence(confidence: Float) {
        _helperState.value = _helperState.value?.copy(minFacePresenceConfidence = confidence)
    }

    fun setMinPosePresenceConfidence(confidence: Float) {
        _helperState.value = _helperState.value?.copy(minPosePresenceConfidence = confidence)
    }
}
