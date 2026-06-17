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

        // 初期表示用のダミーデータ（RadioFileの構造変更に合わせて修正）
        val dummyData = listOf(
            RadioFile("TBSラジオ", "荻上チキ Session", "2026-06-15", "TBSラジオ_荻上チキSession_2026-06-15.m4a", "", "Sample/TBS/"),
            RadioFile("NHKラジオ第1", "ラジオ深夜便", "2026-06-14", "NHKラジオ第1_ラジオ深夜便_2026-06-14.m4a", "", "Sample/NHK/"),
            RadioFile("ニッポン放送", "オールナイトニッポン", "2026-06-13", "ニッポン放送_オールナイトニッポン_2026-06-13.mp3", "", "Sample/LFR/")
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
            Toast.makeText(this, "再生機能はDay 3以降で実装します", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPause).setOnClickListener {
            Toast.makeText(this, "一時停止機能はDay 3以降で実装します", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            Toast.makeText(this, "停止機能はDay 3以降で実装します", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * フォルダが選択された時の処理
     */
    private fun handleFolderSelected(uri: Uri) {
        val documentFile = DocumentFile.fromTreeUri(this, uri)
        if (documentFile != null && documentFile.isDirectory) {
            tvSelectedFolder.text = "選択中: ${documentFile.name}"
            
            val foundFiles = mutableListOf<RadioFile>()
            // 再帰的にファイルをスキャン
            scanDirectoryRecursively(documentFile, "", foundFiles)
            
            if (foundFiles.isEmpty()) {
                Toast.makeText(this, "音声ファイルが見つかりませんでした", Toast.LENGTH_SHORT).show()
            }
            
            // アダプターのデータを更新
            radioFileAdapter.updateData(foundFiles)
        }
    }

    /**
     * 指定されたディレクトリ内を再帰的にスキャンして音声ファイルを抽出する
     * @param directory スキャン対象のディレクトリ
     * @param currentPath 選択したルートフォルダからの相対パス
     * @param resultList 見つかったファイルを格納するリスト
     */
    private fun scanDirectoryRecursively(
        directory: DocumentFile,
        currentPath: String,
        resultList: MutableList<RadioFile>
    ) {
        val files = directory.listFiles()

        for (file in files) {
            if (file.isDirectory) {
                // ディレクトリの場合は再帰的に呼び出し
                val nextPath = if (currentPath.isEmpty()) {
                    file.name ?: ""
                } else {
                    "$currentPath/${file.name}"
                }
                scanDirectoryRecursively(file, nextPath, resultList)
            } else if (file.isFile) {
                // ファイルの場合は拡張子をチェック
                val fileName = file.name ?: ""
                if (isAudioFile(fileName)) {
                    resultList.add(
                        RadioFile(
                            stationName = "未判定",
                            programName = "未判定",
                            broadcastDate = "未判定",
                            fileName = fileName,
                            uriString = file.uri.toString(),
                            folderPath = currentPath
                        )
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
