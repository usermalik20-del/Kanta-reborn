package ph.kanta.reborn.core.update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Typed, strictly validated update feed entry for the KANTA Android app.
 *
 * <p>All validation happens here and in {@link UpdateJson}; an entry that does
 * not satisfy every requirement below is rejected wholesale so a partially
 * corrupted feed can never be acted on. Field values are validated as exact
 * shapes (SHA-256 hex, decimal integers, HTTPS URLs) rather than being
 * coerced.
 */
public final class UpdateManifest {

  /** Feed shape tag this parser understands. */
  public static final String FORMAT = "kanta-update-feed";
  /** Feed shape version within the format tag. */
  public static final int FORMAT_VERSION = 1;
  /** Channel name the stock app polls. */
  public static final String CHANNEL_STABLE = "stable-reconstruction";
  /** Minimum API level the Android build claims. */
  public static final int ANDROID_MIN_SDK = 26;

  private final String format;
  private final String channel;
  private final String publishedAt;
  private final String sourceCommit;
  private final AndroidBuild android;

  private UpdateManifest(
      String format, String channel, String publishedAt, String sourceCommit, AndroidBuild android) {
    this.format = format;
    this.channel = channel;
    this.publishedAt = publishedAt;
    this.sourceCommit = sourceCommit;
    this.android = android;
  }

  public String format() {
    return format;
  }

  public String channel() {
    return channel;
  }

  /** ISO-8601 UTC publication instant, e.g. {@code 2026-11-04T01:00:00Z}. */
  public String publishedAt() {
    return publishedAt;
  }

  /** Full lowercase hex SHA-256 (Git commit) the Android APK was built from. */
  public String sourceCommit() {
    return sourceCommit;
  }

  public AndroidBuild android() {
    return android;
  }

  /** Build requirements for the one Android package this feed publishes. */
  public static final class AndroidBuild {
    private final long versionCode;
    private final String versionName;
    private final int minSdk;
    private final String apkUrl;
    private final long apkBytes;
    private final String apkSha256;
    private final String signingCertSha256;
    private final String notes;
    private final boolean mandatory;

    private AndroidBuild(
        long versionCode,
        String versionName,
        int minSdk,
        String apkUrl,
        long apkBytes,
        String apkSha256,
        String signingCertSha256,
        String notes,
        boolean mandatory) {
      this.versionCode = versionCode;
      this.versionName = versionName;
      this.minSdk = minSdk;
      this.apkUrl = apkUrl;
      this.apkBytes = apkBytes;
      this.apkSha256 = apkSha256;
      this.signingCertSha256 = signingCertSha256;
      this.notes = notes;
      this.mandatory = mandatory;
    }

    /** Monotonic build counter from the Android manifest (human readable suffix). */
    public long versionCode() {
      return versionCode;
    }

    public String versionName() {
      return versionName;
    }

    /** Minimum platform API level the published APK requires. */
    public int minSdk() {
      return minSdk;
    }

    /** Durable HTTPS URL of the APK artifact (release asset, never an Actions artifact). */
    public String apkUrl() {
      return apkUrl;
    }

    /** Exact expected APK byte count; downloads exceeding it are discarded. */
    public long apkBytes() {
      return apkBytes;
    }

    /** Lowercase hex SHA-256 of the APK file. */
    public String apkSha256() {
      return apkSha256;
    }

    /** Optional lowercase hex SHA-256 of the APK signing certificate. */
    public String signingCertSha256() {
      return signingCertSha256;
    }

    /** Optional human release notes; never rendered, only displayed as-is. */
    public String notes() {
      return notes;
    }

    /** True only when the publisher marks this build as forced. */
    public boolean mandatory() {
      return mandatory;
    }
  }

