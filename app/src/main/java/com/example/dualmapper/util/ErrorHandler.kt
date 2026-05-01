package com.example.dualmapper.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.dualmapper.R
import java.io.IOException

object ErrorHandler {
    fun handle(context: Context?, throwable: Throwable, userMessage: String? = null) {
        Log.e("DualMapper", "Error occurred", throwable)
        context?.let { ctx ->
            val message = userMessage ?: when (throwable) {
                is IOException -> ctx.getString(R.string.network_error)
                is SecurityException -> ctx.getString(R.string.permission_denied)
                else -> ctx.getString(R.string.unknown_error)
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}