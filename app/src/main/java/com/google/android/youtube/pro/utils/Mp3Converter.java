package com.google.android.youtube.pro.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/* Darkness Lord: indirilen ses dosyasını (m4a/webm) MP3'e çevirir. */
public final class Mp3Converter {

	public interface Callback {
		void onProgress(int percent);
		void onSuccess(String name);
		void onFailure(String message);
	}

	private Mp3Converter() {}

	public static void convert(final Context ctx, final File input, final String outName, final int kbps, final Callback cb) {
		new Thread(() -> {
			final Handler ui = new Handler(Looper.getMainLooper());
			MediaExtractor ex = new MediaExtractor();
			MediaCodec codec = null;
			OutputStream os = null;
			Uri outUri = null;
			File outFile = null;
			boolean ok = false;
			try {
				// İndirme tamamen diske yazılana kadar bekle (dosya boyutu sabitlenene kadar)
				long last = -1; int stable = 0;
				for (int i = 0; i < 150 && stable < 4; i++) {
					long s = input.length();
					if (s > 0 && s == last) stable++; else stable = 0;
					last = s;
					Thread.sleep(250);
				}
				ex.setDataSource(input.getAbsolutePath());
				int track = -1;
				MediaFormat fmt = null;
				for (int i = 0; i < ex.getTrackCount(); i++) {
					MediaFormat f = ex.getTrackFormat(i);
					String m = f.getString(MediaFormat.KEY_MIME);
					if (m != null && m.startsWith("audio/")) { track = i; fmt = f; break; }
				}
				if (track < 0) throw new Exception("Ses bulunamadı");
				ex.selectTrack(track);
				long durUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;

				codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
				codec.configure(fmt, null, null, 0);
				codec.start();

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					ContentResolver r = ctx.getContentResolver();
					ContentValues v = new ContentValues();
					v.put(MediaStore.Downloads.DISPLAY_NAME, outName);
					v.put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg");
					v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/YTPRO");
					v.put(MediaStore.Downloads.IS_PENDING, 1);
					outUri = r.insert(MediaStore.Downloads.getContentUri("external"), v);
					if (outUri == null) throw new Exception("Dosya oluşturulamadı");
					os = new BufferedOutputStream(r.openOutputStream(outUri), 256 * 1024);
				} else {
					outFile = new File(input.getParentFile(), outName);
					os = new BufferedOutputStream(new FileOutputStream(outFile), 256 * 1024);
				}

				Mp3Encoder enc = null;
				int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
				int rate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
				short[] pcm = new short[0];
				byte[] mp3 = new byte[0];
				MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
				boolean inDone = false, outDone = false;
				int lastPct = -1;

				while (!outDone) {
					if (!inDone) {
						int ii = codec.dequeueInputBuffer(10000);
						if (ii >= 0) {
							ByteBuffer ib = codec.getInputBuffer(ii);
							int n = ex.readSampleData(ib, 0);
							if (n < 0) {
								codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
								inDone = true;
							} else {
								codec.queueInputBuffer(ii, 0, n, ex.getSampleTime(), 0);
								ex.advance();
							}
						}
					}
					int oi = codec.dequeueOutputBuffer(info, 10000);
					if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
						MediaFormat of = codec.getOutputFormat();
						if (enc == null) {
							channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
							rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
						}
					} else if (oi >= 0) {
						if (info.size > 0) {
							if (enc == null) enc = new Mp3Encoder(Math.min(channels, 2), rate, kbps);
							ByteBuffer ob = codec.getOutputBuffer(oi);
							ob.position(info.offset);
							ob.limit(info.offset + info.size);
							ShortBuffer sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer();
							int n = sb.remaining();
							if (pcm.length < n) pcm = new short[n];
							sb.get(pcm, 0, n);
							if (channels > 2) {
								// çok kanallı sesi stereo'ya indir (ilk iki kanal)
								int frames = n / channels;
								for (int f = 0; f < frames; f++) {
									pcm[2 * f] = pcm[f * channels];
									pcm[2 * f + 1] = pcm[f * channels + 1];
								}
								n = frames * 2;
							}
							int need = Mp3Encoder.outBufferSize(n);
							if (mp3.length < need) mp3 = new byte[need];
							int w = enc.encode(pcm, n, mp3);
							if (w > 0) os.write(mp3, 0, w);
							if (durUs > 0) {
								int pct = (int) Math.min(99, info.presentationTimeUs * 100 / durUs);
								if (pct != lastPct && pct % 5 == 0) {
									lastPct = pct;
									final int p = pct;
									ui.post(() -> cb.onProgress(p));
								}
							}
						}
						codec.releaseOutputBuffer(oi, false);
						if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
					}
				}
				if (enc == null) throw new Exception("Ses çözülemedi");
				if (mp3.length < 7200) mp3 = new byte[7200];
				int w = enc.flush(mp3);
				if (w > 0) os.write(mp3, 0, w);
				os.flush();
				os.close();
				os = null;
				if (outUri != null) {
					ContentValues v = new ContentValues();
					v.put(MediaStore.Downloads.IS_PENDING, 0);
					ctx.getContentResolver().update(outUri, v, null, null);
				}
				ok = true;
				ui.post(() -> cb.onSuccess(outName));
			} catch (final Throwable e) {
				final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
				ui.post(() -> cb.onFailure(msg));
			} finally {
				try { if (os != null) os.close(); } catch (Exception ignored) {}
				try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) {}
				try { ex.release(); } catch (Exception ignored) {}
				if (!ok) {
					try { if (outUri != null) ctx.getContentResolver().delete(outUri, null, null); } catch (Exception ignored) {}
					if (outFile != null) outFile.delete();
				}
				MediaMuxerUtils.deleteFile(ctx, input);
			}
		}).start();
	}
}
