package com.google.android.youtube.pro.utils;

import de.sciss.jump3r.mp3.BRHist;
import de.sciss.jump3r.mp3.BitStream;
import de.sciss.jump3r.mp3.GainAnalysis;
import de.sciss.jump3r.mp3.GetAudio;
import de.sciss.jump3r.mp3.ID3Tag;
import de.sciss.jump3r.mp3.Lame;
import de.sciss.jump3r.mp3.LameGlobalFlags;
import de.sciss.jump3r.mp3.MPEGMode;
import de.sciss.jump3r.mp3.Parse;
import de.sciss.jump3r.mp3.Presets;
import de.sciss.jump3r.mp3.Quantize;
import de.sciss.jump3r.mp3.QuantizePVT;
import de.sciss.jump3r.mp3.Reservoir;
import de.sciss.jump3r.mp3.Takehiro;
import de.sciss.jump3r.mp3.VBRTag;
import de.sciss.jump3r.mp3.Version;
import de.sciss.jump3r.mpg.Common;
import de.sciss.jump3r.mpg.Interface;
import de.sciss.jump3r.mpg.MPGLib;

/* Darkness Lord: Android'de çalışan basit MP3 kodlayıcı (jump3r/LAME, javax.sound yok). */
public final class Mp3Encoder {

	private final Lame lame = new Lame();
	private final LameGlobalFlags gfp;
	private final int channels;
	private int[] left = new int[0], right = new int[0];

	public Mp3Encoder(int channels, int sampleRate, int bitrateKbps) {
		this.channels = channels <= 1 ? 1 : 2;
		GetAudio gaud = new GetAudio();
		GainAnalysis ga = new GainAnalysis();
		BitStream bs = new BitStream();
		Presets p = new Presets();
		QuantizePVT qupvt = new QuantizePVT();
		Quantize qu = new Quantize();
		VBRTag vbr = new VBRTag();
		Version ver = new Version();
		ID3Tag id3 = new ID3Tag();
		Reservoir rv = new Reservoir();
		Takehiro tak = new Takehiro();
		Parse parse = new Parse();
		new BRHist();
		MPGLib mpg = new MPGLib();
		Interface intf = new Interface();
		Common common = new Common();

		lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg);
		bs.setModules(ga, mpg, ver, vbr);
		id3.setModules(bs, ver);
		p.setModules(lame);
		qu.setModules(bs, rv, qupvt, tak);
		qupvt.setModules(tak, rv, lame.enc.psy);
		rv.setModules(bs);
		tak.setModules(qupvt);
		vbr.setModules(lame, bs, ver);
		gaud.setModules(parse, mpg);
		parse.setModules(ver, id3, p);
		mpg.setModules(intf, common);
		intf.setModules(vbr, common);

		gfp = lame.lame_init();
		gfp.num_channels = this.channels;
		gfp.in_samplerate = sampleRate;
		gfp.brate = bitrateKbps;
		gfp.mode = this.channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO;
		gfp.quality = 5;
		id3.id3tag_init(gfp);
		gfp.write_id3tag_automatic = false;
		gfp.findReplayGain = false;
		gfp.bWriteVbrTag = false;
		int rc = lame.lame_init_params(gfp);
		if (rc < 0) throw new IllegalArgumentException("MP3 ayarları desteklenmiyor (" + rc + ")");
	}

	/** 16-bit, kanal-sıralı (interleaved) PCM kodlar. Dönen byte sayısını out'a yazar. */
	public int encode(short[] pcm, int sampleCount, byte[] out) {
		int frames = sampleCount / channels;
		if (left.length < frames) { left = new int[frames]; right = new int[frames]; }
		if (channels == 2) {
			for (int i = 0, j = 0; i < frames; i++) {
				left[i] = pcm[j++] << 16;
				right[i] = pcm[j++] << 16;
			}
		} else {
			for (int i = 0; i < frames; i++) { left[i] = right[i] = pcm[i] << 16; }
		}
		int r = lame.lame_encode_buffer_int(gfp, left, right, frames, out, 0, out.length);
		if (r < 0) throw new RuntimeException("MP3 kodlama hatası (" + r + ")");
		return r;
	}

	public static int outBufferSize(int frames) {
		return (int) (1.25 * frames) + 7200;
	}

	public int flush(byte[] out) {
		int r = lame.lame_encode_flush(gfp, out, 0, out.length);
		lame.lame_close(gfp);
		return Math.max(r, 0);
	}
}
