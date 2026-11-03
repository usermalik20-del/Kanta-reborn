package ph.kanta.reborn.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;
import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import ph.kanta.reborn.core.update.UpdateDigest;

/** Installation-time facts about the running app and the staged package. */
public final class UpdatePackageInspector {

  /** Lowercase hex SHA-256 of the APK's signing certificate. */
  public static String signingCertificateSha256(PackageManager pm, String packageName) {
    Signature[] signatures = signaturesOf(pm, packageName, 0);
    if (signatures == null || signatures.length == 0) {
      return null;
    }
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
      byte[] encoded = signatures[0].toByteArray();
      // Try to parse the DER certificate so the digest is computed over the
      // certificate body rather than the opaque signature blob when possible.
      try {
        java.security.cert.CertificateFactory factory =
            java.security.cert.CertificateFactory.getInstance("X.509");
        X509Certificate certificate =
            (X509Certificate) factory.generateCertificate(new java.io.ByteArrayInputStream(encoded));
        return UpdateDigest.sha256Hex(certificate.getEncoded());
      } catch (java.security.cert.CertificateException notDer) {
        // Fall back to the raw signer bytes; still a stable device-side digest.
        return UpdateDigest.sha256Hex(encoded);
      }
    } catch (java.security.NoSuchAlgorithmException impossible) {
      return null;
    }
  }

  private static Signature[] signaturesOf(PackageManager pm, String packageName, int flags) {
    try {
      if (Build.VERSION.SDK_INT >= 28) {
        PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
        if (info.signingInfo == null) {
          return null;
        }
        Signature[] current = info.signingInfo.getApkContentsSigners();
        return current != null && current.length > 0 ? current : null;
      }
      PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
      return info.signatures != null && info.signatures.length > 0 ? info.signatures : null;
    } catch (PackageManager.NameNotFoundException missing) {
      return null;
    }
  }

  /** Installed version code, correct across API 26+ without deprecation casts. */
  public static long installedVersionCode(PackageManager pm, String packageName) {
    try {
      return PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(packageName, 0));
    } catch (PackageManager.NameNotFoundException missing) {
      return -1;
    }
  }

  /** Whether API 26+ allows this app to request installs of unknown packages. */
  public static boolean canRequestPackageInstalls(Context context) {
    return context.getPackageManager().canRequestPackageInstalls();
  }

  /** Settings screen where the user grants installs from this app. */
  public static Intent manageUnknownSourcesIntent(Context context) {
    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
    intent.setData(Uri.parse("package:" + context.getPackageName()));
    return intent;
  }

  /** ACTION_VIEW intent handing the staged APK to the system installer. */
  public static Intent installIntent(Context context, File apk) {
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apk);
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, "application/vnd.android.package-archive");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
  }

  private UpdatePackageInspector() {}
}