package ph.kanta.reborn.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Names and stable slot identifiers for the bundled party effects. */
public final class PartySounds {

  public static final List<String> TITLES =
      Collections.unmodifiableList(
          Arrays.asList(
              "Applause",
              "Cheering",
              "Drum Roll",
              "Air Horn",
              "Laughter",
              "Ta-Da!",
              "Ding",
              "Sad Trombone"));

  /** Effect slots the score reveal reuses by name, so the two stay in step. */
  public static final int APPLAUSE = 0, CHEERING = 1, DRUM_ROLL = 2, AIR_HORN = 3, LAUGHTER = 4, TA_DA = 5, DING = 6, SAD_TROMBONE = 7;

  private PartySounds() {}
}
