package com.babyface.koryosignage;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.Context;

public class MainActivity extends Activity {

    private static final String SIGNAGE_URL =
            "https://aglio0o0oliio.github.io/babyface-koryo-signage/";

    // ネットワーク復旧チェック間隔
    private static final long NETWORK_CHECK_INTERVAL = 10000;

    private WebView webView;
    private final Handler handler = new Handler();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private boolean pageLoaded = false;
    private boolean retryScheduled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        enterImmersive();

        // WebView作成
        webView = new WebView(this);

        webView.setBackgroundColor(Color.WHITE);

        webView.setWebViewClient(new WebViewClient() {

            // Android 6.0以降
            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error) {

                super.onReceivedError(view, request, error);

                // メインページのエラーだけ処理する
                if (request.isForMainFrame()) {
                    pageLoaded = false;

                    // エラー画面を長時間表示したままにしない
                    scheduleRetry();
                }
            }

            // 古いAndroid向け
            @Override
            public void onReceivedError(
                    WebView view,
                    int errorCode,
                    String description,
                    String failingUrl) {

                super.onReceivedError(
                        view,
                        errorCode,
                        description,
                        failingUrl
                );

                pageLoaded = false;

                scheduleRetry();
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                super.onPageFinished(view, url);

                pageLoaded = true;
                retryScheduled = false;

                // 動画再生を試行
                retryVideo(1000);
                retryVideo(3000);
                retryVideo(6000);
            }
        });

        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);

        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);

        // 動画自動再生
        s.setMediaPlaybackRequiresUserGesture(false);

        // 通常のキャッシュを使用
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        setContentView(webView);

        // ネットワーク監視開始
        setupNetworkMonitoring();

        // 起動直後はWi-Fi確立を待つ
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadSignage();
            }
        }, 5000);
    }

    /**
     * サイネージ読み込み
     */
    private void loadSignage() {

        if (webView == null) {
            return;
        }

        if (!isNetworkAvailable()) {
            scheduleRetry();
            return;
        }

        retryScheduled = false;

        webView.loadUrl(SIGNAGE_URL);
    }

    /**
     * WebViewエラー時の再試行
     */
    private void scheduleRetry() {

        if (retryScheduled) {
            return;
        }

        retryScheduled = true;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                retryScheduled = false;

                if (webView == null) {
                    return;
                }

                if (isNetworkAvailable()) {
                    loadSignage();
                } else {
                    scheduleRetry();
                }

            }
        }, NETWORK_CHECK_INTERVAL);
    }

    /**
     * ネットワーク状態監視
     */
    private void setupNetworkMonitoring() {

        connectivityManager =
                (ConnectivityManager)
                        getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return;
        }

        networkCallback =
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        super.onAvailable(network);

                        // ネットワーク復旧時に自動再読み込み
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {

                                if (webView == null) {
                                    return;
                                }

                                loadSignage();
                            }
                        }, 2000);
                    }

                    @Override
                    public void onLost(Network network) {
                        super.onLost(network);

                        // ネットワークが切れても
                        // すぐに画面を消さない
                        scheduleRetry();
                    }
                };

        try {
            connectivityManager.registerDefaultNetworkCallback(
                    networkCallback
            );
        } catch (Exception e) {
            // 端末側でNetworkCallbackが使えない場合
            // リトライ方式だけで動作
        }
    }

    /**
     * 現在ネットワークが使用可能か確認
     */
    private boolean isNetworkAvailable() {

        if (connectivityManager == null) {
            connectivityManager =
                    (ConnectivityManager)
                            getSystemService(Context.CONNECTIVITY_SERVICE);
        }

        if (connectivityManager == null) {
            return false;
        }

        try {

            Network network =
                    connectivityManager.getActiveNetwork();

            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);

            if (capabilities == null) {
                return false;
            }

            return capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
            );

        } catch (Exception e) {

            return false;
        }
    }

    /**
     * 動画自動再生
     */
    private void retryVideo(long delay) {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (webView == null) {
                    return;
                }

                webView.evaluateJavascript(
                        "(function(){"
                        + "var v=document.querySelector('video');"
                        + "if(v){"
                        + "v.muted=true;"
                        + "var p=v.play();"
                        + "if(p){p.catch(function(){});}"
                        + "}"
                        + "})();",
                        null
                );

            }
        }, delay);
    }

    /**
     * 全画面表示
     */
    private void enterImmersive() {

        getWindow().getDecorView().setSystemUiVisibility(

                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {

        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            enterImmersive();
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        enterImmersive();

        // アプリ復帰時にもネットワークを確認
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (webView == null) {
                    return;
                }

                if (!pageLoaded) {
                    loadSignage();
                }
            }
        }, 1000);
    }

    @Override
    public void onBackPressed() {

        // 専用サイネージ端末なので戻る操作を無効化
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        // ネットワーク監視解除
        if (connectivityManager != null
                && networkCallback != null) {

            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
            } catch (Exception e) {
                // 既に解除済みの場合は無視
            }
        }

        if (webView != null) {

            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }

        super.onDestroy();
    }
}
