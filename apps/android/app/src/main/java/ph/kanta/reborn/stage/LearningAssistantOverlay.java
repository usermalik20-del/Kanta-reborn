package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ph.kanta.reborn.core.AudioService;
import ph.kanta.reborn.core.AudioServiceImpl;
import ph.kanta.reborn.core.LearningAssistant;
import ph.kanta.reborn.core.PitchData;
import ph.kanta.reborn.core.AchievementSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Overlay for the Learning Assistant feature.
 * Displays real-time advice and progress tracking.
 */
public class LearningAssistantOverlay extends FrameLayout {

    private static final int MAX_HISTORY = 50;

    private final TextView adviceTextView;
    private final TextView summaryTextView;
    private final TextView exerciseTextView;
    private final TextView progressTextView;
    private final View closeButton;

    private LearningAssistant learningAssistant;
    private AchievementSystem achievementSystem;
    private AudioService audioService;
    private boolean isVisible = false;
    private LearningAssistantListener listener;

    private final List<PitchData> pitchHistory = new ArrayList<>();

    public interface LearningAssistantListener {
        void onAdviceGenerated(String advice);
        void onDismissed();
    }

    public LearningAssistantOverlay(Context context) {
        this(context, null);
    }

    public LearningAssistantOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LearningAssistantOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundColor(Color.parseColor("#CC1A1A2E"));
        setPadding(24, 24, 24, 24);

        ScrollView scrollView = new ScrollView(context);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        addView(scrollView, scrollParams);

        FrameLayout content = new FrameLayout(context);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        scrollView.addView(content, contentParams);

        // Advice section
        TextView adviceLabel = new TextView(context);
        adviceLabel.setText("🤖 Voice Coach");
        adviceLabel.setTextColor(Color.parseColor("#00E676"));
        adviceLabel.setTextSize(20);
        adviceLabel.setGravity(Gravity.LEFT);
        FrameLayout.LayoutParams adviceLabelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        adviceLabelParams.topMargin = 0;
        adviceLabelParams.leftMargin = 0;
        content.addView(adviceLabel, adviceLabelParams);

        adviceTextView = new TextView(context);
        adviceTextView.setText("Start singing for personalized guidance...");
        adviceTextView.setTextColor(Color.WHITE);
        adviceTextView.setTextSize(16);
        adviceTextView.setLineSpacing(8, 1.2f);
        FrameLayout.LayoutParams adviceParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        adviceParams.topMargin = 32;
        content.addView(adviceTextView, adviceParams);

        // Progress section
        TextView progressLabel = new TextView(context);
        progressLabel.setText("📊 Session Progress");
        progressLabel.setTextColor(Color.parseColor("#FFD600"));
        progressLabel.setTextSize(18);
        FrameLayout.LayoutParams progressLabelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        progressLabelParams.topMargin = 200;
        content.addView(progressLabel, progressLabelParams);

        progressTextView = new TextView(context);
        progressTextView.setText("No data yet");
        progressTextView.setTextColor(Color.parseColor("#BBBBBB"));
        progressTextView.setTextSize(14);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = 232;
        content.addView(progressTextView, progressParams);

        // Exercise recommendation
        TextView exerciseLabel = new TextView(context);
        exerciseLabel.setText("🎵 Recommended Exercise");
        exerciseLabel.setTextColor(Color.parseColor("#4ECDC4"));
        exerciseLabel.setTextSize(18);
        FrameLayout.LayoutParams exerciseLabelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        exerciseLabelParams.topMargin = 300;
        content.addView(exerciseLabel, exerciseLabelParams);

        exerciseTextView = new TextView(context);
        exerciseTextView.setText("Will appear after singing...");
        exerciseTextView.setTextColor(Color.parseColor("#BBBBBB"));
        exerciseTextView.setTextSize(14);
        FrameLayout.LayoutParams exerciseParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        exerciseParams.topMargin = 332;
        content.addView(exerciseTextView, exerciseParams);

        // Summary section
        TextView summaryLabel = new TextView(context);
        summaryLabel.setText("📈 Performance Summary");
        summaryLabel.setTextColor(Color.parseColor("#FF6B9D"));
        summaryLabel.setTextSize(18);
        FrameLayout.LayoutParams summaryLabelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        summaryLabelParams.topMargin = 400;
        content.addView(summaryLabel, summaryLabelParams);

