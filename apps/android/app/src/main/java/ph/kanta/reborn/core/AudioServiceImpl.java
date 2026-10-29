package ph.kanta.reborn.core;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of AudioService that wraps the existing MicrophoneInput
 * for pitch detection features.
 */
public class AudioServiceImpl implements AudioService {

    private static final String TAG = "AudioServiceImpl";
    private final Context context;
    private final MicrophoneInput microphoneInput;
    private final DryPitchDetector pitchDetector;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AudioListener> listeners = new ArrayList<>();

    private PitchData latestPitchData;
    private boolean isListening = false;
    private float targetPitchHz = 440f; // Default A4

    public AudioServiceImpl(Context context) {
        this.context = context.getApplicationContext();
        this.microphoneInput = new MicrophoneInput();
        this.pitchDetector = new DryPitchDetector();
    }

    public void setTargetPitch(float targetPitchHz) {
        this.targetPitchHz = targetPitchHz;
    }

    @Override
    public void startListening() {
        if (isListening) return;

        executor.execute(() -> {
            try {
                microphoneInput.start();
                pitchDetector.start();

                isListening = true;
                notifyListeningStarted();

                // Poll for pitch data
                while (isListening) {
                    float detectedPitch = pitchDetector.getCurrentPitch();
                    if (detectedPitch > 0) {
                        float accuracy = calculateAccuracy(detectedPitch, targetPitchHz);
                        latestPitchData = new PitchData(detectedPitch, targetPitchHz, accuracy, System.currentTimeMillis());
                        notifyPitchDetected(latestPitchData);
                    }
                    Thread.sleep(50); // 20Hz polling
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in pitch detection", e);
                notifyError(e.getMessage());
            } finally {
                isListening = false;
                notifyListeningStopped();
            }
        });
    }

    @Override
    public void stopListening() {
        isListening = false;
        try {
            microphoneInput.stop();
            pitchDetector.stop();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio", e);
        }
    }

    @Override
    public boolean isListening() {
        return isListening;
    }

    @Override
    public void addListener(AudioListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(AudioListener listener) {
        listeners.remove(listener);
    }

    @Override
    public PitchData getLatestPitchData() {
        return latestPitchData;
    }

    private float calculateAccuracy(float detectedHz, float targetHz) {
        if (detectedHz <= 0 || targetHz <= 0) return 0f;
        int cents = Math.abs(Math.round(1200f * (float) Math.log(detectedHz / targetHz)));
        if (cents <= 5) return 1.0f;
        if (cents <= 20) return 0.8f;
        if (cents <= 50) return 0.5f;
        if (cents <= 100) return 0.2f;
        return 0f;
    }

    private void notifyPitchDetected(PitchData data) {
        for (AudioListener listener : listeners) {
            listener.onPitchDetected(data);
        }
    }

    private void notifyListeningStarted() {
        for (AudioListener listener : listeners) {
            listener.onListeningStarted();
        }
    }

    private void notifyListeningStopped() {
        for (AudioListener listener : listeners) {
            listener.onListeningStopped();
        }
    }

    private void notifyError(String error) {
        for (AudioListener listener : listeners) {
            listener.onError(error);
        }
    }

    public void shutdown() {
        stopListening();
        executor.shutdown();
    }
}
