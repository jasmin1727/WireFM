package com.wirefm.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wirefm.app.R
import com.wirefm.app.adapter.FileAdapter
import com.wirefm.app.model.FileItem
import com.wirefm.app.network.WireFMClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabPc: LinearLayout
    private lateinit var tabPhone: LinearLayout
    private lateinit var btnPaste: TextView
    private lateinit var btnMenu: View
    private lateinit var adapter: FileAdapter

    private lateinit var ip: String
    private lateinit var port: String
    private lateinit var pass: String
    private var currentPcPath = ""
    private var currentPhoneDir: File = Environment.getExternalStorageDirectory() ?: File("/")
    private var isPhoneStorage = false

    // Clipboard state
    private var clipboard: FileItem? = null
    private var clipAction: String = "" // "copy" or "cut"
    private var clipboardSource: String = "" // "pc" or "phone"

    // Multi-file picker for uploading from phone to PC
    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            uploadSelectedUris(uris)
        }
    }

    // Permission launcher for older Android versions (< 11)
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (granted) {
            loadPhoneFiles(currentPhoneDir)
        } else {
            toast("Storage permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        ip = intent.getStringExtra("ip") ?: ""
        port = intent.getStringExtra("port") ?: "8080"
        pass = intent.getStringExtra("pass") ?: ""
        val hostname = intent.getStringExtra("hostname") ?: ""
        val user = intent.getStringExtra("user") ?: ""
        val root = intent.getStringExtra("root") ?: "/"

        title = "$user@$hostname"

        rvFiles = findViewById(R.id.rvFiles)
        tvPath = findViewById(R.id.tvPath)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        tabPc = findViewById(R.id.tabPc)
        tabPhone = findViewById(R.id.tabPhone)
        btnPaste = findViewById(R.id.btnPaste)
        btnMenu = findViewById(R.id.btnMenu)

        adapter = FileAdapter(
            onItemClick = { file ->
                if (file.isDir) {
                    if (isPhoneStorage) {
                        loadPhoneFiles(File(file.path))
                    } else {
                        navigateToPc(file.path)
                    }
                } else {
                    if (isPhoneStorage) {
                        showPhoneFileOptions(file)
                    } else {
                        showPcFileOptions(file)
                    }
                }
            },
            onItemLongClick = { file ->
                if (isPhoneStorage) {
                    showPhoneFileOptions(file)
                } else {
                    showPcContextMenu(file)
                }
                true
            }
        )

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = adapter

        // Setup storage tabs
        tabPc.setOnClickListener {
            if (isPhoneStorage) {
                isPhoneStorage = false
                updateTabStyles()
                navigateToPc(currentPcPath.ifEmpty { root })
            }
        }

        tabPhone.setOnClickListener {
            if (!isPhoneStorage) {
                isPhoneStorage = true
                updateTabStyles()
                if (checkStoragePermission()) {
                    loadPhoneFiles(currentPhoneDir)
                } else {
                    requestStoragePermission()
                }
            }
        }

        // Paste button listener
        btnPaste.setOnClickListener {
            handlePasteAction()
        }

        // Setup top header 3-dots menu
        btnMenu.setOnClickListener { view ->
            showTopPopupMenu(view)
        }

        updateTabStyles()
        updateClipboardUI()
        navigateToPc(root)
    }

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    override fun onResume() {
        super.onResume()
        if (isPhoneStorage && checkStoragePermission()) {
            loadPhoneFiles(currentPhoneDir)
        }
        startHeartbeat()
    }

    override fun onPause() {
        super.onPause()
        heartbeatJob?.cancel()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val phoneRoot = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
                    val filesList = if (checkStoragePermission()) {
                        phoneRoot.listFiles()?.take(60)?.map { f ->
                            mapOf<String, Any>(
                                "name" to f.name,
                                "path" to f.absolutePath,
                                "is_dir" to f.isDirectory,
                                "size" to if (f.isDirectory) 0L else f.length(),
                                "ext" to if (f.isDirectory) "" else "." + f.extension.lowercase()
                            )
                        } ?: emptyList()
                    } else emptyList()

                    val devName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    WireFMClient.sendPhoneHeartbeat(ip, port, pass, devName, phoneRoot.absolutePath, filesList)
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(12000)
            }
        }
    }

    private fun updateTabStyles() {
        if (isPhoneStorage) {
            tabPc.alpha = 0.5f
            tabPhone.alpha = 1.0f
        } else {
            tabPc.alpha = 1.0f
            tabPhone.alpha = 0.5f
        }
    }

    private fun updateClipboardUI() {
        if (clipboard != null) {
            btnPaste.alpha = 1.0f
            btnPaste.setBackgroundResource(R.drawable.shape_paste_glow)
            btnPaste.setPadding(24, 10, 24, 10)
        } else {
            btnPaste.alpha = 0.35f
            btnPaste.background = null
            btnPaste.setPadding(8, 8, 8, 8)
        }
    }

    // ==========================================
    // STORAGE PERMISSION (ANDROID 11+ & LEGACY)
    // ==========================================

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AlertDialog.Builder(this, R.style.WireFMDialog)
                .setTitle("Storage Access Required")
                .setMessage("To browse, manage, and transfer files from your phone's internal storage, WireFM needs 'All files access'.\n\nPlease allow it in the next screen.")
                .setPositiveButton("Allow in Settings") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    // ==========================================
    // SMART PASTE ACTION (BIDIRECTIONAL & GLOW)
    // ==========================================

    private fun handlePasteAction() {
        val cb = clipboard ?: run {
            toast("Clipboard is empty! Copy or cut a file first.")
            return
        }

        // Scenario 1: Clipboard from PHONE -> Currently in PC Storage -> UPLOAD to PC
        if (clipboardSource == "phone" && !isPhoneStorage) {
            val localFile = File(cb.path)
            if (!localFile.exists()) {
                toast("Source file no longer exists")
                clipboard = null
                updateClipboardUI()
                return
            }
            toast("Uploading to PC: ${localFile.name}...")
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    WireFMClient.uploadFile(ip, port, pass, currentPcPath, localFile)
                }
                progressBar.visibility = View.GONE
                if (ok) {
                    toast("✓ Pasted to PC: ${localFile.name}")
                    if (clipAction == "cut") {
                        localFile.delete()
                        clipboard = null
                    }
                    updateClipboardUI()
                    navigateToPc(currentPcPath)
                } else {
                    toast("Failed to paste to PC")
                }
            }
            return
        }

        // Scenario 2: Clipboard from PC -> Currently in PHONE Storage -> DOWNLOAD to Phone
        if (clipboardSource == "pc" && isPhoneStorage) {
            toast("Downloading to Phone: ${cb.name}...")
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val downloaded = withContext(Dispatchers.IO) {
                    WireFMClient.downloadFile(ip, port, pass, cb.path, currentPhoneDir)
                }
                progressBar.visibility = View.GONE
                if (downloaded != null) {
                    MediaScannerConnection.scanFile(
                        this@FileManagerActivity,
                        arrayOf(downloaded.absolutePath),
                        null,
                        null
                    )
                    toast("✓ Pasted to Phone: ${downloaded.name}")
                    if (clipAction == "cut") {
                        withContext(Dispatchers.IO) {
                            WireFMClient.deleteFile(ip, port, pass, cb.path)
                        }
                        clipboard = null
                    }
                    updateClipboardUI()
                    loadPhoneFiles(currentPhoneDir)
                } else {
                    toast("Failed to download and paste to Phone")
                }
            }
            return
        }

        // Scenario 3: Clipboard from PC -> Currently in PC Storage -> Local PC copy/move
        if (clipboardSource == "pc" && !isPhoneStorage) {
            val dest = "$currentPcPath/${cb.name}"
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    if (clipAction == "cut") WireFMClient.moveFile(ip, port, pass, cb.path, dest)
                    else WireFMClient.copyFile(ip, port, pass, cb.path, dest)
                }
                progressBar.visibility = View.GONE
                if (ok) {
                    if (clipAction == "cut") clipboard = null
                    updateClipboardUI()
                    navigateToPc(currentPcPath)
                    toast("✓ Pasted!")
                } else {
                    toast("Paste failed on PC")
                }
            }
            return
        }

        // Scenario 4: Clipboard from PHONE -> Currently in PHONE Storage -> Local Phone copy/move
        if (clipboardSource == "phone" && isPhoneStorage) {
            val srcFile = File(cb.path)
            val destFile = File(currentPhoneDir, srcFile.name)
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        if (clipAction == "cut") {
                            srcFile.renameTo(destFile)
                        } else {
                            srcFile.copyTo(destFile, overwrite = true)
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
                progressBar.visibility = View.GONE
                if (ok) {
                    MediaScannerConnection.scanFile(
                        this@FileManagerActivity,
                        arrayOf(destFile.absolutePath),
                        null,
                        null
                    )
                    if (clipAction == "cut") clipboard = null
                    updateClipboardUI()
                    loadPhoneFiles(currentPhoneDir)
                    toast("✓ Pasted to Phone!")
                } else {
                    toast("Paste failed on Phone")
                }
            }
            return
        }
    }

    // ==========================================
    // PC FILE OPERATIONS
    // ==========================================

    private fun navigateToPc(path: String) {
        currentPcPath = path
        tvPath.text = path
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                WireFMClient.listFiles(ip, port, pass, path)
            }

            progressBar.visibility = View.GONE
            if (files != null) {
                adapter.submitList(files)
                tvStatus.text = "${files.size} items (PC)"
            } else {
                Toast.makeText(this@FileManagerActivity, "Failed to load PC files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPcFileOptions(file: FileItem) {
        val options = arrayOf(
            "▶️ Stream / Open",
            "📥 Download to Phone",
            "📋 Copy",
            "✂️ Cut",
            "🗑️ Delete",
            "✏️ Rename"
        )
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> streamOrOpenFile(file)
                    1 -> downloadFileToPhone(file)
                    2 -> {
                        clipboard = file
                        clipAction = "copy"
                        clipboardSource = "pc"
                        updateClipboardUI()
                        toast("Copied: ${file.name}")
                    }
                    3 -> {
                        clipboard = file
                        clipAction = "cut"
                        clipboardSource = "pc"
                        updateClipboardUI()
                        toast("Cut: ${file.name}")
                    }
                    4 -> confirmDeletePc(file)
                    5 -> renamePcFile(file)
                }
            }.show()
    }

    private fun showPcContextMenu(file: FileItem) {
        val options = if (file.isDir) {
            arrayOf("📂 Open", "📋 Copy", "✂️ Cut", "📋 Paste Here", "🗑️ Delete", "✏️ Rename")
        } else {
            arrayOf("▶️ Stream / Open", "📥 Download to Phone", "📋 Copy", "✂️ Cut", "📋 Paste Here", "🗑️ Delete", "✏️ Rename")
        }

        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                val selected = options[which]
                when {
                    selected.startsWith("📂 Open") -> navigateToPc(file.path)
                    selected.startsWith("▶️ Stream") -> streamOrOpenFile(file)
                    selected.startsWith("📥 Download") -> downloadFileToPhone(file)
                    selected.startsWith("📋 Copy") -> {
                        clipboard = file
                        clipAction = "copy"
                        clipboardSource = "pc"
                        updateClipboardUI()
                        toast("Copied")
                    }
                    selected.startsWith("✂️ Cut") -> {
                        clipboard = file
                        clipAction = "cut"
                        clipboardSource = "pc"
                        updateClipboardUI()
                        toast("Cut")
                    }
                    selected.startsWith("📋 Paste") -> handlePasteAction()
                    selected.startsWith("🗑️ Delete") -> confirmDeletePc(file)
                    selected.startsWith("✏️ Rename") -> renamePcFile(file)
                }
            }.show()
    }

    private fun streamOrOpenFile(file: FileItem) {
        try {
            val url = "http://$ip:$port/api/download?path=${URLEncoder.encode(file.path, "UTF-8")}&password=${URLEncoder.encode(pass, "UTF-8")}&inline=1"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), getMimeType(file.ext))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("No external app found to play/view this file")
        }
    }

    private fun downloadFileToPhone(file: FileItem) {
        toast("Downloading ${file.name}...")
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: filesDir

            val downloadedFile = withContext(Dispatchers.IO) {
                WireFMClient.downloadFile(ip, port, pass, file.path, targetDir)
            }
            progressBar.visibility = View.GONE
            if (downloadedFile != null) {
                MediaScannerConnection.scanFile(this@FileManagerActivity, arrayOf(downloadedFile.absolutePath), null, null)
                toast("Saved to Downloads: ${downloadedFile.name}")
            } else {
                toast("Download failed")
            }
        }
    }

    private fun confirmDeletePc(file: FileItem) {
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle("Delete")
            .setMessage("Delete \"${file.name}\" on PC?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        WireFMClient.deleteFile(ip, port, pass, file.path)
                    }
                    if (ok) {
                        navigateToPc(currentPcPath)
                        toast("Deleted")
                    } else {
                        toast("Delete failed")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renamePcFile(file: FileItem) {
        val input = EditText(this).apply { setText(file.name) }
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                val dir = file.path.substringBeforeLast("/")
                val dest = "$dir/$newName"
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        WireFMClient.moveFile(ip, port, pass, file.path, dest)
                    }
                    if (ok) {
                        navigateToPc(currentPcPath)
                        toast("Renamed")
                    } else {
                        toast("Rename failed")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==========================================
    // PHONE FILE OPERATIONS & UPLOAD
    // ==========================================

    private fun loadPhoneFiles(dir: File) {
        if (!checkStoragePermission()) {
            requestStoragePermission()
            return
        }

        currentPhoneDir = dir
        tvPath.text = "Phone: " + dir.absolutePath
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                var files = dir.listFiles() ?: arrayOf()

                // Fallback for root /storage/emulated/0 if empty
                if (files.isEmpty() && (dir.absolutePath == "/storage/emulated/0" || dir.absolutePath == Environment.getExternalStorageDirectory().absolutePath)) {
                    val defaultFolders = listOf(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    ).filter { it != null && it.exists() }.toTypedArray()
                    if (defaultFolders.isNotEmpty()) {
                        files = defaultFolders
                    }
                }

                files.map { f ->
                    val ext = if (f.isDirectory) "" else "." + f.extension.lowercase(Locale.getDefault())
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(f.lastModified()))
                    FileItem(
                        name = f.name,
                        path = f.absolutePath,
                        isDir = f.isDirectory,
                        size = if (f.isDirectory) 0L else f.length(),
                        modTime = dateStr,
                        ext = ext
                    )
                }.sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase(Locale.getDefault()) })
            }

            progressBar.visibility = View.GONE
            adapter.submitList(list)
            tvStatus.text = "${list.size} items (Phone)"
        }
    }

    private fun showPhoneFileOptions(file: FileItem) {
        val options = arrayOf(
            "📤 Upload to PC ($currentPcPath)",
            "📋 Copy",
            "✂️ Cut",
            "📂 Open File",
            "🗑️ Delete"
        )
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> uploadLocalFileToPc(File(file.path))
                    1 -> {
                        clipboard = file
                        clipAction = "copy"
                        clipboardSource = "phone"
                        updateClipboardUI()
                        toast("Copied from Phone: ${file.name}")
                    }
                    2 -> {
                        clipboard = file
                        clipAction = "cut"
                        clipboardSource = "phone"
                        updateClipboardUI()
                        toast("Cut from Phone: ${file.name}")
                    }
                    3 -> openLocalPhoneFile(File(file.path), file.ext)
                    4 -> {
                        val f = File(file.path)
                        val trashDir = File(Environment.getExternalStorageDirectory(), ".WireFM_Trash")
                        if (!trashDir.exists()) trashDir.mkdirs()
                        val trashFile = File(trashDir, "${System.currentTimeMillis()}_${f.name}")
                        val trashed = f.renameTo(trashFile)
                        if (trashed) {
                            loadPhoneFiles(currentPhoneDir)
                            toast("Moved to Recycle Bin (.WireFM_Trash)")
                        } else if (f.delete()) {
                            loadPhoneFiles(currentPhoneDir)
                            toast("Deleted")
                        } else {
                            toast("Failed to delete")
                        }
                    }
                }
            }.show()
    }

    private fun uploadLocalFileToPc(localFile: File) {
        toast("Uploading ${localFile.name} to PC...")
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                WireFMClient.uploadFile(ip, port, pass, currentPcPath, localFile)
            }
            progressBar.visibility = View.GONE
            if (ok) {
                toast("✓ Uploaded: ${localFile.name}")
            } else {
                toast("Upload failed")
            }
        }
    }

    private fun uploadSelectedUris(uris: List<Uri>) {
        toast("Uploading ${uris.size} file(s) to PC...")
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            var successCount = 0
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    var fileName = "upload_${System.currentTimeMillis()}"
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val ok = WireFMClient.uploadStream(ip, port, pass, currentPcPath, fileName, stream)
                        if (ok) successCount++
                    }
                }
            }
            progressBar.visibility = View.GONE
            toast("Uploaded $successCount/${uris.size} file(s)!")
            if (!isPhoneStorage) {
                navigateToPc(currentPcPath)
            }
        }
    }

    private fun openLocalPhoneFile(file: File, ext: String) {
        try {
            val uri = Uri.fromFile(file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(ext))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("No app found to open this file")
        }
    }

    private fun getMimeType(ext: String): String {
        return when (ext.lowercase(Locale.getDefault())) {
            ".mp4", ".mkv", ".webm", ".avi", ".mov" -> "video/*"
            ".mp3", ".wav", ".ogg", ".flac", ".m4a" -> "audio/*"
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg" -> "image/*"
            ".pdf" -> "application/pdf"
            ".txt", ".log", ".json", ".md", ".xml", ".html", ".js", ".py" -> "text/plain"
            else -> "*/*"
        }
    }

    // ==========================================
    // MENUS AND ACTIONS
    // ==========================================

    private fun showTopPopupMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            add("📋 Paste Here")
            add("📤 Upload Files to PC")
            add("📁 New Folder")
            add("🔄 Refresh")
            add("🚪 Disconnect")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "📋 Paste Here" -> handlePasteAction()
                "📤 Upload Files to PC" -> pickFilesLauncher.launch("*/*")
                "📁 New Folder" -> showNewFolderDialog()
                "🔄 Refresh" -> if (isPhoneStorage) loadPhoneFiles(currentPhoneDir) else navigateToPc(currentPcPath)
                "🚪 Disconnect" -> finish()
            }
            true
        }
        popup.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.file_manager_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_upload -> {
                pickFilesLauncher.launch("*/*")
                true
            }
            R.id.action_new_folder -> {
                showNewFolderDialog()
                true
            }
            R.id.action_refresh -> {
                if (isPhoneStorage) loadPhoneFiles(currentPhoneDir) else navigateToPc(currentPcPath)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply { hint = "Folder name" }
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                if (isPhoneStorage) {
                    val newDir = File(currentPhoneDir, name)
                    if (newDir.mkdirs()) {
                        loadPhoneFiles(currentPhoneDir)
                        toast("Folder created")
                    } else {
                        toast("Failed to create folder")
                    }
                } else {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            WireFMClient.createFolder(ip, port, pass, "$currentPcPath/$name")
                        }
                        navigateToPc(currentPcPath)
                        toast("Folder created")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        if (isPhoneStorage) {
            val parent = currentPhoneDir.parentFile
            if (parent != null && parent.canRead() && parent.absolutePath != "/") {
                loadPhoneFiles(parent)
            } else {
                isPhoneStorage = false
                updateTabStyles()
                navigateToPc(currentPcPath)
            }
        } else {
            if (currentPcPath.length > 1 && currentPcPath != "/") {
                val parent = currentPcPath.substringBeforeLast("/").ifEmpty { "/" }
                navigateToPc(parent)
            } else {
                super.onBackPressed()
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
