package nl.dcentralize.beltegoed;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

public class AccountActivity extends Activity {
	public static final String PREFS_NAME = "BeltegoedPreferences";
	public static final String PROVIDER = "PROVIDER";
	public static final String USERNAME = "USERNAME";
	public static final String PASSWORD = "PASSWORD";

	public static final String LAST_LOGIN = "LAST_LOGIN";
	public static final String CACHED_PROVIDER = "CACHED_PROVIDER";
	public static final String CACHED_USERNAME = "CACHED_USERNAME";
	public static final String CACHED_PASSWORD = "CACHED_PASSWORD";
	public static final String ACCOUNT_TYPE = "ACCOUNT_TYPE";
	public static final String START_AMOUNT = "START_AMOUNT";
	public static final String AMOUNT_LEFT = "AMOUNT_LEFT";
	public static final String EXTRA_AMOUNT = "EXTRA_AMOUNT";
	public static final String START_DATE = "START_DATE";
	public static final String END_DATE = "END_DATE";
	public static final String LAST_PROVIDER_UPDATE = "LAST_UPDATE";
	public static final String AMOUNT_UNIT = "AMOUNT_UNIT";

	public static final String PROVIDER_VODAFONE = "Vodafone";
	public static final String PROVIDER_KPN = "KPN";
	public static final String PROVIDER_TMOBILE = "T-Mobile";
	public static final ArrayList<String> PROVIDER_LIST = new ArrayList<String>(Arrays.asList(PROVIDER_VODAFONE,
			PROVIDER_KPN, PROVIDER_TMOBILE));

	static final int ACCOUNT_REQUEST = 0;

	EditText username_input;
	EditText password_input;
	Spinner provider_input;

	SharedPreferences settings;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Generate an exception to test the talkback process
		// Integer.parseInt("50,00");

		String phoneNumber = "";
		/*
		 * XXX: Disabled due to crashes reported on HERO try { TelephonyManager
		 * mTelephonyMgr = (TelephonyManager)
		 * getSystemService(Context.TELEPHONY_SERVICE); phoneNumber =
		 * mTelephonyMgr.getLine1Number(); Log.d(TAG, "Phone number: " +
		 * phoneNumber); } catch (Exception e) { Log.e(TAG,
		 * "Unable to get phone number"); phoneNumber = ""; }
		 */

		settings = getSharedPreferences(PREFS_NAME, 0);
		String username = settings.getString(USERNAME, phoneNumber);
		String password = settings.getString(PASSWORD, "");
		String provider = settings.getString(PROVIDER, "");
		Long lastlogin = settings.getLong(LAST_LOGIN, -1);

		String action = this.getIntent().getAction();
		if (action.equals(Intent.ACTION_VIEW) && lastlogin > 0) {
			ReportSuccess(provider, username, password);
		} else if (action.equals(Intent.ACTION_DELETE)) {
			resetAccount();
			ReportSuccess();
		}

		setContentView(R.layout.account);

		// Fill provider options
		provider_input = (Spinner) findViewById(R.id.provider);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				PROVIDER_LIST);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		provider_input.setAdapter(adapter);

		// Restore old settings
		provider_input.setSelection(PROVIDER_LIST.indexOf(provider));
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

	public void resetAccount() {
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.clear();
		editor.commit();
	}

	private void StoreAccount() {
		SharedPreferences.Editor editor = settings.edit();

		String username = username_input.getEditableText().toString();
		String password = password_input.getEditableText().toString();
		String provider = provider_input.getSelectedItem().toString();

		String verificationResult = null;
		if (provider.equals(AccountActivity.PROVIDER_VODAFONE)) {
			verificationResult = providerVodafone.verifyAccount(username, password);
		} else if (provider.equals(AccountActivity.PROVIDER_KPN)) {
			verificationResult = providerKPN.verifyAccount(username, password);
		} else if (provider.equals(AccountActivity.PROVIDER_TMOBILE)) {
			verificationResult = providerTMobile.verifyAccount(username, password);
		}

		if (verificationResult == null) {
			// No errors found. Use these account settings.

			editor.putString(USERNAME, username);
			editor.putString(PASSWORD, password);
			editor.putString(PROVIDER, provider);
			// Reset last valid login as this may be a new account setting to
			// try
			editor.putLong(LAST_LOGIN, -1);
			editor.commit();

			ReportSuccess(provider, username, password);
		} else {
			// Input validation error. Show error description.
			new AlertDialog.Builder(this).setMessage(verificationResult).setCancelable(false).setNeutralButton("Ok",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					}).create().show();
		}
	}

	private void ReportSuccess(String provider, String username, String password) {
		Bundle bundle = new Bundle();
		bundle.putString(PROVIDER, provider);
		bundle.putString(USERNAME, username);
		bundle.putString(PASSWORD, password);
		Intent intent = new Intent();
		intent.putExtras(bundle);

		// We're done, close the activity
		setResult(RESULT_OK, intent);
		finish();
	}

	private void ReportSuccess() {
		// We're done, close the activity
		setResult(RESULT_OK, new Intent());
		finish();
	}
}