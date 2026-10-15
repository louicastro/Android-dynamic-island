package com.dynamicisland.android.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.dynamicisland.android.data.SettingsRepository
import com.dynamicisland.android.service.IslandOverlayService
import com.dynamicisland.android.ui.theme.DynamicIslandTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: SettingsRepository

    private val phonePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            restartIslandService()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                restartIslandService()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository.getInstance(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            DynamicIslandTheme {
                SettingsScreen(
                    repository = repository,
                    onRequestOverlayPermission = {
                        PermissionHelper.openOverlaySettings(this)
                    },
                    onRequestNotificationPermission = {
                        PermissionHelper.openNotificationListenerSettings(this)
                    },
                    onRequestPhonePermission = {
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            arrayOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.ANSWER_PHONE_CALLS
                            )
                        } else {
                            arrayOf(Manifest.permission.READ_PHONE_STATE)
                        }
                        phonePermissionsLauncher.launch(permissions)
                    },
                    onRestartService = {
                        restartIslandService()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val settings = repository.getSettings()
        if (settings.isEnabled && PermissionHelper.hasOverlayPermission(this)) {
            startIslandService()
        }
    }

    private fun startIslandService() {
        val intent = Intent(this, IslandOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopIslandService() {
        val intent = Intent(this, IslandOverlayService::class.java)
        stopService(intent)
    }

    private fun restartIslandService() {
        stopIslandService()
        val settings = repository.getSettings()
        if (settings.isEnabled && PermissionHelper.hasOverlayPermission(this)) {
            startIslandService()
        }
    }
}
