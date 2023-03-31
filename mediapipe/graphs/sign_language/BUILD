load(
    "//mediapipe/framework/tool:mediapipe_graph.bzl",
    "mediapipe_binary_graph",
    "mediapipe_simple_subgraph",
)

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

mediapipe_simple_subgraph(
    name = "holistic_tracking_to_render_data",
    graph = "holistic_tracking_to_render_data.pbtxt",
    register_as = "HolisticTrackingToRenderData",
    deps = [
        "//mediapipe/calculators/core:concatenate_proto_list_calculator",
        "//mediapipe/calculators/core:concatenate_vector_calculator",
        "//mediapipe/calculators/core:merge_calculator",
        "//mediapipe/calculators/core:split_proto_list_calculator",
        "//mediapipe/calculators/core:split_vector_calculator",
        "//mediapipe/calculators/util:detections_to_render_data_calculator",
        "//mediapipe/calculators/util:landmarks_to_render_data_calculator",
        "//mediapipe/calculators/util:rect_to_render_data_calculator",
        "//mediapipe/calculators/util:rect_to_render_scale_calculator",
        "//mediapipe/modules/holistic_landmark:hand_wrist_for_pose",
    ],
)

# cc_library(
#     name = "sign_language_gpu_deps",
#     deps = [
#         ":holistic_tracking_to_render_data",
#         "//mediapipe/calculators/core:flow_limiter_calculator",
#         "//mediapipe/calculators/core:prepare_sign_language_model_input",
#         "//mediapipe/calculators/image:image_properties_calculator",
#         "//mediapipe/calculators/util:annotation_overlay_calculator",
#         "//mediapipe/modules/holistic_landmark:holistic_landmark_gpu",
#         "//mediapipe/calculators/tflite:tflite_inference_calculator",
#         "//mediapipe/calculators/tflite:tflite_converter_calculator",
#         "//mediapipe/calculators/tflite:tflite_tensors_to_classification_calculator",
#     ],
# )

mediapipe_binary_graph(
    name = "sign_language_cpu",
    graph = "sign_language_cpu.pbtxt",
    output_name = "sign_language_cpu.binarypb",
    deps = [":sign_language_cpu_deps"],
)

cc_library(
    name = "sign_language_cpu_deps",
    deps = [
        ":holistic_tracking_to_render_data",
        "//mediapipe/calculators/core:prepare_sign_language_model_input",
        "//mediapipe/calculators/core:flow_limiter_calculator",
        "//mediapipe/calculators/image:image_properties_calculator",
        "//mediapipe/modules/holistic_landmark:holistic_landmark_cpu",
    ],
)
