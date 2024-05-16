package com.mediapipe.example.sign_language

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList
import com.mediapipe.example.sign_language.helper.HolisticLandMarkerHelper

@Immutable
data class UiState(
    val threshold: Threshold = Threshold(),
    val results: ImmutableList<ClassificationResult> = ImmutableList.of()
)

@Immutable
data class Threshold(
    val minFacePresenceConfidence: Float = HolisticLandMarkerHelper.DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE,
    val minHandLandmarkConfidence: Float = HolisticLandMarkerHelper.DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE,
    val minPosePresenceConfidence: Float = HolisticLandMarkerHelper.DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE,
    val minFaceDetectionConfidence: Float = HolisticLandMarkerHelper.DEFAULT_MIN_FACE_DETECTION_CONFIDENCE,
    val minPoseDetectionConfidence: Float = HolisticLandMarkerHelper.DEFAULT_MIN_POSE_DETECTION_CONFIDENCE,
    val minPoseSuppressionThreshold: Float = HolisticLandMarkerHelper.DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD,
    val minFaceSuppressionThreshold: Float = HolisticLandMarkerHelper.DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD,
)

@Immutable
data class ClassificationResult(val label: String, val value: Float)
