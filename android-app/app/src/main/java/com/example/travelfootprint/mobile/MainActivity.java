package com.example.travelfootprint.mobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URI;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 501;
    private static final int LOCATION_PERMISSION_REQUEST = 502;
    private static final String PREFERENCES = "travel_footprint_mobile";
    private static final String SERVER_URL_KEY = "server_url";
    private static final String SERVER_CONFIGURED_KEY = "server_configured";

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout offlinePanel;
    private TextView offlineMessage;
    private ValueCallback<Uri[]> fileCallback;
    private GeolocationPermissions.Callback geolocationCallback;
    private String geolocationOrigin;
    private String serverUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 92, 87));
        if (BuildConfig.SERVER_PRECONFIGURED) {
            // A cloud build must replace any local address retained by an older debug installation.
            serverUrl = BuildConfig.DEFAULT_SERVER_URL;
            getPreferences().edit()
                    .putString(SERVER_URL_KEY, serverUrl)
                    .putBoolean(SERVER_CONFIGURED_KEY, true)
                    .apply();
        } else {
            serverUrl = getPreferences().getString(SERVER_URL_KEY, "");
        }
        buildInterface();
        configureWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else if (!BuildConfig.SERVER_PRECONFIGURED
                && !getPreferences().getBoolean(SERVER_CONFIGURED_KEY, false)) {
            showServerDialog(true);
        } else {
            loadHome();
        }
    }

    private android.content.SharedPreferences getPreferences() {
        return getSharedPreferences(PREFERENCES, MODE_PRIVATE);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 239, 230));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), 0, dp(8), 0);
        toolbar.setBackgroundColor(Color.rgb(15, 118, 110));
        root.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button refresh = toolbarButton(R.string.refresh);
        refresh.setOnClickListener(view -> webView.reload());
        toolbar.addView(refresh);

        Button server = toolbarButton(R.string.server_settings);
        server.setOnClickListener(view -> showServerDialog(false));
        toolbar.addView(server);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        webView = new WebView(this);
        content.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        content.addView(progressBar, progressParams);

        offlinePanel = new LinearLayout(this);
        offlinePanel.setOrientation(LinearLayout.VERTICAL);
        offlinePanel.setGravity(Gravity.CENTER);
        offlinePanel.setPadding(dp(32), dp(32), dp(32), dp(32));
        offlinePanel.setBackgroundColor(Color.rgb(244, 239, 230));
        offlinePanel.setVisibility(View.GONE);
        content.addView(offlinePanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView offlineTitle = new TextView(this);
        offlineTitle.setText(R.string.connection_failed_title);
        offlineTitle.setTextColor(Color.rgb(28, 55, 52));
        offlineTitle.setTextSize(24);
        offlineTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        offlineTitle.setGravity(Gravity.CENTER);
        offlinePanel.addView(offlineTitle);

        offlineMessage = new TextView(this);
        offlineMessage.setText(R.string.connection_failed_message);
        offlineMessage.setTextColor(Color.rgb(77, 98, 95));
        offlineMessage.setTextSize(16);
        offlineMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(12), 0, dp(22));
        offlinePanel.addView(offlineMessage, messageParams);

        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(view -> loadHome());
        offlinePanel.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        Button changeServer = new Button(this);
        changeServer.setText(R.string.change_server);
        changeServer.setOnClickListener(view -> showServerDialog(false));
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        changeParams.setMargins(0, dp(10), 0, 0);
        offlinePanel.addView(changeServer, changeParams);

        setContentView(root);
    }

    private Button toolbarButton(int textResource) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        return button;
    }

    @SuppressLint({"SetJavaScriptEnabled", "WebViewApiAvailability"})
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setUserAgentString(settings.getUserAgentString() + " TravelFootprintAndroid/" + BuildConfig.VERSION_NAME);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        // Framework Safe Browsing is available from Android 8.1; Android 8.0 remains usable without this optional initialization.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            WebView.startSafeBrowsing(this, null);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                progressBar.setProgress(progress);
                progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!isTrustedServerOrigin(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                    return;
                }
                if (geolocationCallback != null && geolocationOrigin != null) {
                    geolocationCallback.invoke(geolocationOrigin, false, false);
                }
                geolocationCallback = callback;
                geolocationOrigin = origin;
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, LOCATION_PERMISSION_REQUEST);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException exception) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, R.string.no_file_picker, Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    offlinePanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showConnectionError(getString(R.string.connection_failed_message));
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 500) {
                    showConnectionError(getString(R.string.server_error_message, response.getStatusCode()));
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showConnectionError(getString(R.string.ssl_error_message));
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> openExternal(Uri.parse(url)));
    }

    private boolean isTrustedServerOrigin(String origin) {
        try {
            URI requested = new URI(origin);
            URI configured = new URI(serverUrl);
            return requested.getScheme() != null
                    && requested.getScheme().equalsIgnoreCase(configured.getScheme())
                    && requested.getHost() != null
                    && requested.getHost().equalsIgnoreCase(configured.getHost())
                    && requested.getPort() == configured.getPort();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            try {
                URI target = new URI(uri.toString());
                URI configured = new URI(serverUrl);
                if (target.getScheme() != null
                        && target.getScheme().equalsIgnoreCase(configured.getScheme())
                        && target.getHost() != null
                        && target.getHost().equalsIgnoreCase(configured.getHost())
                        && target.getPort() == configured.getPort()) {
                    return false;
                }
            } catch (Exception ignored) {
                // Malformed links are delegated to the operating system and will normally be rejected.
            }
        }
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.no_external_app, Toast.LENGTH_LONG).show();
        }
    }

    private void showServerDialog(boolean required) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl);
        input.setSelection(input.length());
        input.setHint("https://travel.example.com");
        int horizontalPadding = dp(22);
        FrameLayout inputFrame = new FrameLayout(this);
        inputFrame.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        inputFrame.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.server_dialog_title)
                .setMessage(BuildConfig.DEBUG ? R.string.server_dialog_debug_message : R.string.server_dialog_release_message)
                .setView(inputFrame)
                .setPositiveButton(R.string.connect, null)
                .setNegativeButton(required ? R.string.exit : android.R.string.cancel, (current, which) -> {
                    if (required) finish();
                })
                .setCancelable(!required)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                String normalized = ServerUrl.normalize(input.getText().toString(), BuildConfig.DEBUG);
                serverUrl = normalized;
                getPreferences().edit()
                        .putString(SERVER_URL_KEY, normalized)
                        .putBoolean(SERVER_CONFIGURED_KEY, true)
                        .apply();
                CookieManager.getInstance().flush();
                dialog.dismiss();
                loadHome();
            } catch (IllegalArgumentException exception) {
                input.setError(exception.getMessage());
            }
        }));
        dialog.show();
    }

    private void loadHome() {
        offlinePanel.setVisibility(View.GONE);
        webView.loadUrl(serverUrl + "/");
    }

    private void showConnectionError(String message) {
        offlineMessage.setText(getString(R.string.connection_detail, message, serverUrl));
        offlinePanel.setVisibility(View.VISIBLE);
        offlinePanel.bringToFront();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        fileCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST || geolocationCallback == null || geolocationOrigin == null) return;
        boolean granted = false;
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_GRANTED) {
                granted = true;
                break;
            }
        }
        geolocationCallback.invoke(geolocationOrigin, granted, false);
        geolocationCallback = null;
        geolocationOrigin = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (offlinePanel.getVisibility() == View.VISIBLE) {
            offlinePanel.setVisibility(View.GONE);
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (geolocationCallback != null && geolocationOrigin != null) {
            geolocationCallback.invoke(geolocationOrigin, false, false);
            geolocationCallback = null;
            geolocationOrigin = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
