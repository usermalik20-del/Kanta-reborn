package ph.kanta.reborn.core;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Learning Assistant that provides contextual guidance based on pitch performance.
 * Analyzes pitch data and generates actionable advice for singers.
 */
public class LearningAssistant {

    private static final float PERFECT_THRESHOLD = 0.9f;
    private static final float GOOD_THRESHOLD = 0.7f;
    private static final float CLOSE_THRESHOLD = 0.4f;

    private String currentSongKey = "C major";
    private List<String> adviceHistory = new ArrayList<>();
    private final Random random = new Random();

    // Advice templates
    private static final String[] PITCH_TOO_LOW_ADVICE = {
            "You're singing flat! Try lifting your soft palate and imagine the note floating up.",
            "Low pitch detected. Think of the note as a balloon rising - gently push it upward.",
            "Flat by a bit. Take a deeper breath and aim slightly above the target note.",
            "Your voice is dropping. Engage your core support and lift the sound.",
            "Try this: hum the target note first, then transition to the word."
    };

    private static final String[] PITCH_TOO_HIGH_ADVICE = {
            "You're singing sharp! Relax your jaw and let the note settle down naturally.",
            "Sharp pitch detected. Imagine the note as a feather landing softly.",
            "A bit high. Try thinking 'down' as you approach the note.",
            "Your pitch is climbing. Release tension in your throat and let it fall.",
            "Try: sing the note slightly below target, then slide up to center."
    };

    private static final String[] PITCH_CLOSE_ADVICE = {
            "So close! Micro-adjustment needed - just a tiny lift.",
            "Almost there! A small breath adjustment will center it.",
            "Great control! Tiny tweak and you'll nail it.",
            "Excellent pitch awareness - just a hair off.",
            "You're in the zone! One subtle correction away from perfect."
    };

    private static final String[] PITCH_PERFECT_ADVICE = {
            "Perfect! That's exactly on target. Lock that feeling in!",
            "Nailed it! Your pitch center is rock solid.",
            "Beautiful! That's the sweet spot. Remember this sensation.",
            "Spot on! You're developing excellent pitch memory.",
            "Incredible control! That's professional-level accuracy."
    };

    private static final String[] GENERAL_TECHNIQUE_ADVICE = {
            "Breath support is key - engage your diaphragm, not your throat.",
            "Keep your jaw relaxed and your tongue forward.",
            "Visualize the pitch before you sing it - hear it in your mind first.",
            "Practice the target note silently (audiation) before singing.",
            "Record yourself and listen back - ears train faster than voice.",
            "Warm up with sirens (slide from low to high) to connect your range.",
            "Vowel modification helps: 'ah' opens the throat, 'ee' lifts placement.",
            "Stay hydrated - vocal folds need moisture for precise vibration."
    };

    public LearningAssistant() {}

    public void setSongKey(String songKey) {
        this.currentSongKey = songKey;
    }

    /**
     * Generates contextual advice based on current pitch performance.
     */
    public String generateAdvice(PitchData pitchData) {
        if (pitchData == null || pitchData.currentPitchHz <= 0) {
            return getRandomGeneralAdvice();
        }

        String advice;
        float accuracy = pitchData.accuracy;
        int cents = pitchData.centsOffset;

        if (accuracy >= PERFECT_THRESHOLD) {
            advice = getRandomAdvice(PITCH_PERFECT_ADVICE);
        } else if (accuracy >= GOOD_THRESHOLD) {
            advice = getRandomAdvice(PITCH_CLOSE_ADVICE);
        } else if (cents < 0 && Math.abs(cents) > 20) {
            advice = getRandomAdvice(PITCH_TOO_LOW_ADVICE);
        } else if (cents > 0 && Math.abs(cents) > 20) {
            advice = getRandomAdvice(PITCH_TOO_HIGH_ADVICE);
        } else {
            advice = getRandomAdvice(PITCH_CLOSE_ADVICE);
        }

        // Add context about current note
        advice += " (Target: " + pitchData.noteName + ")";

        // Store in history (keep last 20)
        adviceHistory.add(0, advice);
        if (adviceHistory.size() > 20) {
            adviceHistory.remove(adviceHistory.size() - 1);
        }

        return advice;
    }

