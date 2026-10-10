package com.wirefm.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.integration.android.IntentIntegrator
import com.wirefm.app.R
import com.wirefm.app.databinding.ActivityConnectBinding
import com.wirefm.app.network.WireFMClient
import kotlinx.coroutines.*

class ConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectBinding
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved prefs
        val prefs = getSharedPreferences("wirefm", MODE_PRIVATE)
        binding.etIp.setText(prefs.getString("last_ip", ""))
        binding.etPassword.setText(prefs.getString("last_pass", ""))

        // QR Scan button
        binding.btnScanQr.setOnClickListener {
            IntentIntegrator(this).apply {
                setPrompt("Scan WireFM QR code")
                setOrientationLocked(false)
                setBeepEnabled(true)
                initiateScan()
            }
        }

        // Manual connect button
        binding.btnConnect.setOnClickListener {
            val ip = binding.etIp.text.toString().trim()
            val pass = binding.etPassword.text.toString()
            val port = binding.etPort.text.toString().ifEmpty { "8080" }

            if (ip.isEmpty()) {
                binding.etIp.error = "Enter IP address"
                return@setOnClickListener
            }
            attemptConnect(ip, port, pass)
        }
    }

    private fun attemptConnect(ip: String, port: String, pass: String) {
        binding.btnConnect.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Connecting..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                WireFMClient.testConnection(ip, port, pass)
            }

            if (result != null) {
                // Save prefs
                getSharedPreferences("wirefm", MODE_PRIVATE).edit()
                    .putString("last_ip", ip)
                    .putString("last_port", port)
                    .putString("last_pass", pass)
                    .apply()

                // Show success
                binding.tvStatus.text = "✓ Connected to ${result.hostname}"
                binding.cardSuccess.visibility = View.VISIBLE
                binding.tvConnectedInfo.text = "${result.user}@${result.hostname} (${result.os})"

                delay(800)

                // Go to file manager
                startActivity(Intent(this@ConnectActivity, FileManagerActivity::class.java).apply {
                    putExtra("ip", ip)
                    putExtra("port", port)
                    putExtra("pass", pass)
                    putExtra("hostname", result.hostname)
                    putExtra("user", result.user)
                    putExtra("os", result.os)
                    putExtra("root", result.root)
                })
                finish()
            } else {
                binding.tvStatus.text = "Connection failed"
                binding.progressBar.visibility = View.GONE
                binding.btnConnect.isEnabled = true
                Toast.makeText(this@ConnectActivity, "Cannot connect. Check IP & password.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // QR Code result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            // Parse: http://IP:PORT?password=PASS
            try {
                val raw = result.contents.trim()
                val fullUrl = if (!raw.startsWith("http://") && !raw.startsWith("https://")) "http://$raw" else raw
                val url = java.net.URL(fullUrl)
                val ip = url.host
                val port = if (url.port > 0) url.port.toString() else "8080"
                val pass = url.query?.substringAfter("password=") ?: ""

                binding.etIp.setText(ip)
                binding.etPort.setText(port)
                binding.etPassword.setText(pass)

                // Auto connect after QR scan
                attemptConnect(ip, port, pass)
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
