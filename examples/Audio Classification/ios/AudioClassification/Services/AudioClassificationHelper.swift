// Copyright 2024 The TensorFlow Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import TensorFlowLite

/// Delegate to returns the classification results.
protocol AudioClassificationHelperDelegate {
  func onResultReceived(_ result: Result)
  func onError(_ error: Error)
}

fileprivate let errorDomain = "org.tensorflow.lite.examples"

/// Stores results for a particular audio snipprt that was successfully classified.

struct Category {
  let label: String
  let probability: Float32
}
struct Result {
  let inferenceTime: Double
  let categories: [Category]
}

/// Information about a model file.
typealias FileInfo = (name: String, extension: String)

/// This class handles all data preprocessing and makes calls to run inference on a audio snippet
/// by invoking the Task Library's `AudioClassifier`.
class AudioClassificationHelper {

  // MARK: Public properties
  var delegate: AudioClassificationHelperDelegate?

  // MARK: Private properties
  /// Sample rate for input sound buffer. Caution: generally this value is a bit less than 1 second's audio sample.
  private(set) var sampleRate = 0
  /// Lable names described in the lable file
  private(set) var labelNames: [String] = []
  private var interpreter: Interpreter!

  private var model: Model
  private var threadCount: Int
  private var maxResults: Int
  private var scoreThreshold: Float
  private let audioBufferInputTensorIndex: Int = 0

  /// A timer to schedule classification routine to run periodically.
  private var timer: Timer?
  
  /// A queue to offload the classification routine to a background thread.
  private let processQueue = DispatchQueue(label: "processQueue")

  // MARK: - Initialization

  /// A failable initializer for `AudioClassificationHelper`. A new instance is created if the model
  /// is successfully loaded from the app's main bundle.
  init(model: Model, threadCount: Int, scoreThreshold: Float, maxResults: Int) {

    self.threadCount = threadCount
    self.maxResults = maxResults
    self.scoreThreshold = scoreThreshold
    self.model = model

    setupInterpreter()
  }

  private func setupInterpreter() {
    guard let modelPath = model.modelPath else {
      fatalError("can not load model path")
    }
    do {
      var options = Interpreter.Options()
      options.threadCount = threadCount
      interpreter = try Interpreter(modelPath: modelPath, options: options)

      try interpreter.allocateTensors()
      let inputShape = try interpreter.input(at: 0).shape
      switch model {
      case .Yamnet:
        sampleRate = inputShape.dimensions[0]
      case .speechCommand:
        sampleRate = inputShape.dimensions[1]
      }
      try interpreter.invoke()

      labelNames = loadLabels()
    } catch {
      fatalError("Failed to create the interpreter with error: \(error.localizedDescription)")
    }
  }

  private func loadLabels() -> [String] {
    guard let labelPath = model.labelPath else { return [] }

    var content = ""
    do {
      content = try String(contentsOfFile: labelPath, encoding: .utf8)
      let labels = content.components(separatedBy: "\n")
        .filter { !$0.isEmpty }
      return labels
    } catch {
      print("Failed to load label content: '\(content)' with error: \(error.localizedDescription)")
      return []
    }
  }

  /// Invokes the `Interpreter` and processes and returns the inference results.
  func start(inputBuffer: [Int16]) {
    let outputTensor: Tensor
    let startTime = Date().timeIntervalSince1970
    do {
      let audioBufferData = int16ArrayToData(inputBuffer)
      try interpreter.copy(audioBufferData, toInputAt: audioBufferInputTensorIndex)
      try interpreter.invoke()
      outputTensor = try interpreter.output(at: 0)
    } catch let error {
      print(">>> Failed to invoke the interpreter with error: \(error.localizedDescription)")
      return
    }
    let inferenceTime = Date().timeIntervalSince1970 - startTime
    // Gets the formatted and averaged results.
    let probabilities = dataToFloatArray(outputTensor.data) ?? []
    let categories: [Category] = Array(probabilities.enumerated().map { (index, probability) in
      return Category(label: labelNames[index], probability: probability)
    }.filter({ $0.probability >= scoreThreshold })
      .sorted(by: {$0.probability > $1.probability}).prefix(maxResults))
    DispatchQueue.main.async {
      self.delegate?.onResultReceived(Result(inferenceTime: inferenceTime, categories: categories))
    }
  }

  /// Creates a new buffer by copying the buffer pointer of the given `Int16` array.
  private func int16ArrayToData(_ buffer: [Int16]) -> Data {
    let floatData = buffer.map { Float($0) / Float(Int16.max) }
    return floatData.withUnsafeBufferPointer(Data.init)
  }

  /// Creates a new array from the bytes of the given unsafe data.
  /// - Returns: `nil` if `unsafeData.count` is not a multiple of `MemoryLayout<Float>.stride`.
  private func dataToFloatArray(_ data: Data) -> [Float]? {
    guard data.count % MemoryLayout<Float>.stride == 0 else { return nil }

    return data.withUnsafeBytes { .init($0.bindMemory(to: Float.self)) }
  }
}

