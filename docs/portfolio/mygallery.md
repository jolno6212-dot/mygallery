# ポートフォリオ: MyGallery（Androidギャラリーアプリ）

> 発注者向けの技術説明資料。提案文に本ファイルのURLを添付して使用する。
> リポジトリ: https://github.com/jolno6212-dot/mygallery

---

## 概要

Android向けの画像・動画ギャラリーアプリ。端末内のメディアをフォルダ単位で閲覧・整理し、リネーム／移動／回転／リサイズ／削除といったファイル操作をアプリ内で完結できる。

| 項目 | 内容 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| コード規模 | 約3,000行（Kotlin、テスト・生成物を除く） |
| 対応OS | Android 8.0（API 26）〜 Android 15（API 35） |
| ビルド | Gradle Kotlin DSL、JVM 17 |
| 配布 | GitHub Releases（アプリ内アップデート機構を実装） |

---

## 技術的な要点

### 1. Scoped Storage への対応

Android 10以降のScoped Storageは、メディアファイルの書き込みに端末利用者の明示的な許可を要求する。本アプリでは書き込み結果を型で表現し、権限が必要なケースを呼び出し側に安全に伝播させている。

```kotlin
sealed class WriteResult {
    data object Success : WriteResult()
    data class NeedsPermission(val intentSender: IntentSender) : WriteResult()
    data object NeedsFullFileAccess : WriteResult()
    data object Failed : WriteResult()
}
```

- `RecoverableSecurityException` を捕捉し、システムの許可ダイアログ（`IntentSender`）へ橋渡し
- 標準メディアディレクトリ（DCIM / Pictures / Movies / Download / Documents）の外への移動には `MANAGE_EXTERNAL_STORAGE` が必要になるため、この条件を判定して適切な権限要求へフォールバック
- ファイルシステム経由での移動時は `MediaScannerConnection` でメディアDBを再同期

**この領域は Android のバージョンごとに挙動が異なり、実装を誤ると「一部の端末でだけ動かない」という不具合が出やすい箇所。** 実機での検証を前提とした実装経験がある。

### 2. MediaStore API によるファイル操作

`ContentResolver` / `MediaStore` を用いた読み書きを実装。

| 機能 | 実装内容 |
|---|---|
| 一覧取得 | バケット（フォルダ）単位でのグルーピング、最新更新日時によるソート |
| 移動 | `RELATIVE_PATH` の `update()` による移動（コピー＆削除ではないため元データが保全される） |
| リネーム | `DISPLAY_NAME` の更新 |
| 回転 | `ExifInterface` による向き情報の読み取りと `Matrix` によるビットマップ変換 |
| リサイズ | `BitmapFactory` によるデコードと再エンコード |
| 撮影日時変更 | EXIF の `DateTimeOriginal` と MediaStore 双方の整合を維持 |
| 削除 | `createDeleteRequest` によるシステムダイアログ経由の削除 |
| フォルダ非表示 | `.nomedia` 方式による除外 |

### 3. Jetpack Compose によるUI実装

- `LazyVerticalGrid` によるサムネイル一覧（大量データでの再利用を前提とした構成）
- `HorizontalPager` によるスワイプ切り替えビューア
- **ピンチズーム・パン**の自前実装（`awaitPointerEvent` によるジェスチャ処理、倍率を1〜5倍にクランプし、パン量を画像境界内に制限）。ズーム中はPagerのスワイプを抑制し、操作の競合を解消
- ダブルタップによるズームのトグル
- **複数選択モード**（長押しで選択開始、選択中は一括共有・移動・削除に切り替わるトップバー）
- 日付によるグルーピング表示、複数のソート基準

### 4. アプリ内アップデート機構

GitHub Releases API を利用した自己更新を実装。

- `https://api.github.com/repos/{owner}/{repo}/releases/latest` を取得し、タグ名から取得したバージョン番号を `BuildConfig.VERSION_CODE` と比較
- 新しいバージョンがあれば APK アセットをダウンロード
- `FileProvider` 経由で `content://` URI を発行し、インストーラへ引き渡し（`REQUEST_INSTALL_PACKAGES` 権限とストアを介さない配布に対応）
- タイムアウト設定と `runCatching` による失敗時のフォールバック

### 5. 他アプリ連携

`Intent` を用いた連携を実装。共有時は選択項目の内容から MIME タイプを推論する（全て画像なら `image/*`、全て動画なら `video/*`、混在なら `*/*`）。

- 共有 / 他アプリで開く / 他アプリで編集 / 「他のアプリで使用」
- カメラ起動、外部プレイヤーでの動画再生
- `ShortcutManagerCompat` によるホーム画面へのフォルダショートカット作成

### 6. 非同期処理とアーキテクチャ

- Kotlin Coroutines（I/O処理は `Dispatchers.IO` に隔離し、UIスレッドをブロックしない）
- データ層（`data/`）とUI層（`ui/screens/`）の分離
- 画像読み込みは Coil（動画サムネイル対応の `coil-video` を含む）

---

## ファイル構成

```
app/src/main/java/com/jolno/mygallery/
├── MainActivity.kt              画面遷移と権限要求のハンドリング
├── MyGalleryApplication.kt
├── data/
│   ├── MediaRepository.kt       MediaStore操作の中核（約320行）
│   ├── MediaActions.kt          他アプリ連携・Intent処理
│   ├── MediaModels.kt           ドメインモデル
│   ├── AppSettingsStore.kt      設定の永続化
│   ├── FavoritesStore.kt        お気に入り管理
│   ├── SortUtils.kt             ソートロジック
│   └── UpdateChecker.kt         アプリ内アップデート
└── ui/screens/
    ├── FolderListScreen.kt      フォルダ一覧（約690行）
    ├── MediaGridScreen.kt       サムネイル一覧・複数選択（約620行）
    ├── MediaViewerScreen.kt     ビューア・ズーム（約490行）
    ├── MediaDialogs.kt          各種操作ダイアログ
    └── DateGroupUtils.kt        日付グルーピング
```

---

## この実績が示せること

提案時には、案件の内容に応じて以下の対応関係で言及する。

| 案件の要件 | 対応する実装経験 |
|---|---|
| Androidアプリの改修・機能追加 | Compose + Material3 での画面実装一式 |
| ファイル・ストレージまわりの不具合 | Scoped Storage、権限フォールバック、MediaStore |
| 画像処理 | EXIF読み書き、回転、リサイズ |
| 複雑なタッチ操作・UI | ピンチズーム／パンの自前実装、ジェスチャ競合の解消 |
| 外部API連携 | GitHub REST API、JSONパース、タイムアウトとエラー処理 |
| アプリ配布・更新 | FileProvider、ストア外配布、バージョン比較 |
| 既存コードの理解・改修 | 3,000行規模のコードベースを設計から実装まで単独で構築 |

---

## 更新について

機能追加やリファクタリングを行った際は、本ファイルの記述も更新する。**提案文で言及する内容と実際のコードが食い違うと、確認された時点で信用を失う。** 記載は常に実装の事実に合わせる。
