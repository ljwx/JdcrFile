package com.jdcr.jdcrfile

import android.content.Context
import android.os.Environment
import com.jdcr.jdcrfile.util.JdcrFileLog
import java.io.File

object JdcrFileUtils {

    /**
     * 获取外置存储根目录
     *
     * @return /storage/emulated/0
     */
    fun getExternalStorageDir(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    /**
     * 获取外置存储App文件目录
     *
     * @return /storage/emulated/0/Android/data/com.jdcr.jdcrfile/files
     */
    fun getExternalFile(context: Context) {
        context.getExternalFilesDirs(null).forEach {
            JdcrFileLog.i("外部文件位置:" + it.path)
        }
    }

    fun getSystemCameraPath(): String {
        return getExternalStorageDir() + "/DCIM" + "/Camera"
    }

    fun test() {
        val dir = getSystemCameraPath()
        val file = File(dir)
        file.listFiles()?.forEach {
            JdcrFileLog.i("媒体:"+it.name)
        }
    }

}