package com.mediapipe.example.sign_language;

public final class HandLandmark {
    public static final int LEFT_HANDLANDMARK_INDEX = 468;
    public static final int RIGHT_HANDLANDMARK_INDEX = 522;
    public static final int WRIST = 0;
    public static final int THUMB_CMC = 1;
    public static final int THUMB_MCP = 2;
    public static final int THUMB_IP = 3;
    public static final int THUMB_TIP = 4;
    public static final int INDEX_FINGER_MCP = 5;
    public static final int INDEX_FINGER_PIP = 6;
    public static final int INDEX_FINGER_DIP = 7;
    public static final int INDEX_FINGER_TIP = 8;
    public static final int MIDDLE_FINGER_MCP = 9;
    public static final int MIDDLE_FINGER_PIP = 10;
    public static final int MIDDLE_FINGER_DIP = 11;
    public static final int MIDDLE_FINGER_TIP = 12;
    public static final int RING_FINGER_MCP = 13;
    public static final int RING_FINGER_PIP = 14;
    public static final int RING_FINGER_DIP = 15;
    public static final int RING_FINGER_TIP = 16;
    public static final int PINKY_MCP = 17;
    public static final int PINKY_PIP = 18;
    public static final int PINKY_DIP = 19;
    public static final int PINKY_TIP = 20;

    private HandLandmark() {
    }

    public @interface HandLandmarkType {
    }
}

