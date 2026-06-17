# 03_開発ログ (Development Log)

## 2026-06-17 Day1 完了ログ

### 実装したこと

* Android StudioでKoeRadiプロジェクトを作成した
* Empty Views Activityを使用し、Kotlin + XMLレイアウトで開発する方針にした
* MainActivity.ktを作成・確認した
* activity_main.xmlで基本画面を作成した
* RadioFile.ktで音声ファイル情報を保持するデータクラスを作成した
* RadioFileAdapter.ktでRecyclerView表示用のAdapterを作成した
* item_radio_file.xmlで音声ファイル1件分の表示レイアウトを作成した
* RecyclerViewにダミーのラジオ音声ファイル3件を表示した
* フォルダ選択、再生、一時停止、停止ボタンを配置した
* Day1時点では各ボタンの処理はToast表示のみとした

### 動作確認

* 実機でアプリが起動することを確認した
* 画面上部に「こえラジ」と表示されることを確認した
* 「フォルダを選択」ボタンが表示されることを確認した
* 「フォルダ未選択」と表示されることを確認した
* 検索欄が表示されることを確認した
* ダミー音声ファイル3件が一覧表示されることを確認した
* 画面下部に「再生」「一時停止」「停止」ボタンが表示されることを確認した

### 発生した問題

* ダークモード環境では、一部の文字色が見えにくい可能性がある
* ただしDay1では機能確認を優先し、文字色調整は後日対応とする

### 解決したこと

* res/layoutが無い問題は、Compose用のEmpty Activityではなく、XML用のEmpty Views Activityで作成し直すことで解決した
* MainActivity.kt、activity_main.xml、Adapter、itemレイアウトのID接続を確認し、RecyclerView表示まで進められた

### 次回やること

* Day2ではフォルダ選択機能を実装する
* Storage Access Frameworkを使ってユーザーが音声フォルダを選べるようにする
* 選択中フォルダ名を画面に表示する
* 可能であれば選択したフォルダ内のファイル一覧取得まで進める
