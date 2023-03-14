#include "mediapipe/framework/calculator_framework.h"
#include "mediapipe/framework/formats/landmark.pb.h"
#include "mediapipe/framework/formats/matrix.h"
#include <android/log.h>

// node {
//     calculator: "PrepareSignLanguageModelInput"
//     input_stream: "POSE_LANDMARKS:pose_landmarks"
//     input_stream: "LEFT_HAND_LANDMARKS:left_hand_landmarks"
//     input_stream: "RIGHT_HAND_LANDMARKS:right_hand_landmarks"
//     input_stream: "FACE_LANDMARKS:face_landmarks"
//     output_stream: "SIGN_LANGUAGE_LANDMARKS:sign_language_landmarks"
// }

namespace mediapipe
{

    class PrepareSignLanguageModelInputCalculator : public CalculatorBase
    {

    public:
        static absl::Status GetContract(CalculatorContract *cc)
        {
            cc->Inputs().Tag("POSE_LANDMARKS").Set<NormalizedLandmarkList>();
            cc->Inputs().Tag("LEFT_HAND_LANDMARKS").Set<NormalizedLandmarkList>();
            cc->Inputs().Tag("RIGHT_HAND_LANDMARKS").Set<NormalizedLandmarkList>();
            cc->Inputs().Tag("FACE_LANDMARKS").Set<NormalizedLandmarkList>();
            cc->Outputs().Tag("SIGN_LANGUAGE_MATRIX").Set<Matrix>();
            return absl::OkStatus();
        }

        absl::Status Open(CalculatorContext *cc) final
        {
            return absl::OkStatus();
        }

        absl::Status Process(CalculatorContext *cc) final
        {
            // check if Face landmarks is empty
            if (cc->Inputs().Tag("POSE_LANDMARKS").IsEmpty() || cc->Inputs().Tag("LEFT_HAND_LANDMARKS").IsEmpty() || cc->Inputs().Tag("RIGHT_HAND_LANDMARKS").IsEmpty() || cc->Inputs().Tag("FACE_LANDMARKS").IsEmpty())
            {
                __android_log_print(ANDROID_LOG_INFO, "sign_language %d ", "stop");
                return absl::OkStatus();
            }

            // FACE + LEFT_HAND  + POSE + RIGHT_HAND. REQUIRE 543.
            auto matrix = Matrix(3, 543);

            // FACE LANDMARK
            const NormalizedLandmarkList &face_landmarks = cc->Inputs().Tag("FACE_LANDMARKS").Get<NormalizedLandmarkList>();
            int face_landmarks_size = face_landmarks.landmark_size();

            for (int i = 0; i < face_landmarks_size; ++i)
            {
                const auto &landmark = face_landmarks.landmark(i);
                matrix(0, i) = landmark.x();
                matrix(1, i) = landmark.y();
                matrix(2, i) = landmark.z();
            }

            std::string s = std::to_string(face_landmarks_size);
            char const *pchar = s.c_str();
            __android_log_print(ANDROID_LOG_INFO, "sign_language %d ", pchar);

            // LEFT_HAND

            const NormalizedLandmarkList &left_hand_landmarks = cc->Inputs().Tag("LEFT_HAND_LANDMARKS").Get<NormalizedLandmarkList>();
            int left_hand_landmarks_size = left_hand_landmarks.landmark_size();

            for (int i = 0; i < left_hand_landmarks_size; ++i)
            {
                const auto &landmark = left_hand_landmarks.landmark(i);
                matrix(0, i) = landmark.x();
                matrix(1, i) = landmark.y();
                matrix(2, i) = landmark.z();
            }

            // POSE

            const NormalizedLandmarkList &pose_landmarks = cc->Inputs().Tag("POSE_LANDMARKS").Get<NormalizedLandmarkList>();
            int pose_landmarks_size = pose_landmarks.landmark_size();

            for (int i = 0; i < pose_landmarks_size; ++i)
            {
                const auto &landmark = pose_landmarks.landmark(i);
                matrix(0, i) = landmark.x();
                matrix(1, i) = landmark.y();
                matrix(2, i) = landmark.z();
            }


            // RIGHT_HAND
            const NormalizedLandmarkList &right_hand_landmarks = cc->Inputs().Tag("POSE_LANDMARKS").Get<NormalizedLandmarkList>();
            int right_hand_landmarks_size = right_hand_landmarks.landmark_size();

            for (int i = 0; i < right_hand_landmarks_size; ++i)
            {
                const auto &landmark = right_hand_landmarks.landmark(i);
                matrix(0, i) = landmark.x();
                matrix(1, i) = landmark.y();
                matrix(2, i) = landmark.z();
            }

            // Output
            auto landmarks_matrix = std::make_unique<Matrix>();
            *landmarks_matrix = matrix;
            cc->Outputs().Tag("SIGN_LANGUAGE_MATRIX").Add(landmarks_matrix.release(), cc->InputTimestamp());
            return absl::OkStatus();
        }

        // absl::Status Close(CalculatorContext *cc)
        // {
        //     return absl::OkStatus();
        // }
        // public:
        //     static absl::Status GetContract(CalculatorContract *cc)
        //     {
        //         cc->Inputs().Tag("POSE_LANDMARKS").Set<LandmarkList>();
        //         cc->Inputs().Tag("LEFT_HAND_LANDMARKS").Set<LandmarkList>();
        //         cc->Inputs().Tag("RIGHT_HAND_LANDMARKS").Set<LandmarkList>();
        //         cc->Inputs().Tag("FACE_LANDMARKS").Set<LandmarkList>();

        //         // cc->Outputs().Tag("SIGN_LANGUAGE_LANDMARKS").Set<Matrix>();
        //         return absl::OkStatus();
        //     }

        //     absl::Status Process(CalculatorContract *cc)
        //     {
        //         const auto &input_frame = cc->Inputs().Tag("FACE_LANDMARKS").Set<LandmarkList>();
        //         __android_log_print(ANDROID_LOG_ERROR, "%d ", "input_frame");
        //         //  Output()->Add();
        //         return absl::OkStatus();
        //     }
        // };
    };
    REGISTER_CALCULATOR(PrepareSignLanguageModelInputCalculator);
}