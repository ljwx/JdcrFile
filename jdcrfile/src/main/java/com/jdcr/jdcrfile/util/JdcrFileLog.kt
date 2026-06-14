package com.jdcr.jdcrfile.util

import com.jdcr.jdcrlog.JdcrLogBase

internal object JdcrFileLog : JdcrLogBase() {
    init {
        setDefaultTag("file")
    }
}