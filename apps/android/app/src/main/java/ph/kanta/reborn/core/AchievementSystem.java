package ph.kanta.reborn.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Achievement system for tracking user progress and unlocking rewards.
 * Persists achievements to SharedPreferences.
 */
public class AchievementSystem {

    private static final String TAG = "AchievementSystem";
    private static final String PREFS_NAME = "kanta_achievements";
    private static final String KEY_UNLOCKED = "unlocked_";
    private static final String KEY_PROGRESS = "progress_";

    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, Achievement> achievements = new HashMap<>();
    private final List<AchievementListener> listeners = new ArrayList<>();

    public interface AchievementListener {
        void onAchievementUnlocked(Achievement achievement);
        void onProgressUpdated(Achievement achievement, int progress);
    }

    public AchievementSystem(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeAchievements();
        loadProgress();
    }

    public void addListener(AchievementListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(AchievementListener listener) {
        listeners.remove(listener);
    }

    private void initializeAchievements() {
        // Pitch Accuracy Achievements
        addAchievement(new Achievement(
                "FIRST_NOTE", "First Note", "Hit your first target note",
                AchievementCategory.ACCURACY, 1, 10, "🎵"));

        addAchievement(new Achievement(
                "PITCH_PERFECT", "Pitch Perfect", "Get 10 perfect hits in a session",
                AchievementCategory.ACCURACY, 10, 50, "🎯"));

        addAchievement(new Achievement(
                "MAESTRO", "Maestro", "Achieve 50 perfect hits total",
                AchievementCategory.ACCURACY, 50, 200, "🏆"));

        addAchievement(new Achievement(
                "CENTURION", "Centurion", "Achieve 100 perfect hits total",
                AchievementCategory.ACCURACY, 100, 500, "💯"));

        // Streak Achievements
        addAchievement(new Achievement(
                "STREAK_3", "On Fire", "Maintain a 3-hit streak",
                AchievementCategory.STREAK, 3, 15, "🔥"));

        addAchievement(new Achievement(
                "STREAK_5", "Hot Streak", "Maintain a 5-hit streak",
                AchievementCategory.STREAK, 5, 25, "🔥🔥"));

        addAchievement(new Achievement(
                "STREAK_10", "Unstoppable", "Maintain a 10-hit streak",
                AchievementCategory.STREAK, 10, 75, "🚀"));

        addAchievement(new Achievement(
                "STREAK_25", "Legendary", "Maintain a 25-hit streak",
                AchievementCategory.STREAK, 25, 200, "⚡"));

        // Score Achievements
        addAchievement(new Achievement(
                "SCORE_500", "Getting Started", "Reach 500 points in a game",
                AchievementCategory.SCORE, 500, 30, "⭐"));

        addAchievement(new Achievement(
                "SCORE_1000", "High Scorer", "Reach 1,000 points in a game",
                AchievementCategory.SCORE, 1000, 50, "⭐⭐"));

        addAchievement(new Achievement(
                "SCORE_5000", "Score Master", "Reach 5,000 points in a game",
                AchievementCategory.SCORE, 5000, 150, "⭐⭐⭐"));

        addAchievement(new Achievement(
                "SCORE_10000", "Grand Master", "Reach 10,000 points in a game",
                AchievementCategory.SCORE, 10000, 300, "💎"));

        // Session Achievements
        addAchievement(new Achievement(
                "SESSION_1", "Welcome", "Complete your first practice session",
                AchievementCategory.SESSION, 1, 10, "🎉"));

        addAchievement(new Achievement(
                "SESSION_10", "Dedicated", "Complete 10 practice sessions",
                AchievementCategory.SESSION, 10, 40, "📅"));

        addAchievement(new Achievement(
                "SESSION_50", "Committed", "Complete 50 practice sessions",
                AchievementCategory.SESSION, 50, 150, "📅📅"));

        addAchievement(new Achievement(
                "SESSION_100", "Veteran", "Complete 100 practice sessions",
                AchievementCategory.SESSION, 100, 300, "🏅"));

        // Consistency Achievements
        addAchievement(new Achievement(
                "CONSISTENT_WEEK", "Weekly Warrior", "Practice 7 days in a row",
                AchievementCategory.CONSISTENCY, 7, 100, "📆"));

        addAchievement(new Achievement(
                "CONSISTENT_MONTH", "Monthly Master", "Practice 30 days in a month",
                AchievementCategory.CONSISTENCY, 30, 500, "🗓️"));

        // Special Achievements
        addAchievement(new Achievement(
                "NIGHT_OWL", "Night Owl", "Practice after 11 PM",
                AchievementCategory.SPECIAL, 1, 25, "🦉"));

        addAchievement(new Achievement(
                "EARLY_BIRD", "Early Bird", "Practice before 7 AM",
                AchievementCategory.SPECIAL, 1, 25, "🐦"));

        addAchievement(new Achievement(
                "PERFECT_SESSION", "Flawless", "Complete a session with 90%+ accuracy",
                AchievementCategory.SPECIAL, 1, 200, "💎"));

        addAchievement(new Achievement(
                "RANGE_MASTER", "Range Master", "Hit notes across 3+ octaves in one session",
                AchievementCategory.SPECIAL, 3, 150, "🎹"));
    }

    private void addAchievement(Achievement achievement) {
        achievements.put(achievement.id, achievement);
    }

    private void loadProgress() {
        for (Achievement achievement : achievements.values()) {
            boolean unlocked = prefs.getBoolean(KEY_UNLOCKED + achievement.id, false);
            int progress = prefs.getInt(KEY_PROGRESS + achievement.id, 0);

            achievement.unlocked = unlocked;
            achievement.currentProgress = progress;
        }
    }

    /**
     * Updates progress for an achievement and checks for unlock.
     */
    public void updateProgress(String achievementId, int progress) {
        Achievement achievement = achievements.get(achievementId);
        if (achievement == null || achievement.unlocked) return;

        achievement.currentProgress = Math.max(achievement.currentProgress, progress);
        saveProgress(achievement);

        for (AchievementListener l : listeners) {
            l.onProgressUpdated(achievement, achievement.currentProgress);
        }

        if (achievement.currentProgress >= achievement.targetProgress) {
            unlock(achievement);
        }
    }

    /**
     * Increments progress for an achievement.
     */
    public void incrementProgress(String achievementId, int amount) {
        Achievement achievement = achievements.get(achievementId);
        if (achievement == null || achievement.unlocked) return;

        int newProgress = achievement.currentProgress + amount;
        updateProgress(achievementId, newProgress);
    }

    /**
     * Checks and unlocks achievements based on game stats.
     */
    public void checkGameStats(int score, int streak, int perfectCount, int maxStreak) {
        updateProgress("SCORE_500", score);
        updateProgress("SCORE_1000", score);
        updateProgress("SCORE_5000", score);
        updateProgress("SCORE_10000", score);

        updateProgress("STREAK_3", streak);
        updateProgress("STREAK_5", streak);
        updateProgress("STREAK_10", streak);
        updateProgress("STREAK_25", streak);

        updateProgress("PITCH_PERFECT", perfectCount);
        updateProgress("MAESTRO", perfectCount);
        updateProgress("CENTURION", perfectCount);
    }

    /**
     * Records a completed session.
     */
    public void recordSession(boolean highAccuracy) {
        incrementProgress("SESSION_1", 1);
        incrementProgress("SESSION_10", 1);
        incrementProgress("SESSION_50", 1);
        incrementProgress("SESSION_100", 1);

        if (highAccuracy) {
            unlock("PERFECT_SESSION");
        }
    }

    /**
     * Records practice time for time-based achievements.
     */
    public void recordPracticeTime(int hourOfDay) {
        if (hourOfDay >= 23 || hourOfDay < 5) {
            unlock("NIGHT_OWL");
        } else if (hourOfDay >= 5 && hourOfDay < 7) {
            unlock("EARLY_BIRD");
        }
    }

    /**
     * Records octave range for RANGE_MASTER achievement.
     */
    public void recordOctaveRange(int octaves) {
        if (octaves >= 3) {
            updateProgress("RANGE_MASTER", octaves);
        }
    }

    /** Unlocks by id. Ids that are not registered are ignored rather than throwing. */
    private void unlock(String id) {
        Achievement achievement = achievements.get(id);
        if (achievement != null) {
            unlock(achievement);
        }
    }

    private void unlock(Achievement achievement) {
        if (achievement.unlocked) return;

        achievement.unlocked = true;
        saveProgress(achievement);

        Log.d(TAG, "Achievement unlocked: " + achievement.name + " (+" + achievement.xpReward + " XP)");

        for (AchievementListener l : listeners) {
            l.onAchievementUnlocked(achievement);
        }
    }

    private void saveProgress(Achievement achievement) {
        prefs.edit()
                .putBoolean(KEY_UNLOCKED + achievement.id, achievement.unlocked)
                .putInt(KEY_PROGRESS + achievement.id, achievement.currentProgress)
                .apply();
    }

    public List<Achievement> getAllAchievements() {
        return new ArrayList<>(achievements.values());
    }

    public List<Achievement> getUnlockedAchievements() {
        List<Achievement> unlocked = new ArrayList<>();
        for (Achievement a : achievements.values()) {
            if (a.unlocked) unlocked.add(a);
        }
        return unlocked;
    }

    public List<Achievement> getLockedAchievements() {
        List<Achievement> locked = new ArrayList<>();
        for (Achievement a : achievements.values()) {
            if (!a.unlocked) locked.add(a);
        }
        return locked;
    }

    public Achievement getAchievement(String id) {
        return achievements.get(id);
    }

    public int getTotalXP() {
        int total = 0;
        for (Achievement a : achievements.values()) {
            if (a.unlocked) total += a.xpReward;
        }
        return total;
    }

    public int getUnlockedCount() {
        int count = 0;
        for (Achievement a : achievements.values()) {
            if (a.unlocked) count++;
        }
        return count;
    }

    public float getCompletionPercentage() {
        return (float) getUnlockedCount() / achievements.size() * 100f;
    }

    public void resetAll() {
        for (Achievement a : achievements.values()) {
            a.unlocked = false;
            a.currentProgress = 0;
        }
        prefs.edit().clear().apply();
    }

    public static class Achievement {
        public final String id;
        public final String name;
        public final String description;
        public final AchievementCategory category;
        public final int targetProgress;
        public final int xpReward;
        public final String icon;

        public boolean unlocked = false;
        public int currentProgress = 0;

        public Achievement(String id, String name, String description,
                           AchievementCategory category, int targetProgress,
                           int xpReward, String icon) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.targetProgress = targetProgress;
            this.xpReward = xpReward;
            this.icon = icon;
        }

        public float getProgressPercentage() {
            return Math.min(100f, (float) currentProgress / targetProgress * 100f);
        }

        public String getProgressText() {
            return currentProgress + "/" + targetProgress;
        }
    }

    public enum AchievementCategory {
        ACCURACY("Accuracy", Color.parseColor("#FF6B6B")),
        STREAK("Streak", Color.parseColor("#4ECDC4")),
        SCORE("Score", Color.parseColor("#FFD93D")),
        SESSION("Sessions", Color.parseColor("#6BCB77")),
        CONSISTENCY("Consistency", Color.parseColor("#4D96FF")),
        SPECIAL("Special", Color.parseColor("#FF6B9D"));

        public final String displayName;
        public final int color;

        AchievementCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }
}
