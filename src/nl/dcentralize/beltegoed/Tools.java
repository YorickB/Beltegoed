package nl.dcentralize.beltegoed;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.os.Environment;
import android.util.Log;

public class Tools {
	private static final String TAG = "Tools";

	public static void writeToSD(String filename, String data) {
		try {
			File root = Environment.getExternalStorageDirectory();
			if (root.canWrite()) {
				File file = new File(root, filename);
				FileWriter gpxwriter = new FileWriter(file);
				BufferedWriter out = new BufferedWriter(gpxwriter);
				out.write(data);
				out.close();
			}
		} catch (IOException e) {
			Log.e(TAG, "Could not write file " + e.getMessage());
		}
	}
}