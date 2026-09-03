package com.meckchat.android.core

import android.util.Log

object Logger {
    private const val DEFAULT_TAG = "MeckChat"

    fun debug(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.d(tag, message)
        } catch (_: Throwable) {
            println("[$tag] DEBUG: $message")
        }
    }

    fun info(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.i(tag, message)
        } catch (_: Throwable) {
            println("[$tag] INFO: $message")
        }
    }

    fun warning(tag: String = DEFAULT_TAG, message: String) {
        try {
            Log.w(tag, message)
        } catch (_: Throwable) {
            println("[$tag] WARN: $message")
        }
    }

    fun error(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        try {
            Log.e(tag, message, throwable)
        } catch (_: Throwable) {
            println("[$tag] ERROR: $message ${throwable?.message ?: ""}")
        }
    }
}
