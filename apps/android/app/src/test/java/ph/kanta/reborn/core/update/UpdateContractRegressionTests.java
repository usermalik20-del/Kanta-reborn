package ph.kanta.reborn.core.update;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Framework-free regression coverage for the OTA update feed contract.
 *
 * <p>Runs under the empty-classpath {@code coreRegression} task, so every
 * behaviour exercised here is pure JDK. The expectations below were written
 * from the documented feed contract (strict JSON, exact field shapes, durable
 * HTTPS artifact URLs only) rather than copied out of the implementation.
 */
public final class UpdateContractRegressionTests {

  private static final String SHA64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String SOURCE = "0123456789abcdef0123456789abcdef01234567";
  private static final String APK_URL =
      "https://github.com/usermalik20-del/Kanta-reborn/releases/download/"
          + "v0.11.0-preview-9ab1cd23/KANTA-Android-preview-9ab1cd23.apk";

  private static int checks;

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }

  /** A fully valid feed document. */
  private static String feed(String... overrides) {
    Map<String, String> values = new HashMap<>();
    values.put("format", "\"kanta-update-feed\"");
    values.put("formatVersion", "1");
    values.put("channel", "\"stable-reconstruction\"");
    values.put("publishedAt", "\"2026-11-04T01:23:45Z\"");
    values.put("sourceCommit", "\"" + SOURCE + "\"");
    values.put("android.versionCode", "11");
    values.put("android.versionName", "\"0.11.0-reconstruction\"");
    values.put("android.minSdk", "26");
    values.put("android.apkUrl", "\"" + APK_URL + "\"");
    values.put("android.apkBytes", "52767115");
    values.put("android.apkSha256", "\"" + SHA64 + "\"");
    values.put("android.signingCertSha256", "\"" + SHA64 + "\"");
    values.put("android.notes", "\"Local preflight\"");
    values.put("android.mandatory", "false");
    for (String override : overrides) {
      int equals = override.indexOf('=');
      values.put(override.substring(0, equals), override.substring(equals + 1));
    }
    StringBuilder json = new StringBuilder("{");
    String[] keys = {
      "format", "formatVersion", "channel", "publishedAt", "sourceCommit",
      "android.versionCode", "android.versionName", "android.minSdk",
      "android.apkUrl", "android.apkBytes", "android.apkSha256",
      "android.signingCertSha256", "android.notes", "android.mandatory"
    };
    for (int i = 0; i < keys.length; i++) {
      if (i > 0) json.append(',');
      String flat = keys[i];
      if (flat.startsWith("android.")) {
        if (flat.equals("android.versionCode")) json.append("\"android\":{");
        json.append('"').append(flat.substring("android.".length())).append("\":");
      } else {
        json.append('"').append(flat).append("\":");
      }
      json.append(values.get(flat));
      String next = i + 1 < keys.length ? keys[i + 1] : "";
      if (flat.startsWith("android.") && (!next.startsWith("android.") || next.equals(""))) {
        json.append('}');
      }
    }
    return json.append('}').toString();
  }

  private static void jsonChecks() {
    // A canonical feed parses into the expected tree shapes.
    Map<?, ?> tree = (Map<?, ?>) requireParse(feed());
    check(tree.get("format").equals("kanta-update-feed"), "format value");
    check(tree.containsKey("android"), "android object present");

    checkRejected("{\"a\":1,\"a\":2}", "duplicate object keys accepted");
    checkRejected("{\"a\":\"tab\there\"}", "raw control character accepted");
    checkRejected("{\"a\":\"\ud800\"}", "lone high surrogate accepted");
    checkRejected("{\"a\":\"\udc00\"}", "lone low surrogate accepted");
    checkRejected("{\"a\":1,}", "trailing comma accepted");
    check(requireParse("[1,2]") instanceof java.util.List, "array root must parse as a list");
    checkRejected("{\"a\":01}", "leading zero accepted");
    checkRejected("{\"a\":+.5}", "plus sign accepted");
    checkRejected("{\"a\":NaN}", "NaN accepted");
    checkRejected("{\"a\":Infinity}", "Infinity accepted");
    checkRejected("{\"a\":\"\\x41\"}", "unknown escape accepted");
    checkRejected("{\"a\":\"\\ud800\"}", "escaped lone surrogate accepted");
    check(requireParse("{\"a\":\"\\ud800\\udc00\"}") != null, "escaped surrogate pair must parse");
    checkRejected("{", "unterminated object accepted");
    checkRejected("{}x", "trailing content accepted");
    checkRejected("\ufeff{}", "BOM accepted");
    checkRejected(depth(12), "over-deep nesting accepted");
    Object parsed;
    try {
      parsed = UpdateJson.parse("{\"a\":\"\\ud83d\\ude00\"}");
      check(parsed instanceof Map, "escaped pair tree type");
      check(((Map<?, ?>) parsed).get("a").equals("\ud83d\ude00"), "escaped pair value");
    } catch (UpdateJson.Malformed bad) {
      throw new AssertionError("valid escape pair rejected: " + bad.getMessage());
    }
  }

  private static String depth(int level) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < level; i++) builder.append('[');
    builder.append('1');
    for (int i = 0; i < level; i++) builder.append(']');
    return builder.toString();
  }

  private static Object requireParse(String text) {
    try {
      return UpdateJson.parse(text);
    } catch (UpdateJson.Malformed bad) {
      throw new AssertionError("valid JSON rejected: " + bad.getMessage());
    }
  }

  private static void checkRejected(String text, String message) {
    try {
      UpdateJson.parse(text);
      check(false, message);
    } catch (UpdateJson.Malformed expected) {
      check(true, "rejection confirmed: " + message);
    }
  }

  private static void manifestChecks() throws Exception {
    UpdateManifest manifest = UpdateManifest.parseText(feed());
    check(manifest.channel().equals("stable-reconstruction"), "manifest channel");
    check(manifest.android().versionCode() == 11, "manifest versionCode");
    check(manifest.android().apkBytes() == 52767115, "manifest apkBytes");
    check(manifest.android().apkSha256().equals(SHA64), "manifest apkSha256");
    check(!manifest.android().mandatory(), "manifest mandatory flag");

    checkRejectedManifest(feed("format=\"other\""), "foreign format accepted");
    checkRejectedManifest(feed("formatVersion=2"), "unknown formatVersion accepted");
    checkRejectedManifest(feed("channel=\"beta\""), "unknown channel accepted");
    checkRejectedManifest(feed("publishedAt=\"2026/11/04\""), "non-ISO publishedAt accepted");
    checkRejectedManifest(feed("sourceCommit=\"ABC\""), "non-hex sourceCommit accepted");
    checkRejectedManifest(feed("android.minSdk=24"), "minSdk mismatch accepted");
    checkRejectedManifest(feed("android.versionCode=0"), "zero versionCode accepted");
    checkRejectedManifest(feed("android.apkBytes=-5"), "negative apkBytes accepted");
    checkRejectedManifest(feed("android.apkBytes=999999999999"), "oversized apkBytes accepted");
    checkRejectedManifest(feed("android.apkSha256=\"zzz\""), "bad apkSha256 accepted");
    checkRejectedManifest(feed("android.signingCertSha256=\"zzz\""), "bad cert digest accepted");
    checkRejectedManifest(feed("android.mandatory=\"yes\""), "non-boolean mandatory accepted");
  }

  private static void checkRejectedManifest(String text, String message) {
    try {
      UpdateManifest.parseText(text);
      check(false, message);
    } catch (UpdateManifest.Malformed expected) {
      check(true, "manifest rejection confirmed: " + message);
    }
  }

  private static void policyChecks() {
    check(UpdatePolicy.isAcceptedApkUrl(APK_URL), "canonical release URL refused");
    check(
        !UpdatePolicy.isAcceptedApkUrl(
            "https://github.com/usermalik20-del/Kanta-reborn/releases/latest/download/KANTA.apk"),
        "latest-release shortcut must not bypass the pinned release path");
    check(
        UpdatePolicy.isAcceptedApkUrl(
            "https://release-assets.githubusercontent.com/repository-assets/123?sig=abc"),
        "release CDN target refused");
    check(
        UpdatePolicy.isAcceptedApkUrl(
            "https://objects.githubusercontent.com/github-production-release-asset/123?expires=1"),
        "objects CDN target refused");
    check(!UpdatePolicy.isAcceptedApkUrl(null), "null URL accepted");
    check(!UpdatePolicy.isAcceptedApkUrl(""), "empty URL accepted");
    check(
        !UpdatePolicy.isAcceptedApkUrl(
            "https://github.com/usermalik20-del/Kanta-reborn/actions/runs/1/artifacts/2"),
        "Actions artifact URL accepted");
    check(
        !UpdatePolicy.isAcceptedApkUrl(
            "https://github.com/usermalik20-del/Kanta-reborn/releases/download/t/f.apk?x=1"),
        "query on canonical URL accepted");
    check(
        !UpdatePolicy.isAcceptedApkUrl(
            "https://github.com/usermalik20-del/Kanta-reborn/releases/download/../etc/passwd"),
        "path traversal accepted");
    check(!UpdatePolicy.isAcceptedApkUrl("http://github.com/usermalik20-del/Kanta-reborn/releases/download/t/f"),
        "http canonical accepted");
    check(
        !UpdatePolicy.isAcceptedApkUrl("https://user@github.com/usermalik20-del/Kanta-reborn/releases/download/t/f"),
        "userinfo accepted");
    check(!UpdatePolicy.isAcceptedApkUrl("https://github.com/releases/download/t/f"), "misrouted path accepted");
    check(
        !UpdatePolicy.isAcceptedApkUrl("https://github.com/other/repo/releases/download/t/f.apk"),
        "foreign repository release URL accepted");
    check(!UpdatePolicy.isAcceptedApkUrl("https://evil.example/k.apk"), "foreign host accepted");
  }

  private static void digestChecks() throws Exception {
    check(
        UpdateDigest.sha256Hex("abc".getBytes(StandardCharsets.US_ASCII))
            .equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
        "SHA-256(NIST vector) mismatch");
    check(UpdateDigest.sha256Hex(new byte[0])
        .equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        "SHA-256(empty) mismatch");
    check(UpdateDigest.stagingName(SHA64).equals("." + SHA64 + ".part"), "staging name shape");
    boolean refused = false;
    try {
      UpdateDigest.stagingName("nope");
    } catch (IllegalArgumentException expected) {
      refused = true;
    }
    check(refused, "staging name accepted a non-hex digest");
  }

  private static void decisionChecks() throws Exception {
    UpdateManifest.AndroidBuild build = UpdateManifest.parseText(feed()).android();
    check(
        UpdateDecision.decide(null, 1, 26, null)
            == UpdateDecision.Reason.NOTHING_PUBLISHED,
        "null entry not refused");
    UpdateManifest.AndroidBuild oldSdk = UpdateManifest.parseText(feed("android.minSdk=29")).android();
    check(
        UpdateDecision.decide(oldSdk, 1, 26, null)
            == UpdateDecision.Reason.DEVICE_TOO_OLD,
        "device too old not refused");
    UpdateManifest.AndroidBuild artifact = UpdateManifest.parseText(
            feed("android.apkUrl=\"https://github.com/usermalik20-del/Kanta-reborn/actions/runs/1/artifacts/2\""))
        .android();
    check(
        UpdateDecision.decide(artifact, 1, 26, null)
            == UpdateDecision.Reason.ARTIFACT_NOT_DURABLE,
        "non-durable artifact not refused");
    check(
        UpdateDecision.decide(build, 11, 26, null)
            == UpdateDecision.Reason.OLDER_OR_SAME_VERSION_PUBLISHED,
        "same version not refused");
    check(
        UpdateDecision.decide(build, 99, 26, null)
            == UpdateDecision.Reason.OLDER_OR_SAME_VERSION_PUBLISHED,
        "older version not refused");
    check(
        UpdateDecision.decide(build, 10, 26, SHA64)
            == UpdateDecision.Reason.APPLY,
        "matching digest not applied");
    check(
        UpdateDecision.decide(build, 10, 26, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
            == UpdateDecision.Reason.DIGEST_MISMATCH,
        "digest mismatch not refused");
  }

  public static int run() throws Exception {
    jsonChecks();
    manifestChecks();
    policyChecks();
    digestChecks();
    decisionChecks();
    return checks;
  }
}