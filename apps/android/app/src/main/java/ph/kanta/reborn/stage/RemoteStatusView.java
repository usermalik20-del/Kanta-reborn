package ph.kanta.reborn.stage;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * In-player indicator showing Smart Link host state.
 *
 * <p>Per {@link replica/design/components.md} RemoteStatusView spec.
 */
public final class RemoteStatusView extends FrameLayout {

  public enum State {
    DISCONNECTED,
    ADVERTISING,
    CONNECTED,
    ERROR
  }

  private final ImageView iconView;
  private final TextView labelView;
  private final Paint pulsePaint;
  private State currentState = State.DISCONNECTED;
  private int connectedCount = 0;

  public RemoteStatusView(Context context) {
    this(context, null);
  }

  public RemoteStatusView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setWillNotDraw(false);

    float density = context.getResources().getDisplayMetrics().density;

    LinearLayout container = new LinearLayout(context);
    container.setOrientation(LinearLayout.HORIZONTAL);
    container.setGravity(android.view.Gravity.CENTER_VERTICAL);
    container.setPadding((int)(8 * density), (int)(4 * density), (int)(8 * density), (int)(4 * density));
    LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    params.gravity = android.view.Gravity.CENTER_VERTICAL;
    addView(container, params);

    iconView = new ImageView(context);
    iconView.setLayoutParams(new LinearLayout.LayoutParams(
        (int)(20 * density), (int)(20 * density)));
    container.addView(iconView);

    labelView = new TextView(context);
    labelView.setTextSize(12);
    labelView.setTypeface(null, android.graphics.Typeface.BOLD);
    labelView.setPadding((int)(8 * density), 0, 0, 0);
    container.addView(labelView);

    pulsePaint = new Paint();
    pulsePaint.setColor(0xFFFFB03A); // warning

    updateDisplay();
  }

  /** Sets the connection state. */
  public void setState(State state) {
    this.currentState = state;
    updateDisplay();
  }

  /** Sets the connected client count. */
  public void setConnectedCount(int count) {
    this.connectedCount = count;
    if (currentState == State.CONNECTED) {
      updateDisplay();
    }
  }

  private void updateDisplay() {
    switch (currentState) {
      case DISCONNECTED:
        iconView.setColorFilter(0xFF999999); // textTertiary
        iconView.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); // placeholder
        labelView.setText("Offline");
        labelView.setTextColor(0xFF999999);
        break;
      case ADVERTISING:
        iconView.setColorFilter(0xFFFFB03A); // warning
        iconView.setImageResource(android.R.drawable.ic_menu_upload); // placeholder
        labelView.setText("Waiting...");
        labelView.setTextColor(0xFFFFB03A);
        startPulse();
        break;
      case CONNECTED:
        iconView.setColorFilter(0xFF3DE0A8); // success
        iconView.setImageResource(android.R.drawable.ic_menu_manage); // placeholder
        labelView.setText(connectedCount + " connected");
        labelView.setTextColor(0xFF3DE0A8);
        stopPulse();
        break;
      case ERROR:
        iconView.setColorFilter(0xFFFF5A6E); // destructive
        iconView.setImageResource(android.R.drawable.ic_dialog_alert);
        labelView.setText("Failed");
        labelView.setTextColor(0xFFFF5A6E);
        stopPulse();
        break;
    }
  }

  private void startPulse() {
    animate().alpha(0.4f).setDuration(350).withEndAction(() -> {
      if (currentState == State.ADVERTISING) {
        animate().alpha(1f).setDuration(350).withEndAction(this::startPulse).start();
      }
    }).start();
  }

  private void stopPulse() {
    animate().cancel();
    setAlpha(1f);
  }
}
