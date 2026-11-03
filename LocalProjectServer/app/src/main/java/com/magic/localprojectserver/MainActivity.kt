package com.magic.localprojectserver

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelect: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnOpenChrome: Button
    private lateinit var webView: WebView

    private var projectDir: File? = null
    private val serverUrl = "http://localhost:8080"

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        btnSelect = findViewById(R.id.btnSelect)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnOpenChrome = findViewById(R.id.btnOpenChrome)
        webView = findViewById(R.id.webView)

        setupWebView()

        btnSelect.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        btnStart.setOnClickListener {
            startServer()
        }

        btnStop.setOnClickListener {
            stopServer()
        }

        btnOpenChrome.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun handleSelectedFolder(treeUri: Uri) {
        val docFolder = DocumentFile.fromTreeUri(this, treeUri)
        if (docFolder == null || !docFolder.isDirectory) {
            Toast.makeText(this, "செல்லுபடியாகும் ஃபோல்டரைத் தேர்ந்தெடுக்கவும்!", Toast.LENGTH_SHORT).show()
            return
        }

        tvStatus.text = "கோப்புகள் நகலெடுக்கப்படுகின்றன..."
        progressBar.visibility = View.VISIBLE
        btnSelect.isEnabled = false
        btnStart.isEnabled = false

        val targetDir = File(cacheDir, "active_project")
        targetDir.deleteRecursively()
        targetDir.mkdirs()

        Thread {
            var copySuccess = true
            var errorMessage = ""
            try {
                copyDocumentFolder(docFolder, targetDir)
                val indexFile = File(targetDir, "index.html")
                if (!indexFile.exists()) {
                    copySuccess = false
                    errorMessage = "index.html கோப்பு ஃபோல்டரில் இல்லை!"
                }
            } catch (e: Exception) {
                copySuccess = false
                errorMessage = e.message ?: "நகலெடுப்பதில் பிழை ஏற்பட்டது"
            }

            runOnUiThread {
                progressBar.visibility = View.GONE
                btnSelect.isEnabled = true
                if (copySuccess) {
                    projectDir = targetDir
                    tvStatus.text = "தயாராக உள்ளது: ${docFolder.name}"
                    btnStart.isEnabled = true
                } else {
                    projectDir = null
                    tvStatus.text = "தோல்வி: $errorMessage"
                    btnStart.isEnabled = false
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @Throws(IOException::class)
    private fun copyDocumentFolder(srcDoc: DocumentFile, destDir: File) {
        for (file in srcDoc.listFiles()) {
            if (file.isDirectory) {
                val subDir = File(destDir, file.name ?: "folder")
                if (!subDir.exists() && !subDir.mkdirs()) {
                    throw IOException("துணை ஃபோல்டர் உருவாக்க முடியவில்லை: ${file.name}")
                }
                copyDocumentFolder(file, subDir)
            } else if (file.isFile) {
                val destFile = File(destDir, file.name ?: "file")
                contentResolver.openInputStream(file.uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("கோப்பை வாசிக்க முடியவில்லை: ${file.name}")
            }
        }
    }

    private fun startServer() {
        val dir = projectDir ?: return
        val serviceIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_START
            putExtra(ServerForegroundService.EXTRA_PATH, dir.absolutePath)
        }

        ContextCompat.startForegroundService(this, serviceIntent)

        tvStatus.text = "Server Online: $serverUrl"
        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        btnOpenChrome.visibility = View.VISIBLE

        webView.visibility = View.VISIBLE
        webView.loadUrl(serverUrl)
    }

    private fun stopServer() {
        val serviceIntent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_STOP
        }
        startService(serviceIntent)

        tvStatus.text = "Server நிறுத்தப்பட்டது"
        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        btnOpenChrome.visibility = View.GONE
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE
    }
}
