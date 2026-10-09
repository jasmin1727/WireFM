package com.wirefm.app.model

import com.google.gson.annotations.SerializedName

data class FileItem(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("is_dir") val isDir: Boolean,
    @SerializedName("size") val size: Long,
    @SerializedName("mod_time") val modTime: String,
    @SerializedName("ext") val ext: String
)

data class ServerInfo(
    @SerializedName("os") val os: String,
    @SerializedName("user") val user: String,
    @SerializedName("hostname") val hostname: String,
    @SerializedName("root") val root: String,
    @SerializedName("version") val version: String
)
