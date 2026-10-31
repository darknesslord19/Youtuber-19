package com.google.android.youtube.pro.webview;

import android.content.Context;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/*
 * Darkness Lord: YTPRO scriptlerini telefonda saklar.
 * Açılışta kayıtlı kopya anında verilir, yeni sürüm arka planda indirilir
 * (bir sonraki açılışta devreye girer). "Güncellemeleri Denetle" anında yeniler.
 */
public final class ScriptCache {

	private static final String JSDELIVR = "https://cdn.jsdelivr.net/gh/darknesslord19/Youtuber-19@main/";
	private static final String RAW = "https://raw.githubusercontent.com/darknesslord19/Youtuber-19/main/";

	private static final Set<String> refreshed = Collections.synchronizedSet(new HashSet<String>());
	private static final ExecutorService EX = Executors.newSingleThreadExecutor();

	private ScriptCache() {}

	static String key(String url) {
		int q = url.indexOf('?');
		return q >= 0 ? url.substring(0, q) : url;
	}

	private static File file(Context c, String key) {
		File d = new File(c.getCacheDir(), "ytpro_scripts");
		if (!d.exists()) d.mkdirs();
		return new File(d, Integer.toHexString(key.hashCode()) + "_" + key.length() + ".js");
	}

	public static byte[] read(Context c, String url) {
		File f = file(c, key(url));
		if (!f.exists() || f.length() == 0) return null;
		try (FileInputStream in = new FileInputStream(f)) {
			return readAll(in);
		} catch (Exception e) {
			return null;
		}
	}

	/* İndirir ve kaydeder. Önce raw.githubusercontent (5 dk önbellek), olmazsa jsDelivr. */
	public static byte[] fetch(Context c, String url) {
		String k = key(url);
		byte[] b = null;
		if (k.startsWith(JSDELIVR)) b = download(RAW + k.substring(JSDELIVR.length()));
		if (b == null) b = download(k);
		if (b == null) return null;
		try {
			File f = file(c, k);
			File tmp = new File(f.getPath() + ".tmp");
			try (FileOutputStream out = new FileOutputStream(tmp)) {
				out.write(b);
			}
			if (!tmp.renameTo(f)) {
				f.delete();
				tmp.renameTo(f);
			}
		} catch (Exception ignored) {}
		refreshed.add(k);
		return b;
	}

	public static void refreshAsync(final Context c, String url) {
		final String k = key(url);
		if (!refreshed.add(k)) return;
		EX.execute(new Runnable() {
			@Override
			public void run() {
				fetch(c, k);
			}
		});
	}

	public static WebResourceResponse response(byte[] body) {
		Map<String, String> h = new HashMap<>();
		h.put("Access-Control-Allow-Origin", "*");
		h.put("Cross-Origin-Resource-Policy", "cross-origin");
		h.put("Cache-Control", "no-store");
		return new WebResourceResponse("application/javascript", "utf-8", 200, "OK", h, new ByteArrayInputStream(body));
	}

	private static byte[] download(String u) {
		HttpsURLConnection con = null;
		try {
			con = (HttpsURLConnection) new URL(u).openConnection();
			con.setConnectTimeout(8000);
			con.setReadTimeout(15000);
			con.setUseCaches(false);
			con.setRequestProperty("Cache-Control", "no-cache");
			con.setRequestProperty("User-Agent", "YTPRO");
			if (con.getResponseCode() != 200) return null;
			byte[] b = readAll(con.getInputStream());
			return b.length > 100 ? b : null;
		} catch (Exception e) {
			return null;
		} finally {
			if (con != null) con.disconnect();
		}
	}

	private static byte[] readAll(InputStream in) throws java.io.IOException {
		ByteArrayOutputStream bo = new ByteArrayOutputStream(64 * 1024);
		byte[] buf = new byte[16 * 1024];
		int n;
		while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
		return bo.toByteArray();
	}
}
