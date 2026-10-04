package com.ubaid.hostt

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ubaid.hostt.ui.dashboard.DashboardScreen
import com.ubaid.hostt.ui.setup.SetupScreen
import com.ubaid.hostt.ui.theme.UbaidTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.setScreenCaptureResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra(EXTRA_PROMPT_SCREEN_CAPTURE, false) == true) {
            launchScreenCapturePermission()
        }

        setContent {
            UbaidTheme {
                val uiState by viewModel.uiState.collectAsState()
                val logs by viewModel.activityLogs.collectAsState()

                if (!uiState.isSetupCompleted) {
                    SetupScreen(
                        uiState = uiState,
                        pairingPayloadJson = viewModel.getPairingPayloadJson(),
                        onRequestScreenCapture = { launchScreenCapturePermission() },
                        onCompleteSetup = { viewModel.completeSetup() }
                    )
                } else {
                    DashboardScreen(
                        uiState = uiState,
                        activityLogs = logs,
                        pairingPayloadJson = viewModel.getPairingPayloadJson(),
                        onToggleService = { viewModel.toggleHostService() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionStates()
    }

    private fun launchScreenCapturePermission() {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    companion object {
        const val EXTRA_PROMPT_SCREEN_CAPTURE = "extra_prompt_screen_capture"
    }
}
