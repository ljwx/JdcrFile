package com.jdcr.jdcrfile.util

import com.jdcr.jdcrlog.JdcrLogBase

object JdcrFileLog : JdcrLogBase() {
    init {
        setDefaultTag("file")
    }
}