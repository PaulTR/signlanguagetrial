package com.mediapipe.example.sign_language

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.framework.AndroidAssetUtil
import com.mediapipe.example.sign_language.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val BINARY_GRAPH_NAME = "sign_language_cpu.binarypb"
        const val INPUT_VIDEO_STREAM_NAME = "input_video"
        const val OUTPUT_VIDEO_STREAM_NAME = "sign_language_matrix"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // allow mediapipe access asset.
        AndroidAssetUtil.initializeNativeAssetManager(this)
        val activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        activityMainBinding.navigation.setupWithNavController(navController)
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }
    }
}
