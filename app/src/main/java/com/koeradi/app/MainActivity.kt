package com.koeradi.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

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

        // ダミーデータの作成
        val dummyData = listOf(
            RadioFile("TBSラジオ", "荻上チキ Session", "2026-06-15", "TBSラジオ_荻上チキSession_2026-06-15.m4a"),
            RadioFile("NHKラジオ第1", "ラジオ深夜便", "2026-06-14", "NHKラジオ第1_ラジオ深夜便_2026-06-14.m4a"),
            RadioFile("ニッポン放送", "オールナイトニッポン", "2026-06-13", "ニッポン放送_オールナイトニッポン_2026-06-13.mp3")
        )

        // RecyclerViewの設定
        val recyclerView: RecyclerView = findViewById(R.id.rvRadioFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RadioFileAdapter(dummyData)

        // ボタンの初期化（Day 1ではトースト表示のみ）
        findViewById<Button>(R.id.btnSelectFolder).setOnClickListener {
            Toast.makeText(this, "フォルダ選択機能は後日実装します", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            Toast.makeText(this, "再生機能は後日実装します", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPause).setOnClickListener {
            Toast.makeText(this, "一時停止機能は後日実装します", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            Toast.makeText(this, "停止機能は後日実装します", Toast.LENGTH_SHORT).show()
        }
    }
}
