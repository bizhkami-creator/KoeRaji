package com.koeradi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import java.util.regex.Pattern

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var radioFileAdapter: RadioFileAdapter
    private lateinit var tvSelectedFolder: TextView
    private lateinit var tvCurrentSelection: TextView
    private lateinit var etSearch: EditText
    private lateinit var tvVoiceResult: TextView
    
    private var player: ExoPlayer? = null
    private var selectedRadioFile: RadioFile? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var speechRecognizer: SpeechRecognizer? = null

    // 音声操作時の再生状態管理
    private var wasPlayingBeforeVoiceInput = false
    private var shouldResumeAfterVoiceResponse = false
    private var pendingTtsAction: (() -> Unit)? = null

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

    // 録音権限のリクエスト用
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(this, "音声操作にはマイクの権限が必要です", Toast.LENGTH_SHORT).show()
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
        tvVoiceResult = findViewById(R.id.tvVoiceResult)

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
            selectRadioFile(radioFile)
            
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

        // 「音声操作」ボタン
        findViewById<Button>(R.id.btnVoiceControl).setOnClickListener {
            checkPermissionAndStartVoice()
        }

        // 再生ボタン
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            playAudio()
        }

        // 一時停止ボタン
        findViewById<Button>(R.id.btnPause).setOnClickListener {
            speakThen("一時停止しました") {
                player?.pause()
            }
        }

        // 停止ボタン
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            speakThen("停止しました") {
                player?.stop()
                player?.seekTo(0)
            }
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
                setupTtsProgressListener()
            }
        } else {
            Toast.makeText(this, "読み上げ機能の初期化に失敗しました", Toast.LENGTH_SHORT).show()
            isTtsReady = false
        }
    }

    private fun setupTtsProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    pendingTtsAction?.invoke()
                    pendingTtsAction = null
                }
            }
            override fun onError(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    pendingTtsAction?.invoke()
                    pendingTtsAction = null
                }
            }
        })
    }

    /**
     * 音声を読み上げる (即時)
     */
    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KoeRadiTTS_${System.currentTimeMillis()}")
        }
    }

    /**
     * 音声を読み上げ、完了後にアクションを実行する
     */
    private fun speakThen(text: String, afterSpeech: (() -> Unit)? = null) {
        if (isTtsReady) {
            pendingTtsAction = afterSpeech
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "KoeRadiTTS_${System.currentTimeMillis()}")
        } else {
            afterSpeech?.invoke()
        }
    }

    /**
     * 検索フィルターを適用しUIを更新する
     */
    private fun applySearchFilter(query: String) {
        // 前回の読み上げ予約をキャンセル
        searchReadRunnable?.let { searchReadHandler.removeCallbacks(it) }
        searchReadRunnable = null

        val filteredList = filterRadioFiles(query)

        if (filteredList.isEmpty() && query.trim().isNotEmpty()) {
            Toast.makeText(this, "該当する音声ファイルがありません", Toast.LENGTH_SHORT).show()
        }
        
        radioFileAdapter.updateData(filteredList)

        // 自動選択の更新
        updateAutoSelection(filteredList, query.isNotBlank())

        // 検索結果の件数を少し遅れて読み上げる
        if (query.trim().isNotEmpty()) {
            val runnable = Runnable {
                speak("検索結果は ${filteredList.size} 件です")
            }
            searchReadRunnable = runnable
            searchReadHandler.postDelayed(runnable, 1000)
        }
    }

    /**
     * クエリに基づいてリストをフィルタリングする (AND検索)
     */
    private fun filterRadioFiles(query: String): List<RadioFile> {
        val terms = buildSearchTerms(query)
        return if (terms.isEmpty()) {
            allRadioFiles
        } else {
            allRadioFiles.filter { file ->
                matchesAllTerms(file, terms)
            }
        }
    }

    /**
     * 音声コマンド「○○を再生」用の処理
     */
    private fun playBySearchQuery(query: String) {
        val filteredList = filterRadioFiles(query)
        
        // 検索欄を更新し、リストを表示する
        etSearch.setText(query)
        // applySearchFilterの自動読み上げと被らないようにキャンセル
        searchReadRunnable?.let { searchReadHandler.removeCallbacks(it) }

        if (filteredList.isEmpty()) {
            selectedRadioFile = null
            tvCurrentSelection.text = "選択中の番組：未選択"
            speakThen("該当する音声ファイルが見つかりませんでした") {
                resumePlaybackIfNeeded()
            }
        } else {
            val file = filteredList[0]
            selectRadioFile(file)
            
            val count = filteredList.size
            val message = if (count == 1) "1件見つかりました。再生します" else "${count}件見つかりました。最初の音声を再生します"
            
            clearResumeAfterVoiceInput() // 新しい再生をするので再開フラグを折る

            speakThen(message) {
                startPlaybackWithoutAnnouncement()
            }
        }
    }

    /**
     * 番組を選択状態にする
     */
    private fun selectRadioFile(radioFile: RadioFile) {
        selectedRadioFile = radioFile
        val selectionText = "${radioFile.stationName} / ${radioFile.programName} / ${radioFile.broadcastDate}"
        tvCurrentSelection.text = "選択中の番組：$selectionText"
    }

    /**
     * 検索結果が1件の場合に自動選択する
     */
    private fun updateAutoSelection(filteredList: List<RadioFile>, isSearchActive: Boolean) {
        if (!isSearchActive) return // 検索中でなければ何もしない

        if (filteredList.size == 1) {
            val file = filteredList[0]
            selectRadioFile(file)
            
            // 自動選択を読み上げ
            speak("${file.stationName}、${file.programName}、${file.broadcastDate}を選択しました")
        } else {
            // 0件または2件以上の場合は選択解除
            selectedRadioFile = null
            tvCurrentSelection.text = "選択中の番組：未選択"
        }
    }

    /**
     * クエリを検索語リストに分解し、標準化する
     */
    private fun buildSearchTerms(query: String): List<String> {
        if (query.isBlank()) return emptyList()

        // 1. 全角を半角に、大文字を小文字に
        val normalized = normalizeFullWidth(query).lowercase()

        // 2. 日付表現を yyyy-MM-dd に変換して抽出
        val (dateTerms, remainingQuery) = extractDateTerms(normalized)

        // 3. 区切り文字を半角スペースに統一 (「と」など)
        val sanitized = remainingQuery
            .replace("と", " ")
            .replace("、", " ")
            .replace(",", " ")

        // 4. スペースで分割してリスト化
        val keywordTerms = sanitized.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { normalizeSearchTerm(it) }

        return dateTerms + keywordTerms
    }

    /**
     * 特定のキーワードを検索用パターンに変換
     */
    private fun normalizeSearchTerm(term: String): String {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        return when (term) {
            "今日" -> sdf.format(cal.time)
            "昨日", "きのう" -> {
                cal.add(Calendar.DATE, -1)
                sdf.format(cal.time)
            }
            "おととい", "一昨日" -> {
                cal.add(Calendar.DATE, -2)
                sdf.format(cal.time)
            }
            else -> {
                // 1. yyyy年M月D日 形式
                val fullMatcher = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").matcher(term)
                if (fullMatcher.find()) {
                    return String.format(Locale.US, "%04d-%02d-%02d", 
                        fullMatcher.group(1).toInt(), fullMatcher.group(2).toInt(), fullMatcher.group(3).toInt())
                }

                // 2. M月D日 形式 (今年)
                val mdMatcher = Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(term)
                if (mdMatcher.find()) {
                    return String.format(Locale.US, "%04d-%02d-%02d", 
                        cal.get(Calendar.YEAR), mdMatcher.group(1).toInt(), mdMatcher.group(2).toInt())
                }

                // 3. M/D 形式 (今年)
                val slashMatcher = Pattern.compile("(\\d{1,2})/(\\d{1,2})").matcher(term)
                if (slashMatcher.find()) {
                    return String.format(Locale.US, "%04d-%02d-%02d", 
                        cal.get(Calendar.YEAR), slashMatcher.group(1).toInt(), slashMatcher.group(2).toInt())
                }

                term
            }
        }
    }

    /**
     * クエリから日付表現を抽出して yyyy-MM-dd に変換し、残りの文字列を返す
     */
    private fun extractDateTerms(query: String): Pair<List<String>, String> {
        var currentQuery = query
        val dateTerms = mutableListOf<String>()

        // 1. 「先週のX曜日」の検出
        val weekdayPattern = Pattern.compile("先週の(月|火|水|木|金|土|日)曜日?")
        val weekdayMatcher = weekdayPattern.matcher(currentQuery)
        while (weekdayMatcher.find()) {
            val dayStr = weekdayMatcher.group(1)
            val dayOfWeek = when (dayStr) {
                "月" -> Calendar.MONDAY
                "火" -> Calendar.TUESDAY
                "水" -> Calendar.WEDNESDAY
                "木" -> Calendar.THURSDAY
                "金" -> Calendar.FRIDAY
                "土" -> Calendar.SATURDAY
                "日" -> Calendar.SUNDAY
                else -> -1
            }
            if (dayOfWeek != -1) {
                dateTerms.add(getLastWeekdayDate(dayOfWeek))
                currentQuery = currentQuery.replace(weekdayMatcher.group(0), " ")
            }
        }

        // 2. 「X日前」の検出
        val daysAgoPattern = Pattern.compile("(\\d+)日前")
        val daysAgoMatcher = daysAgoPattern.matcher(currentQuery)
        while (daysAgoMatcher.find()) {
            val days = daysAgoMatcher.group(1).toInt()
            dateTerms.add(getDateDaysAgo(days))
            currentQuery = currentQuery.replace(daysAgoMatcher.group(0), " ")
        }

        // 3. 「おととい」「一昨日」「昨日」「きのう」の検出 (「と」分割対策で先に処理)
        val relativeDates = mapOf(
            "おととい" to getDateDaysAgo(2),
            "一昨日" to getDateDaysAgo(2),
            "昨日" to getDateDaysAgo(1),
            "きのう" to getDateDaysAgo(1)
        )
        for ((word, date) in relativeDates) {
            if (currentQuery.contains(word)) {
                dateTerms.add(date)
                currentQuery = currentQuery.replace(word, " ")
            }
        }

        return Pair(dateTerms, currentQuery)
    }

    /**
     * 指定された日数前の日付を yyyy-MM-dd で取得
     */
    private fun getDateDaysAgo(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    /**
     * 先週の指定された曜日の日付を yyyy-MM-dd で取得
     */
    private fun getLastWeekdayDate(dayOfWeek: Int): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        // 今週の該当曜日まで戻す
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        // さらに1週間（7日）戻す
        cal.add(Calendar.DATE, -7)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    /**
     * すべての検索語に一致するか判定
     */
    private fun matchesAllTerms(file: RadioFile, terms: List<String>): Boolean {
        return terms.all { term -> matchesOneTerm(file, term) }
    }

    /**
     * 1つの単語がいずれかの項目に含まれるか判定
     */
    private fun matchesOneTerm(file: RadioFile, term: String): Boolean {
        // 全フィールドを連結して一括検索 (小文字化して比較)
        val combinedFields = (file.stationName + file.programName + file.broadcastDate + file.fileName + file.folderPath).lowercase()
        return combinedFields.contains(term)
    }

    /**
     * 全角英数字を半角に変換する
     */
    private fun normalizeFullWidth(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            if (c in '\uFF01'..'\uFF5E') {
                sb.append((c - 0xFEE0))
            } else if (c == '\u3000') {
                sb.append(' ')
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun playAudio() {
        val file = selectedRadioFile
        if (file == null || file.uriString.isEmpty()) {
            val msg = "再生する音声ファイルを選択してください"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            speak(msg)
            return
        }
        speakThen("再生します") {
            playAudioInternal()
        }
    }

    /**
     * 音声の案内なしで再生を開始する
     */
    private fun startPlaybackWithoutAnnouncement() {
        playAudioInternal()
    }

    /**
     * 音声の再生を実行する内部処理
     */
    private fun playAudioInternal() {
        val file = selectedRadioFile ?: return
        if (file.uriString.isEmpty()) return

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

    private fun checkPermissionAndStartVoice() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceRecognition()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun pausePlaybackForVoiceInput() {
        wasPlayingBeforeVoiceInput = player?.isPlaying == true
        shouldResumeAfterVoiceResponse = wasPlayingBeforeVoiceInput
        if (wasPlayingBeforeVoiceInput) {
            player?.pause()
        }
    }

    private fun resumePlaybackIfNeeded() {
        if (shouldResumeAfterVoiceResponse) {
            player?.play()
        }
        shouldResumeAfterVoiceResponse = false
    }

    private fun clearResumeAfterVoiceInput() {
        shouldResumeAfterVoiceResponse = false
    }

    private fun startVoiceRecognition() {
        pausePlaybackForVoiceInput()
        tts?.stop()

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    tvVoiceResult.text = "認識結果：お話しください..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "音声エラー"
                        SpeechRecognizer.ERROR_CLIENT -> "クライアントエラー"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "権限不足"
                        SpeechRecognizer.ERROR_NETWORK -> "ネットワークエラー"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークタイムアウト"
                        SpeechRecognizer.ERROR_NO_MATCH -> "認識できませんでした"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "認識エンジンがビジーです"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "発話タイムアウト"
                        else -> "エラー ($error)"
                    }
                    tvVoiceResult.text = "認識結果：$message"
                    speakThen("認識できませんでした") {
                        resumePlaybackIfNeeded()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val command = matches[0]
                        tvVoiceResult.text = "認識結果：$command"
                        processVoiceCommand(command)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val properIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE.toString())
        }
        speechRecognizer?.startListening(properIntent)
    }

    private fun processVoiceCommand(command: String) {
        val cmd = command.trim()
        val searchQuery = extractSearchQuery(cmd)

        when {
            cmd.contains("検索をクリア") -> {
                etSearch.setText("")
                speakThen("検索をクリアしました") {
                    resumePlaybackIfNeeded()
                }
            }
            cmd.contains("一時停止") -> {
                clearResumeAfterVoiceInput()
                speakThen("一時停止しました") {
                    player?.pause()
                }
            }
            cmd.contains("停止") -> {
                clearResumeAfterVoiceInput()
                speakThen("停止しました") {
                    player?.stop()
                    player?.seekTo(0)
                }
            }
            cmd.contains("を再生") -> {
                clearResumeAfterVoiceInput()
                val query = cmd.replace("を再生", "").trim()
                if (query.isNotEmpty()) {
                    playBySearchQuery(query)
                } else {
                    playAudio()
                }
            }
            cmd.contains("再生") -> {
                clearResumeAfterVoiceInput()
                playAudio()
            }
            searchQuery != null -> {
                etSearch.setText(searchQuery)
                // applySearchFilterの自動読み上げを避けるならここでもキャンセル
                searchReadRunnable?.let { searchReadHandler.removeCallbacks(it) }
                
                val count = filterRadioFiles(searchQuery).size
                speakThen("${searchQuery}を検索しました。検索結果は ${count} 件です") {
                    resumePlaybackIfNeeded()
                }
            }
            else -> {
                speakThen("コマンドを認識できませんでした") {
                    resumePlaybackIfNeeded()
                }
            }
        }
    }

    /**
     * 音声コマンドから検索クエリを抽出する
     */
    private fun extractSearchQuery(command: String): String? {
        var query = command.trim()

        val searchSuffixes = listOf(
            "を探してください",
            "を探して",
            "を探す",
            "で探して",
            "を検索してください",
            "を検索して",
            "を検索",
            "で検索して",
            "で検索",
            "検索して",
            "検索",
            "を調べてください",
            "を調べて",
            "調べてください",
            "調べて"
        )

        for (suffix in searchSuffixes) {
            if (query.endsWith(suffix)) {
                query = query.removeSuffix(suffix).trim()
                return query.ifEmpty { null }
            }
        }

        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        
        searchReadRunnable?.let { searchReadHandler.removeCallbacks(it) }
        searchReadRunnable = null
        
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false

        speechRecognizer?.destroy()
        speechRecognizer = null
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
            
            selectedRadioFile = null
            tvCurrentSelection.text = "選択中の番組：未選択"
            
            player?.stop()
            player?.seekTo(0)
            
            if (allRadioFiles.isEmpty()) {
                val msg = "音声ファイルが見つかりませんでした"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                speak(msg)
            } else {
                speak("音声ファイルが ${allRadioFiles.size} 件見つかりました")
            }
            
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
