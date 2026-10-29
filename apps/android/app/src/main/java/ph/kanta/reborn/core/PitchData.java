package ph.kanta.reborn.core;

/**
 * Immutable pitch data for real-time pitch feedback and analysis.
 * Used by both Pitch Meter Game and Learning Assistant features.
 */
public final class PitchData {

    public final float currentPitchHz;
    public final float targetPitchHz;
    public final float accuracy;
    public final long timestampMs;
    public final String noteName;
    public final int centsOffset;

    public PitchData(float currentPitchHz, float targetPitchHz, float accuracy, long timestampMs) {
        this.currentPitchHz = currentPitchHz;
        this.targetPitchHz = targetPitchHz;
        this.accuracy = accuracy;
        this.timestampMs = timestampMs;
        this.noteName = calculateNoteName(currentPitchHz);
        this.centsOffset = calculateCentsOffset(currentPitchHz, targetPitchHz);
    }

    public boolean isOnTarget(float threshold) {
        return accuracy >= threshold;
    }

    public boolean isSharp() {
        return centsOffset > 0;
    }

    public boolean isFlat() {
        return centsOffset < 0;
    }

    public String getDeviationDescription() {
        int absCents = Math.abs(centsOffset);
        if (absCents <= 5) return "Perfect";
        if (absCents <= 20) return "Close";
        if (absCents <= 50) return "Slightly off";
        return "Off pitch";
    }

    private static String calculateNoteName(float pitchHz) {
        if (pitchHz <= 0) return "—";

        float A4 = 440f;
        int midiNote = Math.round(12f * (float) Math.log(pitchHz / A4) + 69);
        String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (midiNote / 12) - 1;
        return notes[midiNote % 12] + octave;
    }

    private static int calculateCentsOffset(float currentHz, float targetHz) {
        if (currentHz <= 0 || targetHz <= 0) return 0;
        return Math.round(1200f * (float) Math.log(currentHz / targetHz));
    }

    @Override
    public String toString() {
        return String.format("PitchData{current=%.1fHz, target=%.1fHz, accuracy=%.2f, note=%s, cents=%d}",
                currentPitchHz, targetPitchHz, accuracy, noteName, centsOffset);
    }
}
