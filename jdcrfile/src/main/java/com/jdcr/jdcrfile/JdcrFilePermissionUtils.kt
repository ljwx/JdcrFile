package com.jdcr.jdcrfile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jdcr.jdcrfile.util.JdcrFileLog
import com.jdcr.jdcrpermission.JdcrPermission
import com.jdcr.jdcrpermission.handler.JdcrOpenActionHandler


object JdcrFilePermissionUtils {

    /**
     * 检查是否已经拥有最高存储权限
     */
    fun hasMaxStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上，检查是否拥有 "所有文件访问" 权限
            Environment.isExternalStorageManager()
                .apply { JdcrFileLog.i("是否有所有文件权限:$this") }
        } else {
            // Android 10 及以下，检查传统读写权限
            val read = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val write = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            (read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED).apply {
                JdcrFileLog.i(
                    "是否有读写文件权限:$this"
                )
            }
        }
    }

    /**
     * 发起最高权限请求
     * 注意：这里需要你传入处理回调的 Launcher，或者在 Activity 中直接调用
     */
    fun requestMaxStoragePermission(
        context: FragmentActivity,
        callback: () -> Unit
    ) {
        if (hasMaxStoragePermission(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+：跳转到系统设置的 "所有文件访问权限" 页面
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                JdcrOpenActionHandler(context, context.activityResultRegistry, intent, callback).start()
            } catch (e: Exception) {
                // 极少数魔改车机可能没有这个具体的页面，退级到通用所有文件管理页
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                JdcrOpenActionHandler(context, context.activityResultRegistry, intent, callback).start()
            }
        } else {
            // Android 10 及以下：直接申请传统权限
            val legacyPermissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            JdcrPermission.with(context).permissions(legacyPermissions.toList()).request {
                callback()
            }
        }
    }

}