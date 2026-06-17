package com.koeradi.app

/**
 * ラジオ音声ファイルの情報を保持するデータクラス
 */
data class RadioFile(
    val stationName: String, // ラジオ局
    val programName: String, // 番組名
    val broadcastDate: String, // 放送日
    val fileName: String      // ファイル名
)
