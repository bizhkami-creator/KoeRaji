package com.koeradi.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var radioFileAdapter: RadioFileAdapter
    private lateinit var tvSelectedFolder: TextView

    // フォルダ選択の結果を受け取る設定
    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 永続的なアクセス権限を取得（再起動後もアクセスできるようにするため）
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

        // 初期表示用のダミーデータ（RadioFileParserを使用して生成）
        val dummyData = listOf(
            RadioFileParser.createRadioFile("TBSラジオ", "荻上チキSession", "2026-06-15.m4a", ""),
            RadioFileParser.createRadioFile("NHKラジオ第1", "ラジオ深夜便", "20260614.m4a", ""),
            RadioFileParser.createRadioFile("Radio", "ニッポン放送", "[ニッポン放送]伊集院光のタネ(TimeFree)_2026-06-13.mp3", "")
        )

        // RecyclerViewの設定
        val recyclerView: RecyclerView = findViewById(R.id.rvRadioFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)
        radioFileAdapter = RadioFileAdapter(dummyData)
        recyclerView.adapter = radioFileAdapter

        // 「フォルダを選択」ボタン
        findViewById<Button>(R.id.btnSelectFolder).setOnClickListener {
            selectFolderLauncher.launch(null)
        }

        // 再生操作ボタン
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            Toast.makeText(this, "再生機能はDay 4以降で実装します", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPause).setOnClickListener {
            Toast.makeText(this, "一時停止機能はDay 4以降で実装します", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            Toast.makeText(this, "停止機能はDay 4以降で実装します", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * フォルダが選択された時の処理
     */
    private fun handleFolderSelected(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        if (documentFile != null && documentFile.isDirectory) {
            val folderName = documentFile.name
            // 表示用のテキストを設定
            val folderDisplayName = if (folderName.isNullOrEmpty()) "フォルダ選択済み" else folderName
            tvSelectedFolder.text = "選択中：$folderDisplayName"
            
            // 解析用に使用するルートフォルダ名（nullの場合は"Radio"として扱う）
            val rootFolderNameForParser = folderName ?: "Radio"
            
            val foundFiles = mutableListOf<RadioFile>()
            // 再帰的にファイルをスキャン
            scanDirectoryRecursively(documentFile, "", foundFiles, rootFolderNameForParser)
            
            if (foundFiles.isEmpty()) {
                Toast.makeText(this, "音声ファイルが見つかりませんでした", Toast.LENGTH_SHORT).show()
            }
            
            // アダプターのデータを更新
            radioFileAdapter.updateData(foundFiles)
        }
    }

    /**
     * 指定されたディレクトリ内を再帰的にスキャンして音声ファイルを抽出する
     */
    private fun scanDirectoryRecursively(
        directory: DocumentFile,
        currentPath: String,
        resultList: MutableList<RadioFile>,
        rootFolderName: String
    ) {
        val files = directory.listFiles()

        for (file in files) {
            if (file.isDirectory) {
                val nextPath = if (currentPath.isEmpty()) {
                    file.name ?: ""
                } else {
                    "$currentPath/${file.name}"
                }
                scanDirectoryRecursively(file, nextPath, resultList, rootFolderName)
            } else if (file.isFile) {
                val fileName = file.name ?: ""
                if (isAudioFile(fileName)) {
                    // RadioFileParser を使って解析
                    resultList.add(
                        RadioFileParser.createRadioFile(rootFolderName, currentPath, fileName, file.uri.toString())
                    )
                }
            }
        }
    }

    /**
     * ファイル名が対象の音声ファイル形式かチェックする
     */
    private fun isAudioFile(fileName: String): Boolean {
        return fileName.endsWith(".m4a", ignoreCase = true) ||
               fileName.endsWith(".mp3", ignoreCase = true) ||
               fileName.endsWith(".wav", ignoreCase = true)
    }
}
