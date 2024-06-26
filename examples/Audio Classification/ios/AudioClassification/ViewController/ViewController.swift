// Copyright 2022 The TensorFlow Authors. All Rights Reserved.
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

import UIKit
import AVFoundation

class ViewController: UIViewController {

  // MARK: - Variables
  @IBOutlet weak var tableView: UITableView!
  @IBOutlet weak var inferenceView: InferenceView!

  private var audioClassificationHelper: AudioClassificationHelper!

  private var model: Model = DefaultConstants.model
  private var maxResults: Int = DefaultConstants.maxResults
  private var threshold: Float = DefaultConstants.threshold
  private var threadCount: Int = DefaultConstants.threadCount

  private var result: Result?
  private var audioInputManager: AudioInputManager!

  override func viewDidLoad() {
    super.viewDidLoad()
    inferenceView.delegate = self
    inferenceView.setDefault()
    restartClassifier()
  }

  // MARK: - Private Methods

  /// Initializes the AudioInputManager and starts recognizing on the output buffers.
  private func startAudioRecognition() {
    audioInputManager?.stop()
    audioInputManager = AudioInputManager(sampleRate: audioClassificationHelper.sampleRate)
    audioInputManager.delegate = self

    audioInputManager.checkPermissionsAndStartTappingMicrophone()
  }

  private func runModel(inputBuffer: [Int16]) {
    audioClassificationHelper.start(inputBuffer: inputBuffer)
  }

  /// Start a new audio classification routine.
  private func restartClassifier() {
    // Create a new classifier instance.
    audioClassificationHelper = AudioClassificationHelper(
      model: model,
      threadCount: threadCount,
      scoreThreshold: threshold,
      maxResults: maxResults)

    // Start the new classification routine.
    audioClassificationHelper.delegate = self
    startAudioRecognition()
  }
}

// MARK: extension implement show permission error
extension ViewController {
  private func showPermissionsErrorAlert() {
    let alertController = UIAlertController(
      title: "Microphone Permissions Denied",
      message: "Microphone permissions have been denied for this app. You can change this by going to Settings",
      preferredStyle: .alert
    )

    let cancelAction = UIAlertAction(title: "Cancel", style: .cancel, handler: nil)
    let settingsAction = UIAlertAction(title: "Settings", style: .default) { _ in
      UIApplication.shared.open(
        URL(string: UIApplication.openSettingsURLString)!,
        options: [:],
        completionHandler: nil
      )
    }
    alertController.addAction(cancelAction)
    alertController.addAction(settingsAction)

    present(alertController, animated: true, completion: nil)
  }
}

// MARK: UITableViewDataSource, UITableViewDelegate
extension ViewController: UITableViewDataSource, UITableViewDelegate {

  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    guard let cell = tableView.dequeueReusableCell(withIdentifier: "ResultCell") as? ResultTableViewCell else { fatalError() }
    guard let result = result else { return cell }
    cell.setData(result.categories[indexPath.row])
    return cell
  }

  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    guard let result = result else { return 0 }
    return result.categories.count
  }
}

// MARK: AudioClassificationHelperDelegate
extension ViewController: AudioClassificationHelperDelegate {
  func onResultReceived(_ result: Result) {
    self.result = result
    tableView.reloadData()
    inferenceView.inferenceTimeLabel.text = "\(Int(result.inferenceTime * 1000)) ms"
  }

  func onError(_ error: Error) {
    let errorMessage = "An error occured while running audio classification: \(error.localizedDescription)"
    let alert = UIAlertController(title: "Error", message: errorMessage, preferredStyle: UIAlertController.Style.alert)
    alert.addAction(UIAlertAction(title: "OK", style: UIAlertAction.Style.default, handler: nil))
    self.present(alert, animated: true, completion: nil)
  }
}

// MARK: AudioInputManagerDelegate
extension ViewController: AudioInputManagerDelegate {
  func audioInputManagerDidFailToAchievePermission(_ audioInputManager: AudioInputManager) {
    let alertController = UIAlertController(
      title: "Microphone Permissions Denied",
      message: "Microphone permissions have been denied for this app. You can change this by going to Settings",
      preferredStyle: .alert
    )

    let cancelAction = UIAlertAction(title: "Cancel", style: .cancel, handler: nil)
    let settingsAction = UIAlertAction(title: "Settings", style: .default) { _ in
      UIApplication.shared.open(
        URL(string: UIApplication.openSettingsURLString)!,
        options: [:],
        completionHandler: nil
      )
    }
    alertController.addAction(cancelAction)
    alertController.addAction(settingsAction)

    present(alertController, animated: true, completion: nil)
  }

  func audioInputManager(
    _ audioInputManager: AudioInputManager,
    didCaptureChannelData channelData: [Int16]
  ) {
    let sampleRate = audioClassificationHelper.sampleRate
    if channelData.count != sampleRate {
      print("audio data count is not equal sample rate")
      return
    }
    self.runModel(inputBuffer: channelData)
    print(channelData.count)
  }
}

// MARK: InferenceViewDelegate
extension ViewController: InferenceViewDelegate {
  func view(_ view: InferenceView, needPerformActions action: InferenceView.Action) {
    switch action {
    case .changeModel(let model):
      self.model = model
    case .changeMaxResults(let maxResults):
      self.maxResults = maxResults
    case .changeScoreThreshold(let threshold):
      self.threshold = threshold
    case .changeThreadCount(let threadCount):
      self.threadCount = threadCount
    }

    // Restart the audio classifier as the config as changed.
    restartClassifier()
  }
}
