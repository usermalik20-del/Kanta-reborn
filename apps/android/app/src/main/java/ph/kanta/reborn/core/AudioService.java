package ph.kanta.reborn.core;

/**
 * Interface for audio input service used by pitch-based features.
 * Both Pitch Meter Game and Learning Assistant depend on this.
 */
public interface AudioService {

    void startListening();

    void stopListening();

    boolean isListening();

    void addListener(AudioListener listener);

    void removeListener(AudioListener listener);

    PitchData getLatestPitchData();

    interface AudioListener {
        void onPitchDetected(PitchData pitchData);
        void onListeningStarted();
        void onListeningStopped();
        void onError(String error);
    }
}
