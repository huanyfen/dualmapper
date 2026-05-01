package com.example.dualmapper.util

import android.content.Context
import com.example.dualmapper.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader

object LogExporter {
    fun exportLogs(context: Context): File {
        val file = File(context.cacheDir, "dualmapper_log_${System.currentTimeMillis()}.txt")

        // 修复：仅调试构建允许导出日志，正式版返回提示信息
        if (!BuildConfig.DEBUG) {
            file.writeText("Log export is disabled in release builds.")
            return file
        }

        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                FileWriter(file).use { writer ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        writer.write(line)
                        writer.write("\n")
                    }
                }
            }
        } catch (e: IOException) {
            file.writeText("Failed to capture logs: ${e.message}")
        }
        return file
    }
}