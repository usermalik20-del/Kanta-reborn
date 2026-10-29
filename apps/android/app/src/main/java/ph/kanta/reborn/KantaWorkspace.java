package ph.kanta.reborn;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

/**
 * Compact Workspace container implementing the "Always on Stage" principle.
 *
 * The karaoke stage is the persistent base layer. Utilities (Search, Queue,
 * Visuals, Controls) are transient accessories presented above it without ever
 * tearing the stage down, so playback, lyrics and camera survive every
 * accessory transition. Only one accessory may be active at a time, so the
 * workspace can never present a stack of overlapping panels.
 *
 * This mirrors the iOS Compact Workspace composition:
 *   - iOS KantaMainPlayerView.embeddedPlayer  -> Android persistent stage layer
 *   - iOS requestStageSheet(.search/.queue)    -> Android transient accessories
 *   - iOS single-active accessory enforcement -> Android activateAccessory()
 *
 * Accessibility identity rules mirror iOS:
 *   - RC1: a container never carries the identity of its actionable children.
 *          Children keep their own stable identities and the container only
 *          bounds them (the Android equivalent of SwiftUI's
 *          `.accessibilityElement(children: .contain)`).
 *   - RC2: the control-opening action is labelled "Controls", never "Settings",
 *          because Settings is a separate navigation destination and duplicate
 *          labels make assistive-technology queries ambiguous.
 */
public final class KantaWorkspace extends FrameLayout {

  /** Callback when the workspace composition changes. */
  public interface Listener {
    void onAccessoryActivated(String accessoryId);

    void onAccessoryDeactivated(String accessoryId);
  }

  /** Stable accessory identities, mirroring the iOS workspace destinations. */
  public static final String ACCESSORY_SEARCH = "search";

  public static final String ACCESSORY_QUEUE = "queue";
  public static final String ACCESSORY_VISUALS = "visuals";
  public static final String ACCESSORY_MORE = "more";

  /** Pitch meter game accessory for pitch practice. */
  public static final String ACCESSORY_PITCH_GAME = "pitch_game";

  /** Learning assistant accessory for real-time coaching. */
  public static final String ACCESSORY_LEARNING_ASSISTANT = "learning_assistant";

  private static final int PANEL = 0xFF19213E;
  private static final int CYAN = 0xFF6FE5E8;

  private final Context context;
  private final LinearLayout accessorySheet;
  private final LinearLayout accessoryBody;
  private final ScrollView accessoryScroll;
  private String activeAccessory;
  private Listener listener;

  public KantaWorkspace(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
    // RC1 (Android): the workspace bounds its children but never overrides their
    // identities. Descendants keep their own content descriptions.
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

    accessorySheet = new LinearLayout(context);
    accessorySheet.setOrientation(LinearLayout.VERTICAL);
    accessorySheet.setBackgroundColor(PANEL);
    accessorySheet.setElevation(dp(16));
    accessorySheet.setPadding(dp(12), dp(8), dp(12), dp(12));
    accessorySheet.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

    accessoryScroll = new ScrollView(context);
    accessoryScroll.setFillViewport(true);
    accessoryBody = new LinearLayout(context);
    accessoryBody.setOrientation(LinearLayout.VERTICAL);
    accessoryScroll.addView(accessoryBody);

    accessorySheet.addView(
        accessoryScroll,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    addView(
        accessorySheet,
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    accessorySheet.setVisibility(GONE);
  }

  /**
   * Installs the persistent base layer. The stage is added first so every
   * accessory is drawn above it.
   */
  public void addPersistentLayer(View persistent) {
    LayoutParams params = (LayoutParams) persistent.getLayoutParams();
    if (params == null) params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    addView(persistent, 0, params);
  }

  /**
   * Presents one accessory above the persistent stage. Activating a different
   * accessory replaces the current one; activating the same one again closes it.
   */
  public void activateAccessory(String accessoryId) {
    if (accessoryId == null) return;
    if (accessoryId.equals(activeAccessory)) {
      deactivateAccessory();
      return;
    }
    deactivateAccessory();
    activeAccessory = accessoryId;
    accessoryBody.removeAllViews();
    accessorySheet.setVisibility(VISIBLE);
    layoutAccessory();
    if (listener != null) listener.onAccessoryActivated(accessoryId);
  }

  /** Dismisses the active accessory and leaves the stage untouched. */
  public void deactivateAccessory() {
    if (activeAccessory == null) return;
    String previous = activeAccessory;
    activeAccessory = null;
    accessoryBody.removeAllViews();
    accessorySheet.setVisibility(GONE);
    if (listener != null) listener.onAccessoryDeactivated(previous);
  }

  /** The accessory currently presented above the stage, or null. */
  public String activeAccessory() {
    return activeAccessory;
  }

  /** True when only the persistent stage is visible. */
  public boolean isStageOnly() {
    return activeAccessory == null;
  }

  /**
   * The container accessory content is added to. The stage is never detached or
   * rebuilt, so playback, lyrics and camera survive every accessory transition.
   */
  public LinearLayout accessoryContent() {
    return accessoryBody;
  }

  /** Adds a dismiss affordance above the presented accessory content. */
  public void addAccessoryHeader(String title) {
    LinearLayout header = new LinearLayout(context);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);

    TextView label = new TextView(context);
    label.setText(title);
    label.setTextColor(Color.WHITE);
    label.setTextSize(17);
    label.setTypeface(null, android.graphics.Typeface.BOLD);
    // RC1: identity belongs on the actionable child, not on the container row.
    label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

    Button close = new Button(context);
    close.setText("Done");
    close.setAllCaps(false);
    close.setTextColor(Color.WHITE);
    close.setTextSize(14);
    close.setMinimumHeight(dp(40));
    close.setBackgroundTintList(ColorStateList.valueOf(CYAN));
    close.setContentDescription("Close " + title);
    close.setOnClickListener(v -> deactivateAccessory());
    header.addView(close);

    accessorySheet.addView(
        header, 0, new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT));
  }

  /**
   * Lays the presented accessory out as a partial-height bottom sheet on
   * handsets and as an anchored side panel on large screens, matching the iOS
   * split between iPhone sheets and anchored iPad popovers.
   */
  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    layoutAccessory();
  }

  private void layoutAccessory() {
    if (accessorySheet == null || accessorySheet.getVisibility() != VISIBLE) return;
    boolean large = getResources().getConfiguration().screenWidthDp >= 700;
    LayoutParams params = (LayoutParams) accessorySheet.getLayoutParams();
    if (large) {
      params.width = (int) (getWidth() * 0.44f);
      params.height = LayoutParams.MATCH_PARENT;
      params.gravity = Gravity.END;
    } else {
      params.width = LayoutParams.MATCH_PARENT;
      params.height = LayoutParams.WRAP_CONTENT;
      params.gravity = Gravity.BOTTOM;
    }
    accessorySheet.setLayoutParams(params);
    accessorySheet.requestLayout();
  }

  /** Registers the workspace listener. */
  public void setListener(Listener listener) {
    this.listener = listener;
  }

  private int dp(int v) {
    return (int) (v * getResources().getDisplayMetrics().density);
  }
}
