package ph.kanta.reborn.core.update;

/**
 * Pure decision matrix for whether a downloaded feed entry should be acted on.
 *
 * <p>Kept free of Android types so the whole matrix is exercised by the
 * framework-free {@code coreRegression} suite. The app layer feeds in the
 * observed facts (installed version, published manifest, APK digest state) and
 * receives one reason; anything other than {@link Reason#APPLY} means no
 * download or install may start.
 */
public final class UpdateDecision {

  /** Why the offered update must not (or may) be installed. */
  public enum Reason {
    /** Freshly parsed feed with a matching signature; safe to apply. */
    APPLY,
    /** The feed has no usable {@code android} entry. */
    NOTHING_PUBLISHED,
    /** Installed platform is older than the published build requires. */
    DEVICE_TOO_OLD,
    /** Published artifact URL is not on the durable allowlist. */
    ARTIFACT_NOT_DURABLE,
    /** Published {@code versionCode} is not strictly newer than installed. */
    OLDER_OR_SAME_VERSION_PUBLISHED,
    /** The staged APK digest did not match the published SHA-256. */
    DIGEST_MISMATCH,
    /** Download was refused by transport policy (size/URL) during fetching. */
    DOWNLOAD_REFUSED
  }

  /**
   * Decides whether {@code published} is an actionable replacement for the
   * installed app described by {@code installedVersionCode}.
   *
   * @param published the validated feed entry; nullable means "no entry"
   * @param installedVersionCode installed app version code
   * @param installedMinSdk supported platform API level (usually 26)
   * @param stagedSha256 SHA-256 of the already-staged APK, or null when the
   *     digest is still unknown (nothing staged yet)
   */
  public static Reason decide(
      UpdateManifest.AndroidBuild published,
      long installedVersionCode,
      int installedMinSdk,
      String stagedSha256) {
    if (published == null) {
      return Reason.NOTHING_PUBLISHED;
    }
    if (installedMinSdk < published.minSdk()) {
      return Reason.DEVICE_TOO_OLD;
    }
    if (!UpdatePolicy.isAcceptedApkUrl(published.apkUrl())) {
      return Reason.ARTIFACT_NOT_DURABLE;
    }
    if (published.versionCode() <= installedVersionCode) {
      return Reason.OLDER_OR_SAME_VERSION_PUBLISHED;
    }
    if (stagedSha256 == null) {
      return Reason.APPLY;
    }
    if (!published.apkSha256().equalsIgnoreCase(stagedSha256)) {
      return Reason.DIGEST_MISMATCH;
    }
    return Reason.APPLY;
  }

  private UpdateDecision() {}
}