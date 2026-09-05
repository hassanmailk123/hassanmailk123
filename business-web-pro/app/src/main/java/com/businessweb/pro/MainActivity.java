package com.businessweb.pro;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String HOME = "https://web.whatsapp.com/";
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int MEDIA_PERMISSION_REQUEST = 1002;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1003;
    private static final String MSG_CHANNEL = "business_web_messages";
    private static final String CALL_CHANNEL = "business_web_calls";
    private static final int MSG_NOTIFICATION_ID = 51;
    private static final int CALL_NOTIFICATION_ID = 52;

    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> uploadCallback;
    private PermissionRequest pendingPermissionRequest;
    private boolean isForeground = true;
    private int lastUnread = 0;
    private long lastCallAlertAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B141A);
        getWindow().setNavigationBarColor(0xFF0B141A);
        createNotificationChannels();
        requestNotificationPermission();
        startSessionService();

        root = new FrameLayout(this);
        setContentView(root);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        try {
            createWebView();
            if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) return;
            webView.loadUrl(HOME);
        } catch (Throwable error) {
            showWebViewUnavailable();
        }
    }

    private void createWebView() {
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.destroy();
        }
        webView = new WebView(this);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setInitialScale(92);
        root.addView(webView, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        configureWebView();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(94);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(makeDesktopUserAgent(settings.getUserAgentString()));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new NativeBridge(), "BusinessWebNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                injectMobileAndAlertBridge();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(MainActivity.this,
                        "Secure connection failed. Check date, time and internet connection.",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                try {
                    ViewGroup parent = (ViewGroup) view.getParent();
                    if (parent != null) parent.removeView(view);
                    view.destroy();
                } catch (Throwable ignored) {}
                webView = null;
                Toast.makeText(MainActivity.this, "Browser engine restarted.", Toast.LENGTH_SHORT).show();
                root.postDelayed(() -> {
                    try {
                        createWebView();
                        webView.loadUrl(HOME);
                    } catch (Throwable error) {
                        showWebViewUnavailable();
                    }
                }, 300);
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException e) {
                    uploadCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingPermissionRequest == request) pendingPermissionRequest = null;
            }
        });
    }

    private void injectMobileAndAlertBridge() {
        if (webView == null) return;
        String js = "(function(){"
                + "if(window.__BWP_V3)return;window.__BWP_V3=true;"
                + "try{var m=document.querySelector('meta[name=viewport]');"
                + "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}"
                + "m.content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no';}catch(e){}"
                + "var lastTitle='';function titleChanged(){try{var t=document.title||'';"
                + "if(t!==lastTitle){lastTitle=t;BusinessWebNative.onTitleChanged(t);}}catch(e){}}"
                + "titleChanged();var titleNode=document.querySelector('title');"
                + "if(titleNode)new MutationObserver(titleChanged).observe(titleNode,{childList:true,subtree:true,characterData:true});"
                + "var callShown=false,timer=null;function checkCall(){timer=null;try{var ds=document.querySelectorAll('[role=dialog]');var txt='';"
                + "for(var i=0;i<ds.length;i++){txt+=' '+(ds[i].innerText||'');}var low=txt.toLowerCase();"
                + "var incoming=low.indexOf('incoming')>=0&&low.indexOf('call')>=0;"
                + "if(incoming&&!callShown){callShown=true;BusinessWebNative.onIncomingCall();}"
                + "else if(!incoming&&callShown){callShown=false;BusinessWebNative.onCallEnded();}}catch(e){}}"
                + "new MutationObserver(function(){if(timer)clearTimeout(timer);timer=setTimeout(checkCall,350);})"
                + ".observe(document.body,{childList:true,subtree:true});checkCall();"
                + "})();";
        webView.evaluateJavascript(js, null);
    }

    public class NativeBridge {
        @JavascriptInterface
        public void onTitleChanged(String title) {
            runOnUiThread(() -> handleUnreadTitle(title));
        }

        @JavascriptInterface
        public void onIncomingCall() {
            runOnUiThread(MainActivity.this::showIncomingCallNotification);
        }

        @JavascriptInterface
        public void onCallEnded() {
            runOnUiThread(() -> {
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.cancel(CALL_NOTIFICATION_ID);
            });
        }
    }

    private void handleUnreadTitle(String title) {
        int unread = parseUnreadCount(title);
        if (unread > lastUnread && !isForeground) showMessageNotification(unread);
        lastUnread = unread;
    }

    private int parseUnreadCount(String title) {
        if (title == null) return 0;
        Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(title);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return 0;
    }

    private void showMessageNotification(int unread) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, MSG_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("New WhatsApp Business message")
                .setContentText(unread == 1 ? "1 unread chat" : unread + " unread chats")
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        getSystemService(NotificationManager.class).notify(MSG_NOTIFICATION_ID, notification);
    }

    private void showIncomingCallNotification() {
        long now = System.currentTimeMillis();
        if (now - lastCallAlertAt < 8000) return;
        lastCallAlertAt = now;
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CALL_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Incoming WhatsApp call")
                .setContentText("Tap to open Business Web Pro and answer")
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setTimeoutAfter(60000)
                .setContentIntent(pending)
                .build();
        getSystemService(NotificationManager.class).notify(CALL_NOTIFICATION_ID, notification);
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel messages = new NotificationChannel(
                MSG_CHANNEL, "WhatsApp messages", NotificationManager.IMPORTANCE_HIGH);
        messages.setDescription("Message alerts from the active linked WhatsApp Web session");
        messages.enableVibration(true);
        manager.createNotificationChannel(messages);

        NotificationChannel calls = new NotificationChannel(
                CALL_CHANNEL, "WhatsApp calls", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("Incoming call alerts exposed by the linked WhatsApp Web session");
        calls.enableVibration(true);
        manager.createNotificationChannel(calls);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void startSessionService() {
        try {
            Intent service = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
        } catch (Throwable ignored) {}
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if (host != null && (host.equals("whatsapp.com") || host.endsWith(".whatsapp.com"))) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (Exception ignored) { Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show(); }
            return true;
        }
        try {
            Intent intent = "intent".equalsIgnoreCase(scheme)
                    ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, "No app can open this link.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        Uri origin = request.getOrigin();
        String host = origin == null ? null : origin.getHost();
        if (host == null || !(host.equals("web.whatsapp.com") || host.endsWith(".whatsapp.com"))) {
            request.deny();
            return;
        }
        pendingPermissionRequest = request;
        List<String> missing = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.RECORD_AUDIO);
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.CAMERA);
            }
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), MEDIA_PERMISSION_REQUEST);
        else grantAllowedWebResources();
    }

    private void grantAllowedWebResources() {
        if (pendingPermissionRequest == null) return;
        List<String> allowed = new ArrayList<>();
        for (String resource : pendingPermissionRequest.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                allowed.add(resource);
            }
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                    && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                allowed.add(resource);
            }
        }
        if (allowed.isEmpty()) pendingPermissionRequest.deny();
        else pendingPermissionRequest.grant(allowed.toArray(new String[0]));
        pendingPermissionRequest = null;
    }

    private String makeDesktopUserAgent(String original) {
        if (original == null || original.trim().isEmpty()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
        }
        String desktop = original.replaceFirst("\\([^)]*\\)", "(Windows NT 10.0; Win64; x64)")
                .replace("; wv", "")
                .replace(" Version/4.0", "")
                .replace(" Mobile", "");
        if (!desktop.contains("Windows NT")) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
        }
        return desktop;
    }

    private void showWebViewUnavailable() {
        progressBar.setVisibility(View.GONE);
        TextView message = new TextView(this);
        message.setText("Business Web Pro needs Android System WebView or Google Chrome.\n\nPlease update Chrome / Android System WebView and open the app again.");
        message.setTextSize(17f);
        message.setPadding(dp(24), dp(40), dp(24), dp(24));
        root.addView(message, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Browser component unavailable")
                .setMessage("Update Android System WebView or Google Chrome, then reopen Business Web Pro.")
                .setPositiveButton("Open Play Store", (dialog, which) -> openWebViewStore())
                .setNegativeButton("Close", null)
                .show();
    }

    private void openWebViewStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.webview")));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview")));
            } catch (ActivityNotFoundException ignored) {}
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (uploadCallback != null) {
                uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                uploadCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MEDIA_PERMISSION_REQUEST) grantAllowedWebResources();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onPause() {
        isForeground = false;
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (uploadCallback != null) {
            uploadCallback.onReceiveValue(null);
            uploadCallback = null;
        }
        if (pendingPermissionRequest != null) {
            pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("BusinessWebNative");
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        try { stopService(new Intent(this, KeepAliveService.class)); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
