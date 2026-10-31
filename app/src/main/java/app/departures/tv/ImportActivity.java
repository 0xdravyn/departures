package app.departures.tv;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Copies the configuration pushed over ADB into private storage, then finishes. Reads one fixed path only, takes no
 * extras, and never logs the file's contents (it holds calendar addresses).
 *
 *   adb push config.json /data/local/tmp/departures-config.json
 *   adb shell am start -n app.departures.tv/.ImportActivity
 */
public final class ImportActivity extends Activity {
    static final String SOURCE = "/data/local/tmp/departures-config.json";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        File src = new File(SOURCE), dst = new File(getFilesDir(), "config.json");
        try {
            if (!src.exists()) throw new java.io.FileNotFoundException(SOURCE);
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
            //noinspection ResultOfMethodCallIgnored
            src.delete();   // best effort: the pushed copy is shell-owned and may stay; remove it with adb afterwards
            Toast.makeText(this, "Departures: configuration imported", Toast.LENGTH_SHORT).show();
            Log.i("Departures", "configuration imported (" + dst.length() + " bytes)");
        } catch (Exception e) {
            Toast.makeText(this, "Departures: no configuration at " + SOURCE, Toast.LENGTH_LONG).show();
            Log.w("Departures", "import failed: " + e.getClass().getSimpleName());
        }
        finish();
    }
}
