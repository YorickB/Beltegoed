package nl.dcentralize.beltegoed;

import java.util.Date;

import nl.dcentralize.beltegoed.ParseResults.PARSE_RESULT;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nullwire.trace.ExceptionHandler;

public class BeltegoedActivity extends Activity {
	private static final String TAG = "BeltegoedActivity";
	public String username = null;
	public String password = null;
	public String provider = null;
	static final int ACCOUNT_REQUEST = 100;

	private LocalParseTask lpt = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		ExceptionHandler.register(this,
				"http://d-centralize.nl/android_talkback.php");

		setContentView(R.layout.beltegoed);

		showAccountSettings();
	}

	private void showAccountSettings() {
		// Get account information, or ask for it.
		Intent intent = new Intent(this, AccountActivity.class);
		intent.setAction(Intent.ACTION_VIEW);
		startActivityForResult(intent, ACCOUNT_REQUEST);
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case ACCOUNT_REQUEST:
			if (resultCode == RESULT_OK) {
				Bundle bundle = data.getExtras();
				if (bundle != null) {
					username = bundle.getString(AccountActivity.USERNAME);
					password = bundle.getString(AccountActivity.PASSWORD);
					provider = bundle.getString(AccountActivity.PROVIDER);
					GetAccountInformation();
				}
			}
			break;
		}
	}

	private void showAccountDetails(ParseResults parseResults) {
		String providerName = parseResults.provider;
		String accountName = parseResults.accountType;
		String startAmountRaw = parseResults.startAmountRaw;
		String currentAmountRaw = parseResults.currentAmountRaw;
		String extraAmountRaw = parseResults.extraAmountRaw;
		String startDateRaw = parseResults.startDateRaw;
		String endDateRaw = parseResults.endDateRaw;

		int startAmountRounded = Integer.parseInt(startAmountRaw.split(",")[0]);
		int currentAmountRounded = Integer
				.parseInt(currentAmountRaw.split(",")[0]);

		ProgressBar amountBar = (ProgressBar) findViewById(R.id.amount);
		amountBar.setMax(startAmountRounded);
		amountBar.setProgress(currentAmountRounded);

		startAmountRaw = startAmountRaw.replace(',', '.');
		currentAmountRaw = currentAmountRaw.replace(',', '.');
		extraAmountRaw = extraAmountRaw.replace(',', '.');

		TextView account_type = (TextView) findViewById(R.id.account_type);
		account_type.setText(providerName + " " + accountName);

		TextView amount_text = (TextView) findViewById(R.id.amount_text);
		amount_text.setText("€ " + currentAmountRaw + " van oorspronkelijke € "
				+ startAmountRaw + " over.");

		TextView extra_text = (TextView) findViewById(R.id.extra_text);
		extra_text.setText("€ " + extraAmountRaw + " extra buiten bundel.");

		TextView bundle_dates = (TextView) findViewById(R.id.bundle_dates);
		bundle_dates.setText("Periode van " + startDateRaw + " tot "
				+ endDateRaw);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = this.getMenuInflater();
		inflater.inflate(R.menu.main, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_preferences:
			Intent intent = new Intent(this, AccountActivity.class);
			intent.setAction(Intent.ACTION_EDIT);
			startActivityForResult(intent, ACCOUNT_REQUEST);
			return true;
		}
		return false;
	}

	private void GetAccountInformation() {
		// Cancel a running async task on resume. It may otherwise stall
		// forever.
		if (lpt != null) {
			lpt.cancel(true);
			lpt = null;
		}

		lpt = new LocalParseTask();
		lpt.execute();
	}

	// This function does GUI tasks from a non-GUI thread.
	public Handler InvalidLoginHandler = new Handler() {
		public void handleMessage(Message msg) {
			alertInvalidLogin();
		}
	};

	private void alertInvalidLogin() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.login_error).setCancelable(false)
				.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
						showAccountSettings();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	// This function does GUI tasks from a non-GUI thread.
	public Handler UnknownErrorHandler = new Handler() {
		public void handleMessage(Message msg) {
			alertUnknownError();
		}
	};

	private void alertUnknownError() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.error_parsing).setCancelable(false)
				.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	private class LocalParseTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			showDialog(R.id.dialog_load);
		}

		@Override
		protected Void doInBackground(Void... params) {
			final ParseResults parseResult;
			if (provider.equals(AccountActivity.PROVIDER_VODAFONE)) {
				parseResult = providerVodafone
						.ParseVodafone(username, password);
			} else if (provider.equals(AccountActivity.PROVIDER_KPN)) {
				parseResult = providerKPN.ParseKPN(username, password);
			} else {
				parseResult = null;
			}

			SharedPreferences settings = getSharedPreferences(
					AccountActivity.PREFS_NAME, 0);
			SharedPreferences.Editor editor = settings.edit();
			
			if (parseResult != null) {
				if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN) {
					// Make sure the login is invalidated.
					editor.putLong(AccountActivity.LAST_LOGIN, -1);
					editor.commit();
					InvalidLoginHandler.sendMessage(new Message());
				} else if (parseResult.parseResult != PARSE_RESULT.OK) {
					Tools.writeToSD("Beltegoed-error-log.txt", parseResult
							.getErrorMessage()
							+ parseResult.getLogMessage());
					UnknownErrorHandler.sendMessage(new Message());
				} else {
					// Save the login timestamp so we know the login succeeded.
					long currentts = (new Date()).getTime();
					
					editor.putLong(AccountActivity.LAST_LOGIN, currentts);
					editor.commit();

					BeltegoedActivity.this.runOnUiThread(new Runnable() {
						public void run() {
							showAccountDetails(parseResult);
						}
					});
				}
			}

			return null;
		}

		/** {@inheritDoc} */
		@Override
		protected void onPostExecute(Void result) {
			dismissDialog(R.id.dialog_load);
		}
	}

	/** {@inheritDoc} */
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case R.id.dialog_load:
			return buildLoadingDialog();
		default:
			return null;
		}
	}

	/**
	 * Build dialog to show when loading data.
	 */
	private Dialog buildLoadingDialog() {
		ProgressDialog dialog = new ProgressDialog(this);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setMessage(getText(R.string.dialog_loading_account));
		dialog.setIndeterminate(true); // Show cyclic animation
		dialog.setCancelable(true); // Allow pressing BACK to cancel
		dialog.setOnCancelListener(new ProgressDialog.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				// XXX: Cleanup
			}
		});
		return dialog;
	}
}