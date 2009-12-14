package nl.dcentralize.beltegoed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
		BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8 * 1024);
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

	public static String DateToString(Date date) {
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm");
			String dateString = formatter.format(date);
			// If the date has no time specified (assume no time equals 00:00),
			// strip it.
			return dateString.split("00:00")[0];
		} catch (Exception e) {
			return null;
		}
	}

	public static Date StringToDate(String date) {
		SimpleDateFormat df1 = new SimpleDateFormat("dd-MM-yyyy HH:mm");
		try {
			return df1.parse(date);
		} catch (Exception e) {
		}
		df1 = new SimpleDateFormat("dd-MM-yyyy");
		try {
			return df1.parse(date);
		} catch (Exception e) {
		}
		return null;
	}

	public static String CentsToEuroString(int cents) {
		NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
		DecimalFormat df = (DecimalFormat) nf;
		df.applyPattern("##0.00");
		String euroString = df.format((double) cents / 100.0);
		// XXX: Can't get the , and . right. Not even after forcing a locale
		// like GERMANY
		euroString = euroString.replace('.', ',');
		return euroString;
	}
}
