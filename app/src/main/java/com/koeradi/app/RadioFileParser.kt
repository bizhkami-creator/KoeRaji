package com.koeradi.app

import java.util.regex.Pattern

/**
 * ファイル名やフォルダパスからラジオ情報を解析するクラス
 */
object RadioFileParser {

    private const val UNKNOWN = "未判定"

    /**
     * フォルダ情報とファイル名から RadioFile オブジェクトを生成する
     */
    fun createRadioFile(
        rootFolderName: String,
        folderPath: String,
        fileName: String,
        uriString: String
    ): RadioFile {
        var stationName = UNKNOWN
        var programName = UNKNOWN
        var broadcastDate = parseDate(fileName)

        // 1. フォルダ構造からの判定
        val pathParts = if (folderPath.isEmpty()) emptyList() else folderPath.split("/")
        
        // ルートフォルダ名が「Radio」などの汎用名でない場合、それを局名とする
        val isGenericRoot = rootFolderName.contains("Radio", ignoreCase = true) || 
                            rootFolderName.equals("Download", ignoreCase = true) || 
                            rootFolderName.equals("Music", ignoreCase = true)

        if (isGenericRoot) {
            if (pathParts.isNotEmpty()) {
                stationName = pathParts[0]
                if (pathParts.size >= 2) {
                    programName = pathParts[1]
                }
            }
        } else {
            // ルートフォルダ名自体を局名として採用
            stationName = rootFolderName
            if (pathParts.isNotEmpty()) {
                // その下の第1階層を番組名とする
                programName = pathParts[0]
            }
        }

        // 2. ファイル名からの解析と補完
        // [ニッポン放送]のような角括弧をチェック
        val bracketPattern = Pattern.compile("\\[(.*?)\\]")
        val bracketMatcher = bracketPattern.matcher(fileName)
        
        if (bracketMatcher.find()) {
            // 角括弧があれば局名を上書き/取得
            val sName = bracketMatcher.group(1)
            if (sName != null) stationName = sName
            
            // 角括弧の後ろから番組名を抽出
            val afterBracket = fileName.substring(bracketMatcher.end())
            // (TimeFree) または _202x (日付の始まり) または拡張子の前までを番組名とする
            val programPattern = Pattern.compile("(.*?)(?:\\(TimeFree\\)|_202\\d|\\.)")
            val programMatcher = programPattern.matcher(afterBracket)
            if (programMatcher.find()) {
                val pName = programMatcher.group(1)
                val candidate = pName?.trim() ?: ""
                if (candidate.isNotEmpty()) {
                    programName = candidate
                }
            }
        } else if (stationName == UNKNOWN || programName == UNKNOWN) {
            // 角括弧がない場合の従来のアンダースコア区切り解析
            val nameWithoutExtension = fileName.substringBeforeLast(".")
            val parts = nameWithoutExtension.split("_")
            if (parts.size >= 2) {
                if (stationName == UNKNOWN) stationName = parts[0]
                if (programName == UNKNOWN) programName = parts[1]
            }
        }

        // 3. 日付が未判定ならフォルダパスからも探す
        if (broadcastDate == UNKNOWN) {
            broadcastDate = parseDate(folderPath)
        }

        return RadioFile(
            stationName = stationName,
            programName = programName,
            broadcastDate = broadcastDate,
            fileName = fileName,
            uriString = uriString,
            folderPath = folderPath
        )
    }

    /**
     * 文字列から yyyy-MM-dd または yyyyMMdd 形式の日付を探して yyyy-MM-dd で返す
     */
    private fun parseDate(input: String): String {
        val hyphenPattern = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})")
        val hyphenMatcher = hyphenPattern.matcher(input)
        if (hyphenMatcher.find()) {
            return "${hyphenMatcher.group(1)}-${hyphenMatcher.group(2)}-${hyphenMatcher.group(3)}"
        }

        val simplePattern = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})")
        val simpleMatcher = simplePattern.matcher(input)
        if (simpleMatcher.find()) {
            return "${simpleMatcher.group(1)}-${simpleMatcher.group(2)}-${simpleMatcher.group(3)}"
        }

        return UNKNOWN
    }
}
