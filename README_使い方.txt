BABYFACE 広陵店 P31専用サイネージアプリ

このアプリはChromeを使わず、AndroidのWebViewでGitHub Pagesのサイネージを全画面表示します。
アドレスバーは表示されません。

表示先:
https://aglio0o0li0.github.io/babyface-koryo-signage/

特徴:
・縦画面固定
・ステータスバー/ナビゲーションバーを隠す
・画面スリープ防止
・戻るボタンを無効化
・起動ごとにURLへキャッシュバスターを付けて古いindex.htmlを避ける
・動画/GIFはアプリ側では使用しない

APKの作成:
1. このプロジェクトをGitHubへアップロード
2. Actions → Build APK → Run workflow
3. 完了後 Artifacts の BABYFACE-P31-Signage-apk をダウンロード
4. app-release.apk をP31へコピーしてインストール

※この環境ではAndroid SDK/Gradleが入っていないため、APKそのものではなくGitHub Actionsでビルドできるプロジェクトを用意しています。
