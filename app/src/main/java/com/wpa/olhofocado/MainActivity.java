package com.wpa.olhofocado;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {
    public static volatile boolean isAppInForeground = false;
    private WebView webView;
    private String pendingWidgetTarget = null;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_RESULT_CODE = 202;
    private static final int NATIVE_JSON_PICK_CODE = 203;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (getIntent() != null && getIntent().hasExtra("open_target")) {
            pendingWidgetTarget = getIntent().getStringExtra("open_target");
        }
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // BLOQUEIO TOTAL DE NAVEGAÇÃO WEB EXTERNA (Impede abrir YouTube, Google ou sites dentro ou fora do WebView)
                if (url != null && !url.startsWith("file:///android_asset/")) {
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String u = request.getUrl().toString();
                    if (!u.startsWith("file:///android_asset/")) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (url != null && !url.startsWith("file:///android_asset/") && !url.startsWith("about:blank")) {
                    view.stopLoading();
                    view.loadUrl("file:///android_asset/index.html");
                    return;
                }
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (pendingWidgetTarget != null && !pendingWidgetTarget.isEmpty()) {
                    final String target = pendingWidgetTarget;
                    pendingWidgetTarget = null;
                    view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            view.evaluateJavascript(
                                "if(window.handleWidgetNavigation){window.handleWidgetNavigation('" + target + "');}",
                                null
                            );
                        }
                    }, 180);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(Intent.createChooser(intent, "Escolher Arquivo de Backup (.json)"), FILE_CHOOSER_RESULT_CODE);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    public void launchNativeJsonPicker() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(Intent.createChooser(intent, "Escolher Arquivo de Backup (.json)"), NATIVE_JSON_PICK_CODE);
                } catch (Exception ignored) {}
            }
        });
    }

    private void readUriAndDeliverToWebView(Uri uri) {
        if (uri == null || webView == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int nRead;
            while ((nRead = is.read(chunk, 0, chunk.length)) != -1) {
                buffer.write(chunk, 0, nRead);
            }
            is.close();
            byte[] bytes = buffer.toByteArray();
            final String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(
                        "if(window.restoreFromBase64Json){window.restoreFromBase64Json('" + b64 + "');}",
                        null
                    );
                }
            });
        } catch (Exception ignored) {}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                    readUriAndDeliverToWebView(data.getData());
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
        } else if (requestCode == NATIVE_JSON_PICK_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                readUriAndDeliverToWebView(data.getData());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        OlhoFocadoAdminReceiver.syncAllProtections(this);
        if (webView != null) {
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("if(window.refreshGuardStatusUI){window.refreshGuardStatusUI();}", null);
                }
            }, 150);
        }
    }

    @Override
    protected void onPause() {
        isAppInForeground = false;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("open_target") && webView != null) {
            final String target = intent.getStringExtra("open_target");
            if (target != null && !target.isEmpty()) {
                webView.evaluateJavascript(
                    "if(window.handleWidgetNavigation){window.handleWidgetNavigation('" + target + "');}",
                    null
                );
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
