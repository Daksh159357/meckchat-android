package com.meckchat.android.core

import android.util.Log

object Logger {
    private const val DEFAULT_TAG = "MeckChat"

    fun debug(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
    }

    fun info(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }

    fun warning(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
    }

    fun error(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
