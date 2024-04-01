package com.mediapipe.example.sign_language.fragment

import com.mediapipe.example.sign_language.HolisticLandmarkerHelper

data class HelperState(
    val minFacePresenceConfidence: Float = HolisticLandmarkerHelper.DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE,
    val minHandLandmarkConfidence: Float = HolisticLandmarkerHelper.DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE,
    val minPosePresenceConfidence: Float = HolisticLandmarkerHelper.DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE,
    val minFaceDetectionConfidence: Float = HolisticLandmarkerHelper.DEFAULT_MIN_FACE_DETECTION_CONFIDENCE,
    val minPoseDetectionConfidence: Float = HolisticLandmarkerHelper.DEFAULT_MIN_POSE_DETECTION_CONFIDENCE,
    val minPoseSuppressionThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD,
    val minFaceSuppressionThreshold: Float = HolisticLandmarkerHelper.DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD,
)
