package com.mediapipe.example.sign_language

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mediapipe.example.sign_language.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)
    }
}
