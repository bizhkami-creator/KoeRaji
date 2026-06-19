package com.koeradi.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var radioFileAdapter: RadioFileAdapter
    private lateinit var tvSelectedFolder: TextView
    private lateinit var tvCurrentSelection: TextView
    private lateinit var etSearch: EditText
    
    private var player: ExoPlayer? = null
    private var selectedRadioFile: RadioFile? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // 検索の読み上げ遅延用
    private val searchReadHandler = Handler(Looper.getMainLooper())
    private var searchReadRunnable: Runnable? = null

    // 全ての音声ファイルを保持するリスト（検索のベースになる）
    private var allRadioFiles: List<RadioFile> = listOf()

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

        // TextToSpeechの初期化
        tts = TextToSpeech(this, this)

        // システムバーのインセット設定
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvSelectedFolder = findViewById(R.id.tvSelectedFolder)
        tvCurrentSelection = findViewById(R.id.tvCurrentSelection)
        etSearch = findViewById(R.id.etSearch)

        // ExoPlayerの初期化
        player = ExoPlayer.Builder(this).build()

        // 初期表示用のダミーデータ
        allRadioFiles = listOf(
            RadioFileParser.createRadioFile("TBSラジオ", "荻上チキSession", "2026-06-15.m4a", ""),
            RadioFileParser.createRadioFile("NHKラジオ第1", "ラジオ深夜便", "20260614.m4a", ""),
            RadioFileParser.createRadioFile("Radio", "ニッポン放送", "[ニッポン放送]伊集院光のタネ(TimeFree)_2026-06-13.mp3", "")
        )

        // RecyclerViewの設定
        val recyclerView: RecyclerView = findViewById(R.id.rvRadioFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)
        radioFileAdapter = RadioFileAdapter(allRadioFiles) { radioFile ->
            // タップ時の処理
            selectedRadioFile = radioFile
            val selectionText = "${radioFile.stationName} / ${radioFile.programName} / ${radioFile.broadcastDate}"
            tvCurrentSelection.text = "選択中の番組：$selectionText"
            
            val toastMessage = "${radioFile.programName} を選択しました"
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            
            // 選択した番組を読み上げ
            speak("${radioFile.stationName}、${radioFile.programName}、${radioFile.broadcastDate}を選択しました")
        }
        recyclerView.adapter = radioFileAdapter

        // 検索欄の入力検知
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
            speak("一時停止しました")
            player?.pause()
        }

        // 停止ボタン
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            speak("停止しました")
            player?.stop()
            player?.seekTo(0)
        }
    }

    /**
     * TextToSpeechの初期化完了時に呼ばれる
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "日本語の読み上げがサポートされていません", Toast.LENGTH_SHORT).show()
                isTtsReady = false
            } else {
                isTtsReady = true
            }
        } else {
            Toast.makeText(this, "読み上げ機能の初期化に失敗しました", Toast.LENGTH_SHORT).show()
            isTtsReady = false
        }
    }

    /**
     * 音声を読み上げる
     */
    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KoeRadiTTS")
        }
    }

    /**
     * 検索フィルターを適用する
     */
    private fun applySearchFilter(query: String) {
        // 前回の読み上げ予約をキャンセル
        searchReadRunnable?.let { searchReadHandler.removeCallbacks(it) }
        searchReadRunnable = null

        var searchQuery = query.trim().lowercase()
        
        // 簡易日付検索の対応
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        if (searchQuery == "今日") {
            searchQuery = sdf.format(Calendar.getInstance().time)
        } else if (searchQuery == "昨日") {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DATE, -1)
            searchQuery = sdf.format(cal.time)
        }

        val filteredList = if (searchQuery.isEmpty()) {
            allRadioFiles
        } else {
            allRadioFiles.filter { file ->
                file.stationName.lowercase().contains(searchQuery) ||
                file.programName.lowercase().contains(searchQuery) ||
                file.broadcastDate.lowercase().contains(searchQuery) ||
                file.fileName.lowercase().contains(searchQuery) ||
                file.folderPath.lowercase().contains(searchQuery)
            }
        }

        if (filteredList.isEmpty() && searchQuery.isNotEmpty()) {
            Toast.makeText(this, "該当する音声ファイルがありません", Toast.LENGTH_SHORT).show()
        }
        
        radioFileAdapter.updateData(filteredList)

        // 検索結果の件数を少し遅れて読み上げる (クエリが空でない場合のみ)
        if (searchQuery.isNotEmpty()) {
            val runnable = Runnable {
                speak("検索結果は ${filteredList.size} 件です")
            }
            searchReadRunnable = runnable
            searchReadHandler.postDelayed(runnable, 1000) // 1秒入力が止まったら読み上げ
        }
    }

    private fun playAudio() {
        val file = selectedRadioFile
        if (file == null || file.uriString.isEmpty()) {
            val msg = "再生する音声ファイルを選択してください"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            speak(msg)
            return
        }

        try {
            speak("再生します")
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
        
        // 検索読み上げ予約の解除
        searchReadRunnable?.let { searchReadHandler.removeCallbacks(it) }
        searchReadRunnable = null
        
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
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
            speak("フォルダを選択しました")
            
            val rootFolderNameForParser = folderName ?: "Radio"
            val foundFiles = mutableListOf<RadioFile>()
            scanDirectoryRecursively(documentFile, "", foundFiles, rootFolderNameForParser)
            
            allRadioFiles = foundFiles
            
            // 選択状態をリセット
            selectedRadioFile = null
            tvCurrentSelection.text = "選択中の番組：未選択"
            
            // 再生中の音声を停止
            player?.stop()
            player?.seekTo(0)
            
            if (allRadioFiles.isEmpty()) {
                val msg = "音声ファイルが見つかりませんでした"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                speak(msg)
            } else {
                speak("音声ファイルが ${allRadioFiles.size} 件見つかりました")
            }
            
            // 現在の検索ワードがあれば適用、なければ全件表示
            applySearchFilter(etSearch.text.toString())
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
