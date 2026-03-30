package com.swapna.camera2sample

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object CameraPermissionHelper {
    private const val PERMISSION_REQUEST_CODE = 100

    private val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private val AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

    fun hasAllPermissions(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun requestAllPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(CAMERA_PERMISSION, AUDIO_PERMISSION),
            PERMISSION_REQUEST_CODE
        )
    }

    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(CAMERA_PERMISSION), PERMISSION_REQUEST_CODE)
    }

    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)
    }

    fun launchPermissionSettings(activity: Activity) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }

    fun isAllPermissionsGranted(grantResults: IntArray): Boolean {
        return grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }
}
