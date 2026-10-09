package com.wirefm.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wirefm.app.R
import com.wirefm.app.adapter.FileAdapter
import com.wirefm.app.model.FileItem
import com.wirefm.app.network.WireFMClient
import kotlinx.coroutines.*

class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvPath: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: FileAdapter

    private lateinit var ip: String
    private lateinit var port: String
    private lateinit var pass: String
    private var currentPath = ""
    private var clipboard: FileItem? = null
    private var clipAction = ""

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

        adapter = FileAdapter(
            onItemClick = { file ->
                if (file.isDir) navigateTo(file.path)
                else showFileOptions(file)
            },
            onItemLongClick = { file ->
                showContextMenu(file)
                true
            }
        )

        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = adapter

        // Navigate to root
        navigateTo(root)
    }

    private fun navigateTo(path: String) {
        currentPath = path
        tvPath.text = path
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                WireFMClient.listFiles(ip, port, pass, path)
            }

            progressBar.visibility = View.GONE
            if (files != null) {
                adapter.submitList(files)
                tvStatus.text = "${files.size} items"
            } else {
                Toast.makeText(this@FileManagerActivity, "Failed to load files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFileOptions(file: FileItem) {
        val options = arrayOf("📥 Download", "📋 Copy", "✂️ Cut", "🗑️ Delete", "✏️ Rename")
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> downloadFile(file)
                    1 -> { clipboard = file; clipAction = "copy"; toast("Copied: ${file.name}") }
                    2 -> { clipboard = file; clipAction = "cut"; toast("Cut: ${file.name}") }
                    3 -> confirmDelete(file)
                    4 -> renameFile(file)
                }
            }.show()
    }

    private fun showContextMenu(file: FileItem) {
        val options = if (file.isDir)
            arrayOf("📂 Open", "📋 Copy", "✂️ Cut", "📋 Paste Here", "🗑️ Delete", "✏️ Rename")
        else
            arrayOf("📥 Download", "📋 Copy", "✂️ Cut", "📋 Paste Here", "🗑️ Delete", "✏️ Rename")

        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (file.isDir) navigateTo(file.path) else downloadFile(file)
                    1 -> { clipboard = file; clipAction = "copy"; toast("Copied") }
                    2 -> { clipboard = file; clipAction = "cut"; toast("Cut") }
                    3 -> pasteFile()
                    4 -> confirmDelete(file)
                    5 -> renameFile(file)
                }
            }.show()
    }

    private fun downloadFile(file: FileItem) {
        toast("Downloading ${file.name}...")
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                WireFMClient.downloadFile(ip, port, pass, file.path, filesDir)
            }
            toast(if (ok) "Downloaded: ${file.name}" else "Download failed")
        }
    }

    private fun confirmDelete(file: FileItem) {
        AlertDialog.Builder(this, R.style.WireFMDialog)
            .setTitle("Delete")
            .setMessage("Delete \"${file.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        WireFMClient.deleteFile(ip, port, pass, file.path)
                    }
                    if (ok) { navigateTo(currentPath); toast("Deleted") }
                    else toast("Delete failed")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pasteFile() {
        val cb = clipboard ?: run { toast("Nothing in clipboard"); return }
        val dest = "$currentPath/${cb.name}"
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                if (clipAction == "cut") WireFMClient.moveFile(ip, port, pass, cb.path, dest)
                else WireFMClient.copyFile(ip, port, pass, cb.path, dest)
            }
            if (ok) { clipboard = null; navigateTo(currentPath); toast("Done!") }
            else toast("Paste failed")
        }
    }

    private fun renameFile(file: FileItem) {
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
                    if (ok) { navigateTo(currentPath); toast("Renamed") }
                    else toast("Rename failed")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.file_manager_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new_folder -> { showNewFolderDialog(); true }
            R.id.action_refresh -> { navigateTo(currentPath); true }
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
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        WireFMClient.createFolder(ip, port, pass, "$currentPath/$name")
                    }
                    navigateTo(currentPath)
                    toast("Folder created")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onBackPressed() {
        if (currentPath.length > 1) {
            val parent = currentPath.substringBeforeLast("/").ifEmpty { "/" }
            navigateTo(parent)
        } else {
            super.onBackPressed()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
