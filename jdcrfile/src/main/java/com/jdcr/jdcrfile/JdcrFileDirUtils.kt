package com.jdcr.jdcrfile

import android.content.Context
import com.jdcr.jdcrfile.util.JdcrFileLog

object JdcrFileDirUtils {

    fun getRootDir(context: Context) {
        context.getExternalFilesDirs(null).forEach {
            JdcrFileLog.i(it.path)
        }
    }

    fun getChildDir(parentPath: String) {

    }

}