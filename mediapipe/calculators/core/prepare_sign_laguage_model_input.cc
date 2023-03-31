#include "mediapipe/framework/calculator_framework.h"
#include "mediapipe/framework/formats/landmark.pb.h"
#include "mediapipe/framework/formats/matrix.h"
#include <android/log.h>
#include <math.h>

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

    template <class LandmarkListT>
    absl::StatusOr<LandmarkListT> NormalizeLandmarkAspectRatio(
        const LandmarkListT &landmarks, float width, float height)
    {
        const float max_dim = std::max(width, height);
        if (max_dim <= 0)
        {
            return ::absl::InvalidArgumentError(
                absl::StrCat("Invalid image dimensions: [", width, ",", height, "]"));
        }
        const float width_scale_factor = width / max_dim;
        const float height_scale_factor = height / max_dim;
        LandmarkListT normalized_landmarks;
        for (int i = 0; i < landmarks.landmark_size(); ++i)
        {
            const auto &old_landmark = landmarks.landmark(i);
            auto *new_landmark = normalized_landmarks.add_landmark();
            new_landmark->set_x((old_landmark.x() - 0.5) * width_scale_factor + 0.5);
            new_landmark->set_y((old_landmark.y() - 0.5) * height_scale_factor + 0.5);
            new_landmark->set_z(old_landmark.z());
        }
        return normalized_landmarks;
    }

    class PrepareSignLanguageModelInputCalculator : public CalculatorBase
    {

    public:
        static absl::Status GetContract(CalculatorContract *cc)
        {
            cc->Inputs().Tag("IMAGE_SIZE").Set<std::pair<int, int>>();
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
            // if (cc->Inputs().Tag("POSE_LANDMARKS").IsEmpty() || cc->Inputs().Tag("LEFT_HAND_LANDMARKS").IsEmpty() || cc->Inputs().Tag("RIGHT_HAND_LANDMARKS").IsEmpty() || cc->Inputs().Tag("FACE_LANDMARKS").IsEmpty())
            // {
            //     __android_log_print(ANDROID_LOG_INFO, "sign_language %d ", "stop");
            //     return absl::OkStatus();
            // }

            // get width height of image
            const auto [width, height] = cc->Inputs().Tag("IMAGE_SIZE").Get<std::pair<int, int>>();

            // FACE + LEFT_HAND  + POSE + RIGHT_HAND. REQUIRE 543.
            auto matrix = Matrix(3, 543);

            // std::string zz = std::to_string( matrix.rows());
            // char const *gg = zz.c_str();
            // __android_log_print(ANDROID_LOG_INFO, "sign_language %d ", gg);

            int face_offset = 0;
            int left_hand_offset = 468;
            int pose_offset = left_hand_offset + 21;
            int right_hand_offset = pose_offset + 33;

            // FACE LANDMARK
            if (cc->Inputs().Tag("FACE_LANDMARKS").IsEmpty())
            {
                for (int i = 0; i < 468; ++i)
                {
                    matrix(0, i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(1, i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(2, i) = std::numeric_limits<double>::quiet_NaN();
                }
            }
            else
            {
                const NormalizedLandmarkList &face_landmarks = cc->Inputs().Tag("FACE_LANDMARKS").Get<NormalizedLandmarkList>();
                // auto new_face_landmark = NormalizeLandmarkAspectRatio(face_landmarks, width, height);
                int face_landmarks_size = face_landmarks.landmark_size();

                for (int i = 0; i < 468; ++i)
                {
                    const auto &landmark = face_landmarks.landmark(i);
                    matrix(0, i) = landmark.x();
                    matrix(1, i) = landmark.y();
                    matrix(2, i) = landmark.z();
                }
            }
            // std::string s_before = std::to_string(face_landmarks.landmark(10).x());
            // char const *pchar_before = s_before.c_str();
            // __android_log_print(ANDROID_LOG_INFO, "sign_language face size before %d ", pchar_before);

            // std::string s = std::to_string(new_face_landmark->landmark(10).x());
            // char const *pchar = s.c_str();
            // __android_log_print(ANDROID_LOG_INFO, "sign_language face size after %d ", pchar);

            // LEFT_HAND
            if (cc->Inputs().Tag("LEFT_HAND_LANDMARKS").IsEmpty())
            {
                for (int i = 0; i < 21; ++i)
                {
                    matrix(0, left_hand_offset + i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(1, left_hand_offset + i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(2, left_hand_offset + i) = std::numeric_limits<double>::quiet_NaN();
                }
            }
            else
            {
                const NormalizedLandmarkList &left_hand_landmarks = cc->Inputs().Tag("LEFT_HAND_LANDMARKS").Get<NormalizedLandmarkList>();
                // auto new_left_hand_landmark = NormalizeLandmarkAspectRatio(left_hand_landmarks, width, height);
                int left_hand_landmarks_size = left_hand_landmarks.landmark_size();

                for (int i = 0; i < 21; ++i)
                {
                    const auto &landmark = left_hand_landmarks.landmark(i);
                    matrix(0, left_hand_offset + i) = landmark.x();
                    matrix(1, left_hand_offset + i) = landmark.y();
                    matrix(2, left_hand_offset + i) = landmark.z();
                }
            }

            // std::string left_hand_s = std::to_string(new_left_hand_landmark->landmark(10).x());
            // char const *left_hand_char = left_hand_s.c_str();
            // __android_log_print(ANDROID_LOG_INFO, "sign_language left hand size %d ", left_hand_char);

            // POSE
            if (cc->Inputs().Tag("POSE_LANDMARKS").IsEmpty())
            {
                for (int i = 0; i < 33; ++i)
                {
                    matrix(0, pose_offset + i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(1, pose_offset + i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(2, pose_offset + i) = std::numeric_limits<double>::quiet_NaN();
                }
            }
            else
            {
                const NormalizedLandmarkList &pose_landmarks = cc->Inputs().Tag("POSE_LANDMARKS").Get<NormalizedLandmarkList>();
                // auto new_pose_landmark = NormalizeLandmarkAspectRatio(pose_landmarks, width, height);
                int pose_landmarks_size = pose_landmarks.landmark_size();

                for (int i = 0; i < 33; ++i)
                {
                    const auto &landmark = pose_landmarks.landmark(i);
                    matrix(0, pose_offset + i) = landmark.x();
                    matrix(1, pose_offset + i) = landmark.y();
                    matrix(2, pose_offset + i) = landmark.z();
                }
            }

            // std::string pose_s = std::to_string(new_pose_landmark->landmark(10).x());
            // char const *pose_char = pose_s.c_str();
            // __android_log_print(ANDROID_LOG_INFO, "sign_language pose size %d ", pose_char);

            // RIGHT_HAND
            if (cc->Inputs().Tag("RIGHT_HAND_LANDMARKS").IsEmpty())
            {
                 for (int i = 0; i < 21; ++i)
                {
                    matrix(0, right_hand_offset + i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(1, right_hand_offset + i) = std::numeric_limits<double>::quiet_NaN();
                    matrix(2, right_hand_offset + i) = std::numeric_limits<double>::quiet_NaN();
                }
            }
            else
            {
                const NormalizedLandmarkList &right_hand_landmarks = cc->Inputs().Tag("RIGHT_HAND_LANDMARKS").Get<NormalizedLandmarkList>();
                // auto new_right_hand_landmark = NormalizeLandmarkAspectRatio(right_hand_landmarks, width, height);
                int right_hand_landmarks_size = right_hand_landmarks.landmark_size();

                for (int i = 0; i < 21; ++i)
                {
                    const auto &landmark = right_hand_landmarks.landmark(i);
                    matrix(0, right_hand_offset + i) = landmark.x();
                    matrix(1, right_hand_offset + i) = landmark.y();
                    matrix(2, right_hand_offset + i) = landmark.z();
                }
            }

            // std::string right_hand_s = std::to_string(new_right_hand_landmark->landmark(10).x());
            // char const *right_hand_char = right_hand_s.c_str();
            // __android_log_print(ANDROID_LOG_INFO, "sign_language right hand size %d ", right_hand_char);

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