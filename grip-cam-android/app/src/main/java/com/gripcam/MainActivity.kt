package com.gripcam

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.gripcam.ui.GripCamApp
import com.gripcam.ui.GripCamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            private var bucket = -1

            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val next = when (orientation) {
                    in 45 until 135 -> 90
                    in 135 until 225 -> 180
                    in 225 until 315 -> 270
                    else -> 0
                }
                if (next != bucket) {
                    bucket = next
                    viewModel.onDeviceOrientation(next)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A grip session often means the phone is on a stick or tripod, out of reach.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GripCamTheme {
                GripCamApp(viewModel)
            }
        }
    }

    // Volume buttons (and Bluetooth selfie remotes, which send volume-up) act as configured.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && viewModel.onVolumeKey(keyCode == KeyEvent.KEYCODE_VOLUME_UP, true, event.repeatCount)) return true
        if (keyCode == KeyEvent.KEYCODE_CAMERA && event.repeatCount == 0) {
            viewModel.shutterDown()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && viewModel.onVolumeKey(keyCode == KeyEvent.KEYCODE_VOLUME_UP, false, 0)) return true
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            viewModel.shutterUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    override fun onStart() {
        super.onStart()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
    }

    override fun onStop() {
        orientationListener.disable()
        super.onStop()
    }
}
