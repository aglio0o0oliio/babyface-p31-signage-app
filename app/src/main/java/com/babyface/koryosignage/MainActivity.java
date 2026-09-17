package com.babyface.koryosignage;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.Context;

public class MainActivity extends Activity {

    private static final String TAG = "BABYFACE";
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

        Log.d(TAG, "onCreate START");

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

        Log.d(TAG, "WebView created");

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Log.d(TAG, "shouldOverrideUrlLoading: " + request.getUrl());
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "onPageStarted: " + url);
            }

            // Android 6.0以降
            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error) {

                super.onReceivedError(view, request, error);

                Log.e(TAG,
                        "onReceivedError: mainFrame=" + request.isForMainFrame()
                                + " code=" + error.getErrorCode()
                                + " description=" + error.getDescription()
                                + " url=" + request.getUrl());

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

                Log.e(TAG,
                        "onReceivedError(legacy): code=" + errorCode
                                + " description=" + description
                                + " url=" + failingUrl);

                pageLoaded = false;

                scheduleRetry();
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    android.webkit.WebResourceResponse errorResponse) {

                super.onReceivedHttpError(view, request, errorResponse);

                Log.e(TAG,
                        "onReceivedHttpError: mainFrame=" + request.isForMainFrame()
                                + " status=" + errorResponse.getStatusCode()
                                + " reason=" + errorResponse.getReasonPhrase()
                                + " url=" + request.getUrl());
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                super.onPageFinished(view, url);

                pageLoaded = true;
                retryScheduled = false;

                Log.d(TAG, "onPageFinished: " + url);

                // ページタイトル確認
                Log.d(TAG, "title=" + view.getTitle());

                // 動画再生を試行
                retryVideo(1000);
                retryVideo(3000);
                retryVideo(6000);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG,
                        "JS console: "
                                + consoleMessage.message()
                                + " @"
                                + consoleMessage.sourceId()
                                + ":"
                                + consoleMessage.lineNumber());
                return super.onConsoleMessage(consoleMessage);
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

        Log.d(TAG, "setContentView completed");

        // ネットワーク監視開始
        setupNetworkMonitoring();

        // 起動直後はWi-Fi確立を待つ
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "5-second startup loadSignage()");
                loadSignage();
            }
        }, 5000);

        Log.d(TAG, "onCreate END");
    }

    /**
     * サイネージ読み込み
     */
    private void loadSignage() {

        Log.d(TAG, "loadSignage() START");

        if (webView == null) {
            Log.e(TAG, "loadSignage(): webView == null");
            return;
        }

        boolean networkAvailable = isNetworkAvailable();
        Log.d(TAG, "loadSignage(): isNetworkAvailable=" + networkAvailable);

        if (!networkAvailable) {
            Log.w(TAG, "loadSignage(): network unavailable -> scheduleRetry()");
            scheduleRetry();
            return;
        }

        retryScheduled = false;

        Log.d(TAG, "loadSignage(): loadUrl=" + SIGNAGE_URL);
        webView.loadUrl(SIGNAGE_URL);
    }

    /**
     * WebViewエラー時の再試行
     */
    private void scheduleRetry() {

        if (retryScheduled) {
            Log.d(TAG, "scheduleRetry(): already scheduled");
            return;
        }

        retryScheduled = true;

        Log.w(TAG, "scheduleRetry(): retry in " + NETWORK_CHECK_INTERVAL + "ms");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                retryScheduled = false;

                if (webView == null) {
                    Log.e(TAG, "scheduleRetry(): webView == null");
                    return;
                }

                if (isNetworkAvailable()) {
                    Log.d(TAG, "scheduleRetry(): network available -> loadSignage()");
                    loadSignage();
                } else {
                    Log.w(TAG, "scheduleRetry(): network still unavailable");
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
            Log.e(TAG, "setupNetworkMonitoring(): ConnectivityManager == null");
            return;
        }

        Log.d(TAG, "setupNetworkMonitoring(): registering NetworkCallback");

        networkCallback =
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(Network network) {
                        super.onAvailable(network);

                        Log.d(TAG, "NetworkCallback.onAvailable: " + network);

                        // ネットワーク復旧時に自動再読み込み
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {

                                if (webView == null) {
                                    Log.e(TAG, "onAvailable delayed: webView == null");
                                    return;
                                }

                                Log.d(TAG, "onAvailable delayed -> loadSignage()");
                                loadSignage();
                            }
                        }, 2000);
                    }

                    @Override
                    public void onLost(Network network) {
                        super.onLost(network);

                        Log.w(TAG, "NetworkCallback.onLost: " + network);

                        // ネットワークが切れても
                        // すぐに画面を消さない
                        scheduleRetry();
                    }
                };

        try {
            connectivityManager.registerDefaultNetworkCallback(
                    networkCallback
            );
            Log.d(TAG, "NetworkCallback registered");
        } catch (Exception e) {
            Log.e(TAG,
                    "NetworkCallback registration failed: "
                            + e.getClass().getName()
                            + ":"
                            + e.getMessage(),
                    e);

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
            Log.e(TAG, "isNetworkAvailable(): ConnectivityManager == null");
            return false;
        }

        try {

            Network network =
                    connectivityManager.getActiveNetwork();

            if (network == null) {
                Log.w(TAG, "isNetworkAvailable(): activeNetwork == null");
                return false;
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);

            if (capabilities == null) {
                Log.w(TAG, "isNetworkAvailable(): capabilities == null");
                return false;
            }

            boolean internet = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
            );

            boolean validated = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
            );

            boolean wifi = capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
            );

            Log.d(TAG,
                    "isNetworkAvailable(): internet=" + internet
                            + " validated=" + validated
                            + " wifi=" + wifi);

            return internet;

        } catch (Exception e) {

            Log.e(TAG,
                    "isNetworkAvailable() exception: "
                            + e.getClass().getName()
                            + ":"
                            + e.getMessage(),
                    e);

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
                    Log.e(TAG, "retryVideo(): webView == null");
                    return;
                }

                Log.d(TAG, "retryVideo(): delay=" + delay);

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

        Log.d(TAG, "onWindowFocusChanged: hasFocus=" + hasFocus);

        if (hasFocus) {
            enterImmersive();
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        Log.d(TAG, "onResume");

        enterImmersive();

        // アプリ復帰時にもネットワークを確認
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (webView == null) {
                    Log.e(TAG, "onResume delayed: webView == null");
                    return;
                }

                Log.d(TAG, "onResume delayed: pageLoaded=" + pageLoaded);

                if (!pageLoaded) {
                    loadSignage();
                }
            }
        }, 1000);
    }

    @Override
    public void onBackPressed() {

        // 専用サイネージ端末なので戻る操作を無効化
        Log.d(TAG, "onBackPressed ignored");
    }

    @Override
    protected void onDestroy() {

        Log.d(TAG, "onDestroy");

        handler.removeCallbacksAndMessages(null);

        // ネットワーク監視解除
        if (connectivityManager != null
                && networkCallback != null) {

            try {
                connectivityManager.unregisterNetworkCallback(
                        networkCallback
                );
                Log.d(TAG, "NetworkCallback unregistered");
            } catch (Exception e) {
                Log.w(TAG,
                        "unregisterNetworkCallback exception: "
                                + e.getMessage());
                // 既に解除済みの場合は無視
            }
        }

        if (webView != null) {

            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();

            Log.d(TAG, "WebView destroyed");
        }

        super.onDestroy();
    }
}
