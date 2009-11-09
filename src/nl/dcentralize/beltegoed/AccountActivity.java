package nl.dcentralize.beltegoed;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class AccountActivity extends Activity {
	private static final String TAG = "BeltegoedActivity";
	public static final String PREFS_NAME = "BeltegoedPreferences";
	public static final String USERNAME = "USERNAME";
	public static final String PASSWORD = "PASSWORD";
	static final int ACCOUNT_REQUEST = 0;

	EditText username_input;
	EditText password_input;
	SharedPreferences settings;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		String phoneNumber = mTelephonyMgr.getLine1Number();
		Log.d(TAG, "Phone number: " + phoneNumber);

		settings = getSharedPreferences(PREFS_NAME, 0);
		String username = settings.getString(USERNAME, phoneNumber);
		String password = settings.getString(PASSWORD, "");

		String action = this.getIntent().getAction();
		if (action.equals(Intent.ACTION_VIEW)
				&& (username.length() > 0 && password.length() > 0)) {
			ReportSuccess(username, password);
		}

		setContentView(R.layout.account);

		username_input = (EditText) findViewById(R.id.username);
		password_input = (EditText) findViewById(R.id.password);
		username_input.getEditableText().append(username);
		password_input.getEditableText().append(password);

		final Button button = (Button) findViewById(R.id.store_account);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				StoreAccount();
			}
		});
	}

	private void StoreAccount() {
		SharedPreferences.Editor editor = settings.edit();

		String username = username_input.getEditableText().toString();
		String password = password_input.getEditableText().toString();

		editor.putString(USERNAME, username);
		editor.putString(PASSWORD, password);
		editor.commit();

		// XXX: validate first
		ReportSuccess(username, password);
	}

	private void ReportSuccess(String username, String password) {
		Bundle bundle = new Bundle();
		bundle.putString(USERNAME, username);
		bundle.putString(PASSWORD, password);
		Intent intent = new Intent();
		intent.putExtras(bundle);

		// We're done, close the activity
		setResult(RESULT_OK, intent);
		finish();
	}
}