package com.google.android.youtube.pro.webview;


import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.util.Log;
import android.webkit.WebView;

public class YTProWebView extends WebView {
	
	public YTProWebView(Context context) {
		super(context);
	}
	
	public YTProWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	public YTProWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}
	
	@Override
	protected void onWindowVisibilityChanged(int visibility) {
		
		
		SharedPreferences prefs = getContext().getSharedPreferences("YTPRO", Context.MODE_PRIVATE);
		boolean bgPlay = prefs.getBoolean("bgplay", false); 
		
		
		
		if (visibility != View.GONE && visibility != View.INVISIBLE || !bgPlay){
			super.onWindowVisibilityChanged(visibility);
		}
	}

	/* Darkness Lord: uygulama arka plana geçince Android görünümü "gizli" yapar ve Chromium
	   videoyu kendisi duraklatır (pause ENGELLENDİ ama yine de duruyor). Arka plan oynatma açıkken
	   bu gizlenme bildirimini WebView'a iletme. */
	private boolean bgOn() {
		return getContext().getSharedPreferences("YTPRO", Context.MODE_PRIVATE).getBoolean("bgplay", false);
	}

	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		if (visibility != View.VISIBLE && bgOn()) return;
		super.onVisibilityChanged(changedView, visibility);
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		if (!hasWindowFocus && bgOn()) return;
		super.onWindowFocusChanged(hasWindowFocus);
	}

	@Override
	public void onPause() {
		if (bgOn()) return;
		super.onPause();
	}
}
