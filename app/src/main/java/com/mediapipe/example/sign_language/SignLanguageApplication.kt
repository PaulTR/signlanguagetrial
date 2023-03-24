package com.mediapipe.example.sign_language

import android.app.Application

class SignLanguageApplication : Application() {
    // load mediapipe library
    init {
        System.loadLibrary("mediapipe_jni")
        System.loadLibrary("opencv_java3")
    }
}
