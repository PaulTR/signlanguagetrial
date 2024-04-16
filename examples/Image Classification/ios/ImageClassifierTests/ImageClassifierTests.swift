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

import XCTest
@testable import ImageClassifier
import TensorFlowLiteTaskVision

final class ImageClassifierTests: XCTestCase {

  static let modeFileInfo = DefaultConstants.modelInfo

  static let scoreThreshold: Float = 0.01
  static let maxResult: Int = 3
  
  static let testImage = UIImage(
    named: "cup.png",
    in:Bundle(for: ImageClassifierTests.self),
    compatibleWith: nil)!

  static let results: [ClassificationCategory] = [
    ClassificationCategory(index: 505, score: 0.7265625, label: nil, displayName: nil),
    ClassificationCategory(index: 900, score: 0.140625, label: nil, displayName: nil),
    ClassificationCategory(index: 850, score: 0.0703125, label: nil, displayName: nil)

  ]

  func imageClassifierWithModelFileInfo(
    _ model: FileInfo,
    scoreThreshold: Float,
    maxResult: Int
  ) throws -> ImageClassifierService {
    let imageClassifierService = ImageClassifierService(
      model: model,
      scoreThreshold: scoreThreshold,
      maxResult: maxResult)
    return imageClassifierService!
  }

  func assertImageClassifierResultHasOneHead(
    _ imageClassifierResult: ClassificationResult
  ) {
    XCTAssertEqual(imageClassifierResult.classifications.count, 1)
    XCTAssertEqual(imageClassifierResult.classifications[0].headIndex, 0)
  }

  func assertCategoriesAreEqual(
    category: ClassificationCategory,
    expectedCategory: ClassificationCategory,
    indexInCategoryList: Int
  ) {
    XCTAssertEqual(
      category.index,
      expectedCategory.index,
      String(
        format: """
              category[%d].index and expectedCategory[%d].index are not equal.
              """, indexInCategoryList))
    XCTAssertEqual(
      category.score,
      expectedCategory.score,
      accuracy: 1e-3,
      String(
        format: """
              category[%d].score and expectedCategory[%d].score are not equal.
              """, indexInCategoryList))
  }

  func assertEqualCategoryArrays(
    categoryArray: [ClassificationCategory],
    expectedCategoryArray: [ClassificationCategory]
  ) {
    for c in categoryArray {
      print(c.index)
      print(c.score)
    }
    XCTAssertEqual(
      categoryArray.count,
      expectedCategoryArray.count)
    for (index, (category, expectedCategory)) in zip(categoryArray, expectedCategoryArray)
      .enumerated()
    {
      assertCategoriesAreEqual(
        category: category,
        expectedCategory: expectedCategory,
        indexInCategoryList: index)
    }
  }

  func assertResultsForClassify(
    image: UIImage,
    using imageClassifier: ImageClassifierService,
    equals expectedCategories: [ClassificationCategory]
  ) throws {
    let imageClassifierResult =
    try XCTUnwrap(
      imageClassifier.classify(image: image)!.imageClassifierResults[0])
    print(imageClassifierResult)
    assertImageClassifierResultHasOneHead(imageClassifierResult)
    assertEqualCategoryArrays(
      categoryArray:
        imageClassifierResult.classifications[0].categories,
      expectedCategoryArray: expectedCategories)
  }
  func testClassifySucceeds() throws {

    let imageClassifier = try imageClassifierWithModelFileInfo(
      ImageClassifierTests.modeFileInfo,
      scoreThreshold: ImageClassifierTests.scoreThreshold,
      maxResult: ImageClassifierTests.maxResult)
    try assertResultsForClassify(
      image: ImageClassifierTests.testImage,
      using: imageClassifier,
      equals: ImageClassifierTests.results)
  }
}
