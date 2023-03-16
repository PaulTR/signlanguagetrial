// Copyright 2019 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.apps.signlanguage;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import com.google.mediapipe.formats.proto.ClassificationProto.ClassificationList;
import com.google.mediapipe.formats.proto.ClassificationProto.ClassificationListCollection;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.TextView;

public class MainActivity extends com.google.mediapipe.apps.basic.MainActivity {
  private static final String TAG = "MainActivity";

  private static final String OUTPUT_CLASSIFICATION_STREAM_NAME = "classifications";
  private String label = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ApplicationInfo applicationInfo;
    try {
      applicationInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
    } catch (NameNotFoundException e) {
      throw new AssertionError(e);
    }

    AndroidPacketCreator packetCreator = processor.getPacketCreator();
    TextView tvLabels = (TextView) findViewById(R.id.tv_labels);

    // To show verbose logging, run:
    // adb shell setprop log.tag.MainActivity VERBOSE
    // if (Log.isLoggable(TAG, Log.VERBOSE)) {
    processor.addPacketCallback(
        OUTPUT_CLASSIFICATION_STREAM_NAME,
        (packet) -> {
          // Log.v("xxxx>>>", "Received multi-hand landmarks packet.");
          // List<ClassificationList> multiHandLandmarks =
          // PacketGetter.getProtoVector(packet, ClassificationList.parser());

          byte[] protoBytes = PacketGetter.getProtoBytes(packet);
          try {
            ClassificationList landmarkList = ClassificationList.parseFrom(protoBytes);
            // Log.v("xxxx>>>", "[TS:" + packet.getTimestamp() + "] " +
            // landmarkList.getClassification(0));
            // if (landmarkList.size() > 0) {
              label = landmarkList.getClassification(0).getLabel();

              this.runOnUiThread(new Runnable() {
                public void run() {
                  tvLabels.setText("Labels: " + label);
                }
              });
            // }
          } catch (java.lang.Exception e) {
            e.printStackTrace();
          }
        });
    // }
  }
}
