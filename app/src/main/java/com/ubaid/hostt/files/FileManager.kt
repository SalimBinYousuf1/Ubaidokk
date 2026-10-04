package com.ubaid.hostt.files

import android.os.Environment
import android.util.Base64
import com.ubaid.hostt.data.ActivityLogRepository
import com.ubaid.hostt.model.FileItemDto
import com.ubaid.hostt.model.LogCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

class FileManager {

    data class ReadChunkResult(
        val path: String,
        val offset: Long,
        val totalSize: Long,
        val bytesRead: Int,
        val dataBase64: String,
        val isEof: Boolean,
        val error: String? = null
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("type", "file_chunk")
                put("path", path)
                put("offset", offset)
                put("totalSize", totalSize)
                put("bytesRead", bytesRead)
                put("data", dataBase64)
                put("isEof", isEof)
                if (error != null) put("error", error)
            }
        }
    }

    val defaultRootPath: String
        get() = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"

    fun listDirectory(requestedPath: String?): JSONObject {
        val targetPath = if (requestedPath.isNullOrBlank() || requestedPath == "/") defaultRootPath else requestedPath
        val targetDir = File(targetPath)

        val resultJson = JSONObject()
        resultJson.put("type", "dir_list")
        resultJson.put("path", targetPath)

        if (!targetDir.exists()) {
            resultJson.put("error", "Directory does not exist: $targetPath")
            resultJson.put("items", JSONArray())
            return resultJson
        }

        if (!targetDir.isDirectory) {
            resultJson.put("error", "Path is not a directory: $targetPath")
            resultJson.put("items", JSONArray())
            return resultJson
        }

        val itemsArray = JSONArray()
        val files = targetDir.listFiles()
        if (files != null) {
            files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
                val itemObj = JSONObject().apply {
                    put("name", f.name)
                    put("path", f.absolutePath)
                    put("isDirectory", f.isDirectory)
                    put("size", if (f.isDirectory) 0L else f.length())
                    put("lastModified", f.lastModified())
                }
                itemsArray.put(itemObj)
            }
        }
        resultJson.put("items", itemsArray)
        ActivityLogRepository.log(
            title = "File List Requested",
            description = "Browsed path: $targetPath (${files?.size ?: 0} items)",
            category = LogCategory.FILE
        )
        return resultJson
    }

    fun readFileChunk(path: String, offset: Long, chunkSize: Int = 64 * 1024): ReadChunkResult {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return ReadChunkResult(
                path = path,
                offset = offset,
                totalSize = 0L,
                bytesRead = 0,
                dataBase64 = "",
                isEof = true,
                error = "File does not exist or is not a file: $path"
            )
        }

        val totalLength = file.length()
        if (offset >= totalLength) {
            return ReadChunkResult(
                path = path,
                offset = offset,
                totalSize = totalLength,
                bytesRead = 0,
                dataBase64 = "",
                isEof = true
            )
        }

        val buffer = ByteArray(chunkSize.coerceAtMost((totalLength - offset).toInt()))
        try {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                val read = raf.read(buffer)
                val isEof = (offset + read) >= totalLength
                val base64Data = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                return ReadChunkResult(
                    path = path,
                    offset = offset,
                    totalSize = totalLength,
                    bytesRead = read,
                    dataBase64 = base64Data,
                    isEof = isEof
                )
            }
        } catch (e: Exception) {
            return ReadChunkResult(
                path = path,
                offset = offset,
                totalSize = totalLength,
                bytesRead = 0,
                dataBase64 = "",
                isEof = true,
                error = e.localizedMessage ?: "Read failure"
            )
        }
    }

    fun writeFileChunk(path: String, base64Data: String, append: Boolean): JSONObject {
        val file = File(path)
        val result = JSONObject()
        result.put("type", "write_result")
        result.put("path", path)
        try {
            file.parentFile?.mkdirs()
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            RandomAccessFile(file, "rw").use { raf ->
                if (append) {
                    raf.seek(file.length())
                } else {
                    raf.setLength(0)
                }
                raf.write(bytes)
            }
            result.put("success", true)
            result.put("bytesWritten", bytes.size)
            ActivityLogRepository.log(
                title = "File Saved",
                description = "Wrote ${bytes.size} bytes to ${file.name}",
                category = LogCategory.FILE
            )
        } catch (e: Exception) {
            result.put("success", false)
            result.put("error", e.localizedMessage ?: "Write failed")
        }
        return result
    }

    fun deleteFile(path: String): JSONObject {
        val file = File(path)
        val result = JSONObject()
        result.put("type", "delete_result")
        result.put("path", path)
        val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
        result.put("success", success)
        ActivityLogRepository.log(
            title = "File Deleted",
            description = "Deleted: $path (success: $success)",
            category = LogCategory.FILE
        )
        return result
    }
}
