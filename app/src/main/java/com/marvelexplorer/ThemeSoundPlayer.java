package com.marvelexplorer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Short, synthesized interface cues. No external sound files or network are required. */
public final class ThemeSoundPlayer {
    public static final int CUE_OPEN = 1;
    public static final int CUE_SCAN = 2;
    public static final int CUE_CONFIRM = 3;
    public static final int CUE_ALERT = 4;

    private static final String PREFS = "marvel_ui_preferences";
    private static final String PREF_SOUND_ENABLED = "sound_effects_enabled";
    private static final int SAMPLE_RATE = 22050;
    private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "marvel-theme-audio");
        thread.setDaemon(true);
        return thread;
    });

    private ThemeSoundPlayer() {}

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(PREF_SOUND_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PREF_SOUND_ENABLED, enabled).apply();
    }

    public static void play(Context context, int cue) {
        if (!isEnabled(context)) return;
        final int[] notes;
        switch (cue) {
            case CUE_OPEN:
                notes = new int[]{440, 760, 1040};
                break;
            case CUE_SCAN:
                notes = new int[]{520, 1180, 640};
                break;
            case CUE_CONFIRM:
                notes = new int[]{620, 880, 1180};
                break;
            case CUE_ALERT:
                notes = new int[]{720, 410, 720};
                break;
            default:
                return;
        }
        AUDIO.execute(() -> playSequence(notes, cue == CUE_ALERT ? 92 : 70,
                cue == CUE_ALERT ? 0.11 : 0.075));
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void playSequence(int[] frequencies, int toneMillis, double amplitude) {
        byte[] data = synthesize(frequencies, toneMillis, amplitude);
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(data.length)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(data, 0, data.length);
            track.play();
            long duration = (data.length * 1000L) / (SAMPLE_RATE * 2L);
            Thread.sleep(duration + 35L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Audio output is optional; a muted or unavailable output must not affect navigation.
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (RuntimeException ignored) {
                    // The track may have stopped already.
                }
                track.release();
            }
        }
    }

    private static byte[] synthesize(int[] frequencies, int toneMillis, double amplitude) {
        int gapMillis = 28;
        int samplesPerTone = SAMPLE_RATE * toneMillis / 1000;
        int samplesPerGap = SAMPLE_RATE * gapMillis / 1000;
        byte[] pcm = new byte[(samplesPerTone * frequencies.length
                + samplesPerGap * Math.max(0, frequencies.length - 1)) * 2];
        int output = 0;
        for (int n = 0; n < frequencies.length; n++) {
            double startFrequency = frequencies[n];
            double endFrequency = n + 1 < frequencies.length ? frequencies[n + 1] : frequencies[n] * 0.84;
            for (int i = 0; i < samplesPerTone; i++) {
                double progress = i / (double) Math.max(1, samplesPerTone - 1);
                double frequency = startFrequency + (endFrequency - startFrequency) * progress;
                double envelope = Math.min(1.0, Math.min(i / 160.0, (samplesPerTone - i) / 330.0));
                double sample = Math.sin(2.0 * Math.PI * frequency * i / SAMPLE_RATE)
                        * amplitude * Math.max(0.0, envelope);
                short value = (short) (sample * Short.MAX_VALUE);
                pcm[output++] = (byte) (value & 0xff);
                pcm[output++] = (byte) ((value >>> 8) & 0xff);
            }
            if (n + 1 < frequencies.length) output += samplesPerGap * 2;
        }
        return pcm;
    }
}
