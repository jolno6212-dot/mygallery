# MyGallery

Android向けの画像・動画ギャラリーアプリ。端末内のメディアをフォルダ単位で閲覧し、リネーム・移動・回転・リサイズ・削除といったファイル操作をアプリ内で完結できます。

Kotlin と Jetpack Compose で実装しています。

<!--
スクリーンショット追加欄:
画像を docs/images/ に配置したうえで、この位置に3列のテーブルを追加してください
（フォルダ一覧 / サムネイル一覧 / ビューア）。
実機またはエミュレータでの撮影が必要です。
-->

## 主な機能

- **フォルダ単位の閲覧** — 端末内のメディアをフォルダごとに一覧表示
- **サムネイル一覧** — 日付グルーピング、複数のソート基準に対応
- **ビューア** — スワイプでの切り替え、ピンチズーム／パン、ダブルタップでのズーム切り替え
- **ファイル操作** — リネーム、移動、コピー、回転、リサイズ、撮影日時の変更、削除
- **複数選択** — 長押しで選択モードに入り、一括で共有・移動・削除
- **お気に入り** — 個別のマーキングと絞り込み
- **フォルダの非表示** — `.nomedia` によるメディアスキャンからの除外
- **他アプリ連携** — 共有、他アプリで開く／編集、カメラ起動、外部プレイヤーでの再生
- **ショートカット** — ホーム画面へのフォルダショートカット作成
- **アプリ内アップデート** — GitHub Releases を参照した更新確認とインストール

## 技術構成

| 項目 | 内容 |
|---|---|
| 言語 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 画像読み込み | Coil（`coil-video` により動画サムネイルにも対応） |
| 非同期処理 | Kotlin Coroutines |
| メディアアクセス | MediaStore / ContentResolver / ExifInterface |
| compileSdk / targetSdk | 35 |
| minSdk | 26（Android 8.0） |
| JVM ターゲット | 17 |
| ビルド | Gradle Kotlin DSL |

## 実装上の要点

Scoped Storage への対応、ピンチズームの自前実装、アプリ内アップデート機構などの詳細は、
**[docs/portfolio/mygallery.md](docs/portfolio/mygallery.md)** に技術資料としてまとめています。

抜粋:

- **Scoped Storage 対応** — 書き込み結果を `sealed class WriteResult` で型として表現し、`RecoverableSecurityException` を捕捉してシステムの許可ダイアログへ橋渡し。標準メディアディレクトリ外への移動では `MANAGE_EXTERNAL_STORAGE` へフォールバック
- **移動処理** — `RELATIVE_PATH` の `update()` による移動（コピー＆削除ではないため元データが保全される）
- **ピンチズーム／パン** — `awaitPointerEvent` によるジェスチャ処理。倍率を1〜5倍にクランプし、パン量を画像境界内に制限。ズーム中は Pager のスワイプを抑制して操作の競合を解消
- **撮影日時の変更** — MediaStore の `DATE_TAKEN` と EXIF の `DateTimeOriginal` の双方を更新して整合を維持

## ビルド

Android Studio で開くか、コマンドラインから実行します。

```bash
./gradlew assembleDebug
```

Android SDK（compileSdk 35）と JDK 17 が必要です。

## 構成

```
app/src/main/java/com/jolno/mygallery/
├── MainActivity.kt              画面遷移と権限要求のハンドリング
├── data/                        MediaStore操作、設定・お気に入りの永続化、更新確認
└── ui/screens/                  フォルダ一覧、サムネイル一覧、ビューア、各種ダイアログ
```

## ライセンス

未設定です。利用をご検討の際はお問い合わせください。