    /**
     * Generates a summary of recent performance with actionable tips.
     */
    public String generatePerformanceSummary(List<PitchData> recentPitches) {
        if (recentPitches == null || recentPitches.isEmpty()) {
            return "Start singing to get personalized feedback!";
        }

        int total = recentPitches.size();
        int perfect = 0, good = 0, close = 0, off = 0;
        int sharpCount = 0, flatCount = 0;

        for (PitchData pd : recentPitches) {
            if (pd.accuracy >= PERFECT_THRESHOLD) perfect++;
            else if (pd.accuracy >= GOOD_THRESHOLD) good++;
            else if (pd.accuracy >= CLOSE_THRESHOLD) close++;
            else off++;

            if (pd.centsOffset > 20) sharpCount++;
            else if (pd.centsOffset < -20) flatCount++;
        }

        StringBuilder summary = new StringBuilder();
        summary.append("📊 **Performance Summary**\n");
        summary.append(String.format("Perfect: %d%% | Good: %d%% | Close: %d%% | Off: %d%%\n",
                perfect * 100 / total, good * 100 / total, close * 100 / total, off * 100 / total));

        if (sharpCount > flatCount && sharpCount > total * 0.3) {
            summary.append("🎯 **Tendency:** You tend to sing sharp. Relax and let notes settle.\n");
        } else if (flatCount > sharpCount && flatCount > total * 0.3) {
            summary.append("🎯 **Tendency:** You tend to sing flat. Lift and support more.\n");
        }

        if (perfect * 100 / total >= 70) {
            summary.append("🌟 **Excellent!** Your pitch accuracy is outstanding!\n");
        } else if (perfect * 100 / total >= 40) {
            summary.append("👍 **Good progress!** Focus on consistency for the next level.\n");
        } else {
            summary.append("📈 **Keep practicing!** Focus on breath support and listening.\n");
        }

        summary.append("\n💡 **Next session focus:** ");
        if (off * 100 / total > 30) {
            summary.append("Slow practice - hit each note accurately before speed.");
        } else if (sharpCount > flatCount) {
            summary.append("Landing notes - practice descending to center pitch.");
        } else {
            summary.append("Lifting pitch - practice with a tuner for feedback.");
        }

        return summary.toString();
    }

    /**
     * Generates a specific exercise recommendation based on weaknesses.
     */
    public String recommendExercise(List<PitchData> recentPitches) {
        if (recentPitches == null || recentPitches.isEmpty()) {
            return "Try the 'Siren Exercise': Slide smoothly from your lowest to highest note and back.";
        }

        int sharpCount = 0, flatCount = 0, offCount = 0;
        for (PitchData pd : recentPitches) {
            if (pd.centsOffset > 30) sharpCount++;
            else if (pd.centsOffset < -30) flatCount++;
            if (pd.accuracy < CLOSE_THRESHOLD) offCount++;
        }

        if (offCount > recentPitches.size() * 0.4) {
            return "🎵 **Pitch Matching Exercise**: Play a note on piano/app, hum it, then sing 'ah' matching it exactly. 5 minutes daily.";
        } else if (sharpCount > flatCount * 2) {
            return "🎵 **Descending Scale Exercise**: Sing a 5-note scale down slowly, focusing on landing each note without overshooting.";
        } else if (flatCount > sharpCount * 2) {
            return "🎵 **Ascending Scale Exercise**: Sing a 5-note scale up slowly, imagining each note lifting like a balloon.";
        } else {
            return "🎵 **Interval Training**: Practice major/minor 3rds, perfect 4ths/5ths. Sing 'do-mi', 'do-fa', 'do-sol' with a reference tone.";
        }
    }

    /**
     * Returns a random general technique tip.
     */
    public String getRandomGeneralAdvice() {
        return "💡 " + GENERAL_TECHNIQUE_ADVICE[random.nextInt(GENERAL_TECHNIQUE_ADVICE.length)];
    }

    private String getRandomAdvice(String[] adviceArray) {
        return adviceArray[random.nextInt(adviceArray.length)];
    }

    public List<String> getAdviceHistory() {
        return new ArrayList<>(adviceHistory);
    }

    public void clearHistory() {
        adviceHistory.clear();
    }

    public ProgressReport generateProgressReport(List<PitchData> sessionPitches, List<PitchData> previousSessionPitches) {
        float currentAvg = calculateAverageAccuracy(sessionPitches);
        float previousAvg = calculateAverageAccuracy(previousSessionPitches);

        String trend;
        if (previousAvg <= 0) {
            trend = "First session! Establishing baseline.";
        } else if (currentAvg > previousAvg + 0.05f) {
            trend = "📈 Improving! Accuracy up " + Math.round((currentAvg - previousAvg) * 100) + "%";
        } else if (currentAvg < previousAvg - 0.05f) {
            trend = "📉 Slight dip. Focus on fundamentals.";
        } else {
            trend = "➡️ Consistent. Keep steady practice.";
        }

        return new ProgressReport(currentAvg, previousAvg, trend);
    }

    private float calculateAverageAccuracy(List<PitchData> pitches) {
        if (pitches == null || pitches.isEmpty()) return 0f;
        float sum = 0f;
        for (PitchData pd : pitches) sum += pd.accuracy;
        return sum / pitches.size();
    }

    public static class ProgressReport {
        public final float currentAccuracy;
        public final float previousAccuracy;
        public final String trend;

        public ProgressReport(float current, float previous, String trend) {
            this.currentAccuracy = current;
            this.previousAccuracy = previous;
            this.trend = trend;
        }
    }
}