  /** Raised for malformed, ambiguous, hostile or out-of-contract feeds. */
  public static final class Malformed extends Exception {
    public Malformed(String message) {
      super(message);
    }
  }/**
   * Parses and validates a whole feed document.
   *
   * <p>The document must be a JSON object with exactly the expected shape.
   * Unknown siblings are rejected so feed drift cannot silently change
   * behaviour; {@code android} is required and must satisfy every build
   * requirement. Returns a manifest whose methods are all pre-validated.
   */
  public static UpdateManifest parseText(String text) throws Malformed {
    Object root;
    try {
      root = UpdateJson.parse(text);
    } catch (UpdateJson.Malformed json) {
      throw new Malformed("feed is not valid JSON: " + json.getMessage());
    }
    if (!(root instanceof Map)) {
      throw new Malformed("feed root must be a JSON object");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> object = (Map<String, Object>) root;
    return of(object);
  }

  /** Validates an already-parsed feed object tree (used by tests and callers). */
  public static UpdateManifest of(Map<String, Object> feed) throws Malformed {
    if (feed == null) {
      throw new Malformed("feed object is absent");
    }
    requireOnly(feed, List.of("format", "formatVersion", "channel", "publishedAt", "sourceCommit", "android"), "feed");
    String format = requireString(feed, "format");
    if (!FORMAT.equals(format)) {
      throw new Malformed("unsupported feed format: " + format);
    }
    Integer version = requireInt(feed, "formatVersion");
    if (version != FORMAT_VERSION) {
      throw new Malformed("unsupported feed formatVersion: " + version);
    }
    String channel = requireString(feed, "channel");
    if (!CHANNEL_STABLE.equals(channel)) {
      throw new Malformed("unknown feed channel: " + channel);
    }
    String publishedAt = requireString(feed, "publishedAt");
    if (!publishedAt.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
      throw new Malformed("publishedAt must be ISO-8601 UTC: " + publishedAt);
    }
    String sourceCommit = requireString(feed, "sourceCommit");
    if (!sourceCommit.matches("[0-9a-f]{40}")) {
      throw new Malformed("sourceCommit must be a lowercase 40-hex SHA");
    }
    Object rawAndroid = feed.get("android");
    if (!(rawAndroid instanceof Map)) {
      throw new Malformed("android build entry is required and must be an object");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> android = (Map<String, Object>) rawAndroid;
    AndroidBuild build = parseAndroid(android);
    return new UpdateManifest(format, channel, publishedAt, sourceCommit, build);
  }

  private static AndroidBuild parseAndroid(Map<String, Object> android) throws Malformed {
    requireOnly(
        android,
        List.of("versionCode", "versionName", "minSdk", "apkUrl", "apkBytes", "apkSha256",
            "signingCertSha256", "notes", "mandatory"),
        "android build entry");
    long versionCode = requireLong(android, "versionCode");
    if (versionCode < 1) {
      throw new Malformed("versionCode must be a positive integer");
    }
    String versionName = requireString(android, "versionName");
    if (versionName.isEmpty() || versionName.length() > 64 || !versionName.matches("[A-Za-z0-9.+-]+")) {
      throw new Malformed("versionName is outside the allowed shape: " + versionName);
    }
    int minSdk = requireInt(android, "minSdk");
    if (minSdk != ANDROID_MIN_SDK) {
      throw new Malformed("minSdk must equal " + ANDROID_MIN_SDK + ", got " + minSdk);
    }
    String apkUrl = requireString(android, "apkUrl");
    if (!UpdatePolicy.isAcceptedApkUrl(apkUrl)) {
      throw new Malformed("apkUrl is not an accepted durable HTTPS URL: " + apkUrl);
    }
    long apkBytes = requireLong(android, "apkBytes");
    if (apkBytes < 1 || apkBytes > UpdatePolicy.MAX_APK_BYTES) {
      throw new Malformed("apkBytes must be within 1.." + UpdatePolicy.MAX_APK_BYTES);
    }
    String apkSha256 = requireString(android, "apkSha256");
    if (!apkSha256.matches("[0-9a-f]{64}")) {
      throw new Malformed("apkSha256 must be a lowercase 64-hex SHA-256");
    }
    String signingCertSha256 = optionalString(android, "signingCertSha256");
    if (signingCertSha256 != null && !signingCertSha256.matches("[0-9a-f]{64}")) {
      throw new Malformed("signingCertSha256 must be a lowercase 64-hex SHA-256");
    }
    String notes = optionalString(android, "notes");
    if (notes != null && notes.length() > 1024) {
      throw new Malformed("notes must be 1024 characters or fewer");
    }
    boolean mandatory = requireBoolean(android, "mandatory");
    return new AndroidBuild(
        versionCode, versionName, minSdk, apkUrl, apkBytes, apkSha256,
        signingCertSha256, notes, mandatory);
  }

  private static String requireString(Map<String, Object> object, String key) throws Malformed {
    Object value = object.get(key);
    if (!(value instanceof String) || ((String) value).isEmpty()) {
      throw new Malformed("missing or non-string field: " + key);
    }
    return (String) value;
  }

  private static String optionalString(Map<String, Object> object, String key) throws Malformed {
    Object value = object.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new Malformed("field must be a string when present: " + key);
    }
    return (String) value;
  }

  private static Integer requireInt(Map<String, Object> object, String key) throws Malformed {
    BigDecimal value = requireNumber(object, key);
    try {
      return value.intValueExact();
    } catch (ArithmeticException overflow) {
      throw new Malformed("field is not an integer: " + key);
    }
  }

  private static long requireLong(Map<String, Object> object, String key) throws Malformed {
    BigDecimal value = requireNumber(object, key);
    try {
      return value.longValueExact();
    } catch (ArithmeticException overflow) {
      throw new Malformed("field is out of range: " + key);
    }
  }

  private static BigDecimal requireNumber(Map<String, Object> object, String key) throws Malformed {
    Object value = object.get(key);
    if (!(value instanceof BigDecimal)) {
      throw new Malformed("missing or non-number field: " + key);
    }
    return (BigDecimal) value;
  }

  private static boolean requireBoolean(Map<String, Object> object, String key) throws Malformed {
    Object value = object.get(key);
    if (value == null) {
      throw new Malformed("missing required boolean field: " + key);
    }
    if (!(value instanceof Boolean)) {
      throw new Malformed("field must be true or false: " + key);
    }
    return (Boolean) value;
  }

  private static void requireOnly(
      Map<String, Object> object, List<String> allowed, String what) throws Malformed {
    for (String key : object.keySet()) {
      if (!allowed.contains(key)) {
        throw new Malformed("unexpected field in " + what + ": " + key);
      }
    }
  }

  /** Field-name guard used by tooling to keep feed generation aligned. */
  static List<String> requiredFields() {
    return List.of(
        "format", "formatVersion", "channel", "publishedAt", "sourceCommit", "android");
  }
}