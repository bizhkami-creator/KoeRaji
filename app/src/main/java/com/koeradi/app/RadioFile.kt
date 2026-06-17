package com.koeradi.app

/**
 * ラジオ音声ファイルの情報を保持するデータクラス
 */
data class RadioFile(
    val stationName: String, // ラジオ局
    val programName: String, // 番組名
    val broadcastDate: String, // 放送日
    val fileName: String,      // ファイル名
    val uriString: String,     // ファイルの場所(Uri) - 再生時に使用
    val folderPath: String     // 選択フォルダからの相対パス
)
