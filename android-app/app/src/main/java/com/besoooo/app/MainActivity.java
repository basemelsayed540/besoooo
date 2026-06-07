package com.besoooo.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.besoooo.app.databinding.ActivityMainBinding;
import com.besoooo.app.databinding.LayoutErrorBinding;
import com.besoooo.app.databinding.LayoutNoInternetBinding;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.net.URLDecoder;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BesooooFCM";
    private static final String NOTIFICATION_CHANNEL_ID = "besoooo_notifications";
    private ActivityMainBinding binding;
    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LayoutErrorBinding errorBinding;
    private LayoutNoInternetBinding noInternetBinding;

    private static final String BASE_URL = "https://basemelsayed540.github.io/besoooo/";
    private static final int FILE_CHOOSER_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private ValueCallback<Uri[]> filePathCallback;
    private ValueCallback<Uri> filePathCallbackLegacy;

    private boolean isLoadingError = false;
    private boolean isOffline = false;

    private ConnectivityReceiver connectivityReceiver;
    private Handler retryHandler = new Handler();

    @SuppressLint({"SetJavaScriptEnabled", "ObsoleteSdkInt"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        webView = binding.webView;
        swipeRefresh = binding.swipeRefresh;
        progressBar = binding.progressBar;
        errorBinding = binding.errorView;
        noInternetBinding = binding.noInternetView;

        setupSwipeRefresh();
        setupWebView();
        setupScrollListener();
        setupErrorButtons();
        registerConnectivityReceiver();

        createNotificationChannel();
        requestNotificationPermission();
        logFirebaseToken();
        subscribeToNotificationsTopic();

        loadUrl();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App notifications",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("App notification channel");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void logFirebaseToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }
                Log.d(TAG, "FCM token: " + task.getResult());
            });
    }

    private void subscribeToNotificationsTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic("all")
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Subscribed to FCM topic: all");
                } else {
                    Log.w(TAG, "Subscribing to FCM topic failed", task.getException());
                }
            });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary),
            ContextCompat.getColor(this, R.color.accent),
            ContextCompat.getColor(this, R.color.primary_dark)
        );
        swipeRefresh.setOnRefreshListener(() -> {
            if (isOffline) {
                checkNetworkAndReload();
            } else {
                reloadWebView();
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        settings.setSupportMultipleWindows(false);

        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();

        String ua = webView.getSettings().getUserAgentString();
        webView.getSettings().setUserAgentString(ua + " BesooooApp/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                isLoadingError = false;
                progressBar.setVisibility(View.VISIBLE);
                errorBinding.getRoot().setVisibility(View.GONE);
                noInternetBinding.getRoot().setVisibility(View.GONE);
                // Hide WebView completely while loading to prevent partial rendering
                webView.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (!isLoadingError) {
                    errorBinding.getRoot().setVisibility(View.GONE);
                    noInternetBinding.getRoot().setVisibility(View.GONE);
                    // Show WebView only after loading is 100% complete
                    webView.setVisibility(View.VISIBLE);
                }

                disableServiceWorker();
                clearAppCache();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                         WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    isLoadingError = true;
                    progressBar.setVisibility(View.GONE);
                    webView.setVisibility(View.GONE);
                    errorBinding.getRoot().setVisibility(View.VISIBLE);
                    noInternetBinding.getRoot().setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                isLoadingError = true;
                progressBar.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                errorBinding.getRoot().setVisibility(View.VISIBLE);
                noInternetBinding.getRoot().setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.startsWith("whatsapp://") || url.contains("wa.me") || url.contains("api.whatsapp.com")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        // Force the system to show the app chooser
                        startActivity(Intent.createChooser(intent, "اختر تطبيق واتساب"));
                        return true;
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                            getString(R.string.install_app), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }

                if (url.startsWith("tg://") ||
                    url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("viber://") || url.startsWith("facebook://") ||
                    url.startsWith("fb://") || url.startsWith("instagram://") ||
                    url.startsWith("twitter://") || url.startsWith("x://") ||
                    url.startsWith("snapchat://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                            getString(R.string.install_app), Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }

                if (url.startsWith("intent://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (url.contains("basemelsayed540.github.io/besoooo")) {
                        return false;
                    }
                    if (url.contains("github.com") || url.contains("github.io")) {
                        return false;
                    }
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }

                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            private View customView;
            private FrameLayout fullscreenContainer;
            private CustomViewCallback customViewCallback;

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                            GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                String[] acceptTypes = fileChooserParams != null ?
                    fileChooserParams.getAcceptTypes() : new String[]{};

                boolean isCamera = false;
                for (String type : acceptTypes) {
                    if (type != null && type.contains("image")) {
                        isCamera = true;
                        break;
                    }
                }

                Intent chooserIntent = fileChooserParams != null ?
                    fileChooserParams.createIntent() : new Intent(Intent.ACTION_GET_CONTENT);

                if (isCamera) {
                    Intent cameraIntent = new Intent(
                        android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    Intent[] intentArray = new Intent[]{cameraIntent};
                    Intent multiIntent = new Intent(Intent.ACTION_CHOOSER);
                    multiIntent.putExtra(Intent.EXTRA_INTENT, chooserIntent);
                    multiIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                    startActivityForResult(multiIntent, FILE_CHOOSER_REQUEST_CODE);
                } else {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_REQUEST_CODE);
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, int requestedOrientation,
                                          CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setBackgroundColor(
                    ContextCompat.getColor(MainActivity.this, android.R.color.black));
                fullscreenContainer.addView(view);
                setContentView(fullscreenContainer);

                int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                fullscreenContainer.setSystemUiVisibility(uiOptions);

                customViewCallback = callback;
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                setContentView(binding.getRoot());
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                }
                customView = null;
                fullscreenContainer = null;
                customViewCallback = null;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    String[] resources = request.getResources();
                    for (String resource : resources) {
                        if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE) ||
                            resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            request.grant(request.getResources());
                            return;
                        }
                    }
                    request.deny();
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String fileName = URLDecoder.decode(
                    Uri.parse(url).getLastPathSegment(), "UTF-8");
                request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName);
                DownloadManager dm = (DownloadManager) getSystemService(
                    Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this,
                        getString(R.string.downloading), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this,
                    getString(R.string.download_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupScrollListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                swipeRefresh.setEnabled(scrollY == 0);
            });
        } else {
            // Fallback for older APIs (though project minSdk is 24)
            webView.getViewTreeObserver().addOnScrollChangedListener(() -> {
                if (webView != null && swipeRefresh != null) {
                    swipeRefresh.setEnabled(webView.getScrollY() == 0);
                }
            });
        }
    }

    private void setupErrorButtons() {
        errorBinding.btnRetry.setOnClickListener(v -> {
            errorBinding.getRoot().setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            reloadWebView();
        });
        noInternetBinding.btnRetryOffline.setOnClickListener(v -> {
            noInternetBinding.getRoot().setVisibility(View.GONE);
            checkNetworkAndReload();
        });
    }

    private void loadUrl() {
        if (NetworkUtils.isNetworkAvailable(this)) {
            isOffline = false;
            String url = BASE_URL + "?v=" + System.currentTimeMillis();
            webView.loadUrl(url);
        } else {
            showOffline();
        }
    }

    private void reloadWebView() {
        isLoadingError = false;
        errorBinding.getRoot().setVisibility(View.GONE);
        noInternetBinding.getRoot().setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        String url = BASE_URL + "?v=" + System.currentTimeMillis();
        webView.loadUrl(url);
    }

    private void showOffline() {
        isOffline = true;
        isLoadingError = true;
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorBinding.getRoot().setVisibility(View.GONE);
        noInternetBinding.getRoot().setVisibility(View.VISIBLE);
    }

    private void checkNetworkAndReload() {
        if (NetworkUtils.isNetworkAvailable(this)) {
            isOffline = false;
            reloadWebView();
        } else {
            showOffline();
            Toast.makeText(this, R.string.no_internet_title, Toast.LENGTH_SHORT).show();
        }
    }

    private void disableServiceWorker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            webView.evaluateJavascript(
                "try { " +
                "  if ('serviceWorker' in navigator) { " +
                "    navigator.serviceWorker.getRegistrations().then(function(regs) { " +
                "      for (var r of regs) { r.unregister(); } " +
                "    }); " +
                "  } " +
                "} catch(e) {}; true", null);
        }
    }

    private void clearAppCache() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            webView.evaluateJavascript(
                "try { " +
                "  if ('caches' in window) { " +
                "    caches.keys().then(function(names) { " +
                "      for (var n of names) { caches.delete(n); } " +
                "    }); " +
                "  } " +
                "} catch(e) {}; true", null);
        }
    }

    private void registerConnectivityReceiver() {
        connectivityReceiver = new ConnectivityReceiver(isConnected -> {
            runOnUiThread(() -> {
                if (isConnected) {
                    if (isOffline || isLoadingError) {
                        checkNetworkAndReload();
                    }
                } else {
                    if (!isOffline) {
                        isOffline = true;
                        showOffline();
                    }
                }
            });
        });
        registerReceiver(connectivityReceiver,
            ConnectivityReceiver.getFilter());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                finishAffinity();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK) {
                    if (data != null && data.getDataString() != null) {
                        results = new Uri[]{Uri.parse(data.getDataString())};
                    } else if (data != null && data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            } else if (filePathCallbackLegacy != null) {
                Uri result = data != null ? data.getData() : null;
                filePathCallbackLegacy.onReceiveValue(result);
                filePathCallbackLegacy = null;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(android.Manifest.permission.CAMERA) &&
                    grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    reloadWebView();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Removed checkNetworkAndReload() to prevent auto-refresh on app resume.
        // Network state changes are handled by ConnectivityReceiver.
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityReceiver != null) {
            unregisterReceiver(connectivityReceiver);
        }
        retryHandler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.destroy();
        }
    }
}
