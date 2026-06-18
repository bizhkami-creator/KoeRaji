package com.koeradi.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var radioFileAdapter: RadioFileAdapter
    private lateinit var tvSelectedFolder: TextView
    private lateinit var tvCurrentSelection: TextView
    
    private var player: ExoPlayer? = null
    private var selectedRadioFile: RadioFile? = null

    // フォルダ選択の結果を受け取る設定
    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            handleFolderSelected(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // システムバーのインセット設定
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvSelectedFolder = findViewById(R.id.tvSelectedFolder)
        tvCurrentSelection = findViewById(R.id.tvCurrentSelection)

        // ExoPlayerの初期化
        player = ExoPlayer.Builder(this).build()

        // 初期表示用のダミーデータ
        val dummyData = listOf(
            RadioFileParser.createRadioFile("TBSラジオ", "荻上チキSession", "2026-06-15.m4a", ""),
            RadioFileParser.createRadioFile("NHKラジオ第1", "ラジオ深夜便", "20260614.m4a", ""),
            RadioFileParser.createRadioFile("Radio", "ニッポン放送", "[ニッポン放送]伊集院光のタネ(TimeFree)_2026-06-13.mp3", "")
        )

        // RecyclerViewの設定
        val recyclerView: RecyclerView = findViewById(R.id.rvRadioFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)
        radioFileAdapter = RadioFileAdapter(dummyData) { radioFile ->
            // タップ時の処理
            selectedRadioFile = radioFile
            tvCurrentSelection.text = "選択中の番組：${radioFile.stationName} / ${radioFile.programName} / ${radioFile.broadcastDate}"
            Toast.makeText(this, "${radioFile.programName} を選択しました", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = radioFileAdapter

        // 「フォルダを選択」ボタン
        findViewById<Button>(R.id.btnSelectFolder).setOnClickListener {
            selectFolderLauncher.launch(null)
        }

        // 再生ボタン
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            playAudio()
        }

        // 一時停止ボタン
        findViewById<Button>(R.id.btnPause).setOnClickListener {
            player?.pause()
        }

        // 停止ボタン
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            player?.stop()
            player?.seekTo(0)
        }
    }

    private fun playAudio() {
        val file = selectedRadioFile
        if (file == null || file.uriString.isEmpty()) {
            Toast.makeText(this, "再生する音声ファイルを選択してください", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(file.uriString)
            val mediaItem = MediaItem.fromUri(uri)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.play()
        } catch (e: Exception) {
            Toast.makeText(this, "再生エラー: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    /**
     * フォルダが選択された時の処理
     */
    private fun handleFolderSelected(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        if (documentFile != null && documentFile.isDirectory) {
            val folderName = documentFile.name
            val folderDisplayName = if (folderName.isNullOrEmpty()) "フォルダ選択済み" else folderName
            tvSelectedFolder.text = "選択中：$folderDisplayName"
            
            val rootFolderNameForParser = folderName ?: "Radio"
            val foundFiles = mutableListOf<RadioFile>()
            scanDirectoryRecursively(documentFile, "", foundFiles, rootFolderNameForParser)
            
            if (foundFiles.isEmpty()) {
                Toast.makeText(this, "音声ファイルが見つかりませんでした", Toast.LENGTH_SHORT).show()
            }
            radioFileAdapter.updateData(foundFiles)
        }
    }

    private fun scanDirectoryRecursively(
        directory: DocumentFile,
        currentPath: String,
        resultList: MutableList<RadioFile>,
        rootFolderName: String
    ) {
        val files = directory.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                val nextPath = if (currentPath.isEmpty()) file.name ?: "" else "$currentPath/${file.name}"
                scanDirectoryRecursively(file, nextPath, resultList, rootFolderName)
            } else if (file.isFile) {
                val fileName = file.name ?: ""
                if (isAudioFile(fileName)) {
                    resultList.add(
                        RadioFileParser.createRadioFile(rootFolderName, currentPath, fileName, file.uri.toString())
                    )
                }
            }
        }
    }

    private fun isAudioFile(fileName: String): Boolean = 
        fileName.endsWith(".m4a", true) || fileName.endsWith(".mp3", true) || fileName.endsWith(".wav", true)
}
