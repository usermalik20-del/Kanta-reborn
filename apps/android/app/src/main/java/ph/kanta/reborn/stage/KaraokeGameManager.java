package ph.kanta.reborn.stage;

import android.util.Log;

import ph.kanta.reborn.core.PitchData;

/**
 * Game manager for the Pitch Meter Game feature.
 * Handles scoring, streaks, achievements, and game state.
 */
public class KaraokeGameManager {

    private static final String TAG = "KaraokeGameManager";

    private static final float PERFECT_THRESHOLD = 0.9f;
    private static final float GOOD_THRESHOLD = 0.7f;
    private static final float CLOSE_THRESHOLD = 0.4f;

    private static final int PERFECT_POINTS = 100;
    private static final int GOOD_POINTS = 50;
    private static final int CLOSE_POINTS = 20;
    private static final int OFF_POINTS = 0;

    private static final int STREAK_BONUS_PER = 10;
    private static final int MAX_STREAK_BONUS = 200;

    private int score = 0;
    private int streak = 0;
    private int maxStreak = 0;
    private int perfectCount = 0;
    private int goodCount = 0;
    private int closeCount = 0;

    private float targetPitchHz = 440f;
    private boolean gameActive = false;
    private long gameStartTime = 0;

    private GameListener listener;

    public interface GameListener {
        void onScoreChanged(int score);
        void onStreakChanged(int streak);
        void onPerfectHit();
        void onGoodHit();
        void onCloseHit();
        void onMiss();
        void onAchievementUnlocked(String achievementName);
        void onGameEnded(int finalScore, int maxStreak);
    }

    public KaraokeGameManager() {}

    public void setListener(GameListener listener) {
        this.listener = listener;
    }

    public void startGame(float targetPitchHz) {
        this.targetPitchHz = targetPitchHz;
        this.score = 0;
        this.streak = 0;
        this.maxStreak = 0;
        this.perfectCount = 0;
        this.goodCount = 0;
        this.closeCount = 0;
        this.gameActive = true;
        this.gameStartTime = System.currentTimeMillis();
        Log.d(TAG, "Game started with target: " + targetPitchHz + "Hz");
    }

    public void processPitch(PitchData pitchData) {
        if (!gameActive) return;

        float accuracy = pitchData.accuracy;
        int points = 0;
        String hitType = "";

        if (accuracy >= PERFECT_THRESHOLD) {
            points = PERFECT_POINTS + Math.min(streak * STREAK_BONUS_PER, MAX_STREAK_BONUS);
            streak++;
            perfectCount++;
            hitType = "PERFECT";
            if (listener != null) listener.onPerfectHit();
        } else if (accuracy >= GOOD_THRESHOLD) {
            points = GOOD_POINTS;
            streak++;
            goodCount++;
            hitType = "GOOD";
            if (listener != null) listener.onGoodHit();
        } else if (accuracy >= CLOSE_THRESHOLD) {
            points = CLOSE_POINTS;
            streak++;
            closeCount++;
            hitType = "CLOSE";
            if (listener != null) listener.onCloseHit();
        } else {
            streak = 0;
            hitType = "MISS";
            if (listener != null) listener.onMiss();
        }

        if (streak > maxStreak) {
            maxStreak = streak;
        }

        int oldScore = score;
        score += points;
        Log.d(TAG, hitType + " hit! +" + points + " (accuracy: " + String.format("%.2f", accuracy) + ")");

        if (listener != null) {
            listener.onScoreChanged(score);
            listener.onStreakChanged(streak);
        }

        checkAchievements();
    }

    public void endGame() {
        gameActive = false;
        long duration = System.currentTimeMillis() - gameStartTime;
        Log.d(TAG, "Game ended. Score: " + score + ", Max streak: " + maxStreak + ", Duration: " + duration + "ms");

        if (listener != null) {
            listener.onGameEnded(score, maxStreak);
        }
    }

    public void pauseGame() {
        gameActive = false;
    }

    public void resumeGame() {
        gameActive = true;
    }

    private void checkAchievements() {
        // Perfect streak achievements
        if (streak == 5 && perfectCount >= 5) {
            unlockAchievement("PERFECT_STREAK_5");
        } else if (streak == 10 && perfectCount >= 8) {
            unlockAchievement("PERFECT_STREAK_10");
        } else if (streak == 20) {
            unlockAchievement("UNSTOPPABLE");
        }

        // Score milestones
        if (score >= 1000 && score < 1100) {
            unlockAchievement("SCORE_1000");
        } else if (score >= 5000 && score < 5100) {
            unlockAchievement("SCORE_5000");
        } else if (score >= 10000) {
            unlockAchievement("SCORE_10000");
        }

        // Accuracy achievements
        if (perfectCount >= 10) {
            unlockAchievement("PITCH_PERFECT");
        } else if (perfectCount >= 50) {
            unlockAchievement("MAESTRO");
        }

        // Streak achievements
        if (maxStreak >= 10) {
            unlockAchievement("STREAK_10");
        } else if (maxStreak >= 25) {
            unlockAchievement("STREAK_25");
        }
    }

    private void unlockAchievement(String achievementName) {
        Log.d(TAG, "Achievement unlocked: " + achievementName);
        if (listener != null) {
            listener.onAchievementUnlocked(achievementName);
        }
    }

    public int getScore() {
        return score;
    }

    public int getStreak() {
        return streak;
    }

    public int getMaxStreak() {
        return maxStreak;
    }

    public int getPerfectCount() {
        return perfectCount;
    }

    public int getGoodCount() {
        return goodCount;
    }

    public int getCloseCount() {
        return closeCount;
    }

    public boolean isGameActive() {
        return gameActive;
    }

    public float getTargetPitchHz() {
        return targetPitchHz;
    }

    public long getGameDuration() {
        return gameActive ? System.currentTimeMillis() - gameStartTime : 0;
    }

    public GameStats getStats() {
        return new GameStats(score, streak, maxStreak, perfectCount, goodCount, closeCount);
    }

    public static class GameStats {
        public final int score;
        public final int streak;
        public final int maxStreak;
        public final int perfectCount;
        public final int goodCount;
        public final int closeCount;

        public GameStats(int score, int streak, int maxStreak, int perfectCount, int goodCount, int closeCount) {
            this.score = score;
            this.streak = streak;
            this.maxStreak = maxStreak;
            this.perfectCount = perfectCount;
            this.goodCount = goodCount;
            this.closeCount = closeCount;
        }
    }
}
