package com.jdcr.jdcrfile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import com.jdcr.jdcrpermission.JdcrPermission


object JdcrFilePermissionUtils {

    @SuppressLint("NewApi")
    fun checkPermissionAll(): Boolean {
        return Environment.isExternalStorageManager()
    }

    fun checkExternal(context: FragmentActivity) {
        JdcrPermission.with(context).permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
            .request {

            }
    }

    fun openSettings(context: Context) {
        val intent: Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        context.startActivity(intent)
    }

    fun requestTree(context: Context) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

}