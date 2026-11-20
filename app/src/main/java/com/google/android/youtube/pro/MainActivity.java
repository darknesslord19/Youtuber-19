package com.google.android.youtube.pro;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.graphics.drawable.Icon;
import java.util.ArrayList;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;

// Import the separated components
import com.google.android.youtube.pro.webview.YTProWebView;
import com.google.android.youtube.pro.webview.YTProWebViewClient;
import com.google.android.youtube.pro.webview.YTProWebChromeClient;
import com.google.android.youtube.pro.webview.WebAppInterface;
import com.google.android.youtube.pro.webview.BinaryStreamManager;
import android.view.MotionEvent;
import android.os.SystemClock;

import com.google.android.youtube.pro.receivers.MediaCommandReceiver;

public class MainActivity extends Activity {

    public boolean portrait = false;
    public boolean isPlaying = false;
    public boolean mediaSession = false;
    public boolean isPip = false;
    public boolean dL = false;

    private YTProWebView web;
    private MediaCommandReceiver broadcastReceiver;
    private OnBackInvokedCallback backCallback;
    public BinaryStreamManager streamManager;
    
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        if (!prefs.contains("bgplay")) {
            prefs.edit().putBoolean("bgplay", true).apply();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        load(false);
    }

    public void load(boolean dl) {
              
        
        this.dL = dl;
        web = findViewById(R.id.web);
        
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setSupportZoom(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setDatabaseEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false); 
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(web, true);
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        String url = "https://m.youtube.com/";
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            url = data.toString();
        } else if (Intent.ACTION_SEND.equals(action)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
                url = sharedText;
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }


        web.addJavascriptInterface(new WebAppInterface(this, web), "Android");
        web.setWebChromeClient(new YTProWebChromeClient(this, web));
        web.setWebViewClient(new YTProWebViewClient(this, web));
        // Darkness Lord: arka plan engellemelerini sayfa yüklenmeden önce kur
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                java.io.InputStream in = getAssets().open("ytpro_early.js");
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                java.util.HashSet<String> origins = new java.util.HashSet<>();
                origins.add("https://m.youtube.com");
                origins.add("https://www.youtube.com");
                origins.add("https://youtube.com");
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, bo.toString("UTF-8"), origins);
            }
        } catch (Exception ignored) {}
        
        web.loadUrl(url);

        setupReceiver();
        setupBackNavigation();
        streamManager = new BinaryStreamManager(web,this);
        
        
    }
         

   

    private void setupReceiver() {
        broadcastReceiver = new MediaCommandReceiver(web);
        registerAudioOnlyReceiver();
        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"), RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"));
        }
    }

    private void setupBackNavigation() {
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
            backCallback = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    handleBackPress();
                }
            };
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    private void handleBackPress() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                web.loadUrl("https://m.youtube.com");
            } else {
                Toast.makeText(getApplicationContext(), getString(R.string.grant_mic), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(getApplicationContext(), getString(R.string.grant_storage), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /*Darkness Lord: mini pencerede "Sadece ses" butonu*/
    public static final String ACTION_AUDIO_ONLY = "com.google.android.youtube.pro.AUDIO_ONLY";
    private BroadcastReceiver audioOnlyReceiver;
    private boolean bgAudio = false;

    public PictureInPictureParams buildPipParams(boolean portrait) {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(portrait ? 9 : 16, portrait ? 16 : 9));
        try {
            Intent i = new Intent(ACTION_AUDIO_ONLY).setPackage(getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(this, 77, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            ArrayList<RemoteAction> actions = new ArrayList<>();
            actions.add(new RemoteAction(Icon.createWithResource(this, R.drawable.ic_headset_white), "Sadece ses", "Pencereyi kapat, ses arka planda devam etsin", pi));
            b.setActions(actions);
        } catch (Exception ignored) {}
        return b.build();
    }

    private void registerAudioOnlyReceiver() {
        if (Build.VERSION.SDK_INT < 26 || audioOnlyReceiver != null) return;
        audioOnlyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                // Mini pencereyi gizle, uygulama arka planda çalmaya devam etsin
                bgAudio = true;
                web.evaluateJavascript("try{ytproAudioOnly(true)}catch(e){}", null);
                moveTaskToBack(true);
                web.postDelayed(() -> web.evaluateJavascript("try{ytproAudioOnly(true)}catch(e){}", null), 800);
                Toast.makeText(MainActivity.this, "Ses arka planda devam ediyor", Toast.LENGTH_SHORT).show();
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(audioOnlyReceiver, new IntentFilter(ACTION_AUDIO_ONLY), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(audioOnlyReceiver, new IntentFilter(ACTION_AUDIO_ONLY));
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        isPip = isInPictureInPictureMode;
        if (!isInPictureInPictureMode && bgAudio) {
            web.evaluateJavascript("try{ytproAudioOnly(true)}catch(e){}", null);
        } else {
            web.evaluateJavascript(isInPictureInPictureMode ? "PIPlayer();" : "removePIP();", null);
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= 26 && web.getUrl() != null && (web.getUrl().contains("watch") || web.getUrl().contains("shorts"))) {
            boolean pipPref = getSharedPreferences("YTPRO", MODE_PRIVATE).getBoolean("pipOnLeave", true);
            if (isPlaying && pipPref) {
                try {

         /**Webview requires gesture for FullScreenView which is used for PiP mode , this does that ig.*/
         web.loadUrl("javascript:showCurtain();");
         long downTime = SystemClock.uptimeMillis();
         long eventTime = downTime;

         float x = web.getWidth() / 2f;
         float y = 10f; 
         MotionEvent down = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0);
         web.dispatchTouchEvent(down);

         eventTime += 15; 
         MotionEvent up = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0);
         web.dispatchTouchEvent(up);
         down.recycle();
         up.recycle();
         web.loadUrl( "javascript:hideCurtain();");
                 
                    PictureInPictureParams params = buildPipParams(portrait);
                    enterPictureInPictureMode(params);
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Uygulama arka plana geçti: çalıyorsa YouTube'un otomatik duraklatmasını engelle
        boolean bg = getSharedPreferences("YTPRO", MODE_PRIVATE).getBoolean("bgplay", true);
        if (bg && isPlaying && !isPip && web != null) {
            bgAudio = true;
            web.evaluateJavascript("try{ytproAudioOnly(true)}catch(e){}", null);
        }
        if (bgAudio && web != null) {
            try { web.resumeTimers(); } catch (Exception ignored) {}
            try { if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT, false); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgAudio && web != null && !isPip) {
            bgAudio = false;
            web.evaluateJavascript("try{ytproAudioOnly(false)}catch(e){}", null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(getApplicationContext(), ForegroundService.class));
        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
        if (audioOnlyReceiver != null) { try { unregisterReceiver(audioOnlyReceiver); } catch (Exception ignored) {} }
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (streamManager != null) {
            streamManager.cleanup();
        }
    }
}
