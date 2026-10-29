package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import ph.kanta.reborn.core.AudioService;
import ph.kanta.reborn.core.AudioServiceImpl;
import ph.kanta.reborn.core.PitchData;

/**
 * Overlay for the Pitch Meter Game feature.
 * Displays real-time pitch feedback and game UI.
 */
public class PitchGameOverlay extends FrameLayout {

    private static final int OVERLAY_WIDTH = 800;
    private static final int OVERLAY_HEIGHT = 400;

    private final PitchMeterView pitchMeterView;
    private final TextView scoreTextView;
    private final TextView streakTextView;
    private final TextView statusTextView;
    private final View closeButton;

    private KaraokeGameManager gameManager;
    private AudioService audioService;
    private boolean isVisible = false;
    private PitchGameListener listener;

    public interface PitchGameListener {
        void onGameStarted();
        void onGameEnded(int score, int maxStreak);
        void onDismissed();
    }

    public PitchGameOverlay(Context context) {
        this(context, null);
    }

    public PitchGameOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PitchGameOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.parseColor("#CC000000"));
        setPadding(32, 32, 32, 32);

        // Create pitch meter view
        pitchMeterView = new PitchMeterView(context);
        FrameLayout.LayoutParams meterParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 200);
        meterParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        meterParams.topMargin = 20;
        addView(pitchMeterView, meterParams);

        // Score display
        scoreTextView = new TextView(context);
        scoreTextView.setText("Score: 0");
        scoreTextView.setTextColor(Color.WHITE);
        scoreTextView.setTextSize(24);
        scoreTextView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scoreParams.gravity = Gravity.TOP | Gravity.LEFT;
        scoreParams.topMargin = 240;
        scoreParams.leftMargin = 20;
        addView(scoreTextView, scoreParams);

        // Streak display
        streakTextView = new TextView(context);
        streakTextView.setText("Streak: 0");
        streakTextView.setTextColor(Color.parseColor("#FFD600"));
        streakTextView.setTextSize(20);
        streakTextView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams streakParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        streakParams.gravity = Gravity.TOP | Gravity.RIGHT;
        streakParams.topMargin = 240;
        streakParams.rightMargin = 20;
        addView(streakTextView, streakParams);

        // Status text
        statusTextView = new TextView(context);
        statusTextView.setText("Tap to start pitch practice");
        statusTextView.setTextColor(Color.parseColor("#BBBBBB"));
        statusTextView.setTextSize(16);
        statusTextView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        statusParams.bottomMargin = 20;
        addView(statusTextView, statusParams);

        // Close button area (tap outside to dismiss)
        closeButton = new View(context);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        closeButton.setOnClickListener(v -> dismiss());
        addView(closeButton, closeParams);

        setVisibility(GONE);
    }

    public void setListener(PitchGameListener listener) {
        this.listener = listener;
    }

    public void startGame(float targetPitchHz) {
        if (gameManager == null) {
            gameManager = new KaraokeGameManager();
            gameManager.setListener(new KaraokeGameManager.GameListener() {
                @Override
                public void onScoreChanged(int score) {
                    post(() -> scoreTextView.setText("Score: " + score));
                }

                @Override
                public void onStreakChanged(int streak) {
                    post(() -> streakTextView.setText("Streak: " + streak));
                }

                @Override
                public void onPerfectHit() {
                    pitchMeterView.triggerSuccess();
                    post(() -> statusTextView.setText("🎯 PERFECT!"));
                }

                @Override
                public void onGoodHit() {
                    post(() -> statusTextView.setText("👍 GOOD!"));
                }

                @Override
                public void onCloseHit() {
                    post(() -> statusTextView.setText("📏 Close!"));
                }

                @Override
                public void onMiss() {
                    post(() -> statusTextView.setText("💫 Keep trying!"));
                }

                @Override
                public void onAchievementUnlocked(String achievementName) {
                    post(() -> statusTextView.setText("🏆 " + achievementName + " unlocked!"));
                }

                @Override
                public void onGameEnded(int finalScore, int maxStreak) {
                    post(() -> {
                        statusTextView.setText("Game Over! Final Score: " + finalScore);
                        if (listener != null) listener.onGameEnded(finalScore, maxStreak);
                    });
                }
            });
        }

        gameManager.startGame(targetPitchHz);
        pitchMeterView.setTargetPitch(targetPitchHz);
        pitchMeterView.setScore(0);
        pitchMeterView.setStreak(0);

        // Setup audio service
        if (audioService == null) {
            audioService = new AudioServiceImpl(getContext());
            ((AudioServiceImpl) audioService).setTargetPitch(targetPitchHz);
            audioService.addListener(new AudioService.AudioListener() {
                @Override
                public void onPitchDetected(PitchData pitchData) {
                    post(() -> {
                        pitchMeterView.updatePitch(pitchData);
                        if (gameManager != null && gameManager.isGameActive()) {
                            gameManager.processPitch(pitchData);
                        }
                    });
                }

                @Override
                public void onListeningStarted() {
                    post(() -> statusTextView.setText("🎤 Listening... Sing the target note!"));
                }

                @Override
                public void onListeningStopped() {
                    post(() -> statusTextView.setText("Stopped"));
                }

                @Override
                public void onError(String error) {
                    post(() -> statusTextView.setText("Error: " + error));
                }
            });
        }

        audioService.startListening();
        isVisible = true;
        setVisibility(VISIBLE);
        statusTextView.setText("🎤 Listening... Sing the target note!");

        if (listener != null) listener.onGameStarted();
    }

    public void dismiss() {
        if (audioService != null) {
            audioService.stopListening();
        }
        if (gameManager != null) {
            gameManager.endGame();
        }
        isVisible = false;
        setVisibility(GONE);
        if (listener != null) listener.onDismissed();
    }

    public void pauseGame() {
        if (gameManager != null) gameManager.pauseGame();
        if (audioService != null) audioService.stopListening();
    }

    public void resumeGame() {
        if (gameManager != null) gameManager.resumeGame();
        if (audioService != null) audioService.startListening();
    }

    public boolean isVisible() {
        return isVisible;
    }

    public int getCurrentScore() {
        return gameManager != null ? gameManager.getScore() : 0;
    }

    public int getCurrentStreak() {
        return gameManager != null ? gameManager.getStreak() : 0;
    }

    public void setTargetPitch(float targetPitchHz) {
        if (pitchMeterView != null) pitchMeterView.setTargetPitch(targetPitchHz);
        if (audioService instanceof AudioServiceImpl) {
            ((AudioServiceImpl) audioService).setTargetPitch(targetPitchHz);
        }
        if (gameManager != null && !gameManager.isGameActive()) {
            // Only update if not actively playing
        }
    }

    public void shutdown() {
        dismiss();
        if (audioService instanceof AudioServiceImpl) {
            ((AudioServiceImpl) audioService).shutdown();
        }
    }
}
