package com.wirefm.app.network

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wirefm.app.model.FileItem
import com.wirefm.app.model.ServerInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object WireFMClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    fun downloadFile(ip: String, port: String, pass: String, path: String, destDir: File): Boolean {
        return try {
            val req = getRequest(ip, port, pass, "/api/download?path=${encode(path)}")
            val res = client.newCall(req).execute()
            val file = File(destDir, path.substringAfterLast("/"))
            res.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            true
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

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
