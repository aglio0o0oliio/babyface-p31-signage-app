package com.babyface.koryosignage;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private static final String SIGNAGE_URL =
            "https://aglio0o0oliio.github.io/babyface-koryo-signage/";

    private WebView webView;
    private final Handler handler = new Handler();

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

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setWebViewClient(new WebViewClient());

        WebSettings s = webView.getSettings();

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);

        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);

        // 動画の自動再生を許可
        s.setMediaPlaybackRequiresUserGesture(false);

        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        setContentView(webView);

        // 起動直後はWi-Fi接続が完全に確立するまで少し待つ
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                loadSignage();
            }
        }, 5000);
    }

    private void loadSignage() {
        webView.loadUrl(SIGNAGE_URL);

        // ページ読み込み後、動画再生を複数回試行
        retryVideo(3000);
        retryVideo(6000);
        retryVideo(10000);
        retryVideo(15000);
    }

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
                        + "v.load();"
                        + "var p=v.play();"
                        + "if(p){p.catch(function(){});}"
                        + "}"
                        + "})();",
                        null
                );

            }
        }, delay);
    }

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
    }

    @Override
    public void onBackPressed() {
        // 専用サイネージ端末なので戻る操作を無効化
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        if (webView != null) {
            webView.destroy();
        }

        super.onDestroy();
    }
}