        summaryTextView = new TextView(context);
        summaryTextView.setText("Summary will appear after session...");
        summaryTextView.setTextColor(Color.parseColor("#BBBBBB"));
        summaryTextView.setTextSize(14);
        FrameLayout.LayoutParams summaryParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = 432;
        content.addView(summaryTextView, summaryParams);

        // Close button
        closeButton = new View(context);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        closeButton.setOnClickListener(v -> dismiss());
        addView(closeButton, closeParams);

        setVisibility(GONE);
    }

    public void setListener(LearningAssistantListener listener) {
        this.listener = listener;
    }

    public void startSession() {
        if (learningAssistant == null) {
            learningAssistant = new LearningAssistant();
        }
        if (achievementSystem == null) {
            achievementSystem = new AchievementSystem(getContext());
        }

        pitchHistory.clear();

        // Setup audio service
        if (audioService == null) {
            audioService = new AudioServiceImpl(getContext());
            audioService.addListener(new AudioService.AudioListener() {
                @Override
                public void onPitchDetected(PitchData pitchData) {
                    post(() -> {
                        updateAdvice(pitchData);
                        pitchHistory.add(pitchData);
                        if (pitchHistory.size() > MAX_HISTORY) {
                            pitchHistory.remove(0);
                        }
                    });
                }

                @Override
                public void onListeningStarted() {
                    post(() -> adviceTextView.setText("🎤 Listening... I'll analyze your pitch in real-time!"));
                }

                @Override
                public void onListeningStopped() {
                    post(() -> adviceTextView.setText("Session ended. Great practice!"));
                }

                @Override
                public void onError(String error) {
                    post(() -> adviceTextView.setText("Error: " + error));
                }
            });
        }

        audioService.startListening();
        isVisible = true;
        setVisibility(VISIBLE);
    }

    private void updateAdvice(PitchData pitchData) {
        if (learningAssistant == null) return;

        String advice = learningAssistant.generateAdvice(pitchData);
        adviceTextView.setText(advice);

        // Update progress
        float avgAccuracy = 0f;
        if (!pitchHistory.isEmpty()) {
            float sum = 0f;
            for (PitchData pd : pitchHistory) sum += pd.accuracy;
            avgAccuracy = sum / pitchHistory.size();
        }
        progressTextView.setText(String.format("Average Accuracy: %.0f%% | Notes Analyzed: %d",
                avgAccuracy * 100, pitchHistory.size()));

        // Update exercise recommendation periodically
        if (pitchHistory.size() % 10 == 0) {
            String exercise = learningAssistant.recommendExercise(pitchHistory);
            exerciseTextView.setText(exercise);
        }

        // Update summary periodically
        if (pitchHistory.size() % 20 == 0) {
            String summary = learningAssistant.generatePerformanceSummary(pitchHistory);
            summaryTextView.setText(summary);
        }

        // Check achievements
        if (achievementSystem != null) {
            int perfectCount = 0;
            for (PitchData pd : pitchHistory) {
                if (pd.accuracy >= 0.9f) perfectCount++;
            }
            achievementSystem.checkGameStats(0, 0, perfectCount, 0);
        }

        if (listener != null) listener.onAdviceGenerated(advice);
    }

    public void endSession() {
        if (audioService != null) {
            audioService.stopListening();
        }

        // Generate final summary
        if (learningAssistant != null && !pitchHistory.isEmpty()) {
            String finalSummary = learningAssistant.generatePerformanceSummary(pitchHistory);
            summaryTextView.setText(finalSummary);

            String exercise = learningAssistant.recommendExercise(pitchHistory);
            exerciseTextView.setText(exercise);

            float avgAccuracy = 0f;
            float sum = 0f;
            for (PitchData pd : pitchHistory) sum += pd.accuracy;
            avgAccuracy = sum / pitchHistory.size();
            progressTextView.setText(String.format("Final Accuracy: %.0f%% | Total Notes: %d",
                    avgAccuracy * 100, pitchHistory.size()));

            // Record session for achievements
            if (achievementSystem != null) {
                achievementSystem.recordSession(avgAccuracy >= 0.9f);
            }
        }

        isVisible = false;
        setVisibility(GONE);
        if (listener != null) listener.onDismissed();
    }

    public void dismiss() {
        endSession();
    }

    public void setSongKey(String songKey) {
        if (learningAssistant != null) {
            learningAssistant.setSongKey(songKey);
        }
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void shutdown() {
        dismiss();
        if (audioService instanceof AudioServiceImpl) {
            ((AudioServiceImpl) audioService).shutdown();
        }
    }
}
