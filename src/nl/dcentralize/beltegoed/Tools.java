package nl.dcentralize.beltegoed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.os.Environment;
import android.util.Log;

public class Tools {
	private static final String TAG = "Tools";

	public static void writeToSD(String filename, String data) {
		try {
			File root = Environment.getExternalStorageDirectory();
			if (root.canWrite()) {
				File file = new File(root, filename);
				FileWriter writer = new FileWriter(file, true);
				BufferedWriter out = new BufferedWriter(writer);
				out.write(data);
				out.close();
			}
		} catch (IOException e) {
			Log.e(TAG, "Could not write file " + e.getMessage());
		}
	}
	 
	public static String convertStreamToString(InputStream is) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is),
				8 * 1024);
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return sb.toString();
	}

}