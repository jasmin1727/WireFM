package com.wirefm.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wirefm.app.model.FileItem
import com.wirefm.app.model.ServerInfo
import com.wirefm.app.model.PhoneCommand
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

object WireFMClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getRequest(ip: String, port: String, pass: String, path: String): Request {
        return Request.Builder()
            .url("http://$ip:$port$path")
            .header("X-Password", pass)
            .build()
    }

    fun testConnection(ip: String, port: String, pass: String): ServerInfo? {
        return try {
            val req = Request.Builder().url("http://$ip:$port/api/info").build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                gson.fromJson(res.body?.string(), ServerInfo::class.java)
            } else null
        } catch (e: Exception) { null }
    }

    fun listFiles(ip: String, port: String, pass: String, path: String): List<FileItem>? {
        return try {
            val req = getRequest(ip, port, pass, "/api/files?path=${encode(path)}")
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return null
            val json = gson.fromJson(body, Map::class.java)
            val filesJson = gson.toJson(json["files"])
            val type = object : TypeToken<List<FileItem>>() {}.type
            gson.fromJson(filesJson, type)
        } catch (e: Exception) { null }
    }

    fun downloadFile(ip: String, port: String, pass: String, path: String, destDir: File): File? {
        return try {
            destDir.mkdirs()
            val req = getRequest(ip, port, pass, "/api/download?path=${encode(path)}")
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return null
            val fileName = path.substringAfterLast("/")
            val file = File(destDir, fileName)
            res.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        } catch (e: Exception) { null }
    }

    fun uploadFile(ip: String, port: String, pass: String, targetDirPath: String, file: File): Boolean {
        return try {
            val mediaType = "application/octet-stream".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
                .build()
            val req = Request.Builder()
                .url("http://$ip:$port/api/upload?path=${encode(targetDirPath)}&password=${encode(pass)}")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun uploadStream(ip: String, port: String, pass: String, targetDirPath: String, fileName: String, inputStream: InputStream): Boolean {
        return try {
            val bytes = inputStream.readBytes()
            val mediaType = "application/octet-stream".toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, bytes.toRequestBody(mediaType))
                .build()
            val req = Request.Builder()
                .url("http://$ip:$port/api/upload?path=${encode(targetDirPath)}&password=${encode(pass)}")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun deleteFile(ip: String, port: String, pass: String, path: String): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://$ip:$port/api/delete?path=${encode(path)}")
                .header("X-Password", pass)
                .delete()
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun copyFile(ip: String, port: String, pass: String, src: String, dest: String): Boolean {
        return try {
            val body = gson.toJson(mapOf("src" to src, "dest" to dest))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://$ip:$port/api/copy")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun moveFile(ip: String, port: String, pass: String, src: String, dest: String): Boolean {
        return try {
            val body = gson.toJson(mapOf("src" to src, "dest" to dest))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://$ip:$port/api/move")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun createFolder(ip: String, port: String, pass: String, path: String): Boolean {
        return try {
            val body = gson.toJson(mapOf("path" to path))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://$ip:$port/api/mkdir")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun sendPhoneHeartbeat(ip: String, port: String, pass: String, deviceName: String, currentPath: String, files: List<Map<String, Any>>): Boolean {
        return try {
            val payload = mapOf(
                "device_name" to deviceName,
                "current_path" to currentPath,
                "files" to files
            )
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://$ip:$port/api/phone/heartbeat")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun pollPhoneCommand(ip: String, port: String, pass: String): PhoneCommand? {
        return try {
            val req = Request.Builder()
                .url("http://$ip:$port/api/phone/poll")
                .header("X-Password", pass)
                .build()
            val res = client.newCall(req).execute()
            if (res.code == 200) {
                val body = res.body?.string() ?: return null
                val cmd = gson.fromJson(body, PhoneCommand::class.java)
                if (cmd != null && cmd.id.isNotBlank()) cmd else null
            } else null
        } catch (e: Exception) { null }
    }

    fun respondPhoneCommand(ip: String, port: String, pass: String, id: String, success: Boolean, files: List<Map<String, Any>>): Boolean {
        return try {
            val payload = mapOf(
                "id" to id,
                "success" to success,
                "files" to files
            )
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://$ip:$port/api/phone/respond")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun respondPhoneError(ip: String, port: String, pass: String, id: String, error: String): Boolean {
        return try {
            val payload = mapOf(
                "id" to id,
                "success" to false,
                "error" to error
            )
            val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("http://$ip:$port/api/phone/respond")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun uploadPhoneStream(ip: String, port: String, pass: String, token: String, file: File): Boolean {
        return try {
            val mediaType = "application/octet-stream".toMediaType()
            val body = file.asRequestBody(mediaType)
            val req = Request.Builder()
                .url("http://$ip:$port/api/phone/stream_upload?token=${encode(token)}&name=${encode(file.name)}")
                .header("X-Password", pass)
                .post(body)
                .build()
            client.newCall(req).execute().isSuccessful
        } catch (e: Exception) { false }
    }

    fun downloadFileToExactPath(ip: String, port: String, pass: String, pcPath: String, destFile: File): Boolean {
        return try {
            val parent = destFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val req = getRequest(ip, port, pass, "/api/download?path=${encode(pcPath)}")
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return false
            res.body?.byteStream()?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) { false }
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
