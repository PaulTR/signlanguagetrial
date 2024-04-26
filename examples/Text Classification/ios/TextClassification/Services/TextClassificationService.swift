// Copyright 2024 The TensorFlow Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// =============================================================================

import TensorFlowLiteTaskText

class TextClassificationService {

  var tFLNLClassifier: TFLNLClassifier?
  var tFLBertNLClassifier: TFLBertNLClassifier?

  var usingModel: Model

  init(model: Model) {
    usingModel = model
    switch model {
    case .mobileBert:
      guard let modelPath = model.modelPath else { return }
      tFLBertNLClassifier = TFLBertNLClassifier.bertNLClassifier(modelPath: modelPath)
    case .avgWordClassifier:
      guard let modelPath = model.modelPath else { return }
      let options = TFLNLClassifierOptions()
      tFLNLClassifier = TFLNLClassifier.nlClassifier(modelPath: modelPath, options: options)
    }
  }

  func classify(text: String) -> ClassificationResult? {
    let startTime = Date().timeIntervalSince1970
    switch usingModel {
    case .mobileBert:
      guard let result = tFLBertNLClassifier?.classify(text: text) else { return nil }
      return ClassificationResult(inferenceTime: 1000 * (Date().timeIntervalSince1970 - startTime),
                                  categories: result)
    case .avgWordClassifier:
      guard let result = tFLNLClassifier?.classify(text: text) else { return nil }
      let newResult: [String: NSNumber] = ["positive": result["1"] ?? 0, "negative": result["0"] ?? 0]
      return ClassificationResult(inferenceTime: 1000 * (Date().timeIntervalSince1970 - startTime),
                                  categories: newResult)
    }
  }
}

enum Model: String, CaseIterable {
  case mobileBert = "Mobile Bert"
  case avgWordClassifier = "Avg Word Classifier"

  var modelPath: String? {
    switch self {
    case .mobileBert:
      return Bundle.main.path(
        forResource: "bert_classifier", ofType: "tflite")
    case .avgWordClassifier:
      return Bundle.main.path(
        forResource: "average_word_classifier", ofType: "tflite")
    }
  }
}

struct ClassificationResult {
  let inferenceTime: Double
  let categories: [String: NSNumber]
}
