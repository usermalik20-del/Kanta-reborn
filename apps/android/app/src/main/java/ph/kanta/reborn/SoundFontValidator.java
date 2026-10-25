package ph.kanta.reborn;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class SoundFontValidator {
    private static final String TAG = "SoundFontValidator";
    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L * 1024L; // 5 GB limit to support large professional SoundFonts (e.g., 4GB+)

    public static boolean validateSoundFontFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            Log.e(TAG, "SoundFont file is invalid or does not exist.");
            return false;
        }

        if (file.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "SoundFont file exceeds maximum allowed size: " + file.length());
            return false;
        }

        // Check RIFF / SFBK header
        byte[] buffer = new byte[12];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(buffer);
            if (read < 12) {
                Log.e(TAG, "File too small to be a valid SoundFont.");
                return false;
            }

            String riff = new String(buffer, 0, 4);
            String sfbk = new String(buffer, 8, 4);

            if (!"RIFF".equals(riff) || !"sfbk".equals(sfbk)) {
                Log.e(TAG, "Invalid SoundFont header. RIFF/sfbk expected.");
                return false;
            }

        } catch (IOException e) {
            Log.e(TAG, "Failed to read SoundFont file for validation", e);
            return false;
        }

        Log.i(TAG, "SoundFont file validation passed successfully.");
        return true;
    }
}
