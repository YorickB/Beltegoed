package nl.dcentralize.beltegoed;

import java.text.SimpleDateFormat;

import nl.dcentralize.beltegoed.AccountDetails.AMOUNT_UNIT;
import nl.dcentralize.beltegoed.AccountDetails.PARSE_RESULT;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BeltegoedActivity extends Activity {
	private Account account;

	static final int ACCOUNT_REQUEST = 100;
	static final int ACCOUNT_RESET = 101;

	private BeltegoedService appService = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Make sure the exceptions are always logged to SD
		Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());

		setContentView(R.layout.beltegoed);

		bindService(new Intent(this, BeltegoedService.class), onService, Context.BIND_AUTO_CREATE);

		showAccountSettings();
	}

	@Override
	public void onResume() {
		super.onResume();

		registerReceiver(receiver, new IntentFilter(BeltegoedService.BROADCAST_ACTION));
		// Update to the latest information gathered by service.
		getBeltegoed();
	}

	@Override
	public void onPause() {
		super.onPause();

		unregisterReceiver(receiver);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		unbindService(onService);
	}

	private void getBeltegoed() {
		// If service has been started, and account has ben set, request
		// beltegoed.
		if (appService != null && account != null) {
			// Show old account details right away (if available)
			AccountDetails accountDetails = appService.getCachedAccountDetails(account);
			if (accountDetails != null) {
				updateAccountDetails(accountDetails);
			}
			refreshBeltegoed();
		}
	}

	private void refreshBeltegoed() {
		// Get fresh details right away
		showDialog(R.id.dialog_load);
		appService.refreshAccountDetails(account);
	}

	// This function does GUI tasks from a non-GUI thread.
	public Handler DetailsAvailableHandler = new Handler() {

		public void handleMessage(Message msg) {
			// Signal received that there are new account details, read them.
			AccountDetails parseResult = appService.getCachedAccountDetails(account);
			updateAccountDetails(parseResult);
		}
	};

	private void updateAccountDetails(AccountDetails parseResult) {
		if (parseResult == null) {
			alertMessage(R.string.error_parsing, false);
		} else if (parseResult.parseResult == PARSE_RESULT.OK || parseResult.parseResult == PARSE_RESULT.CACHED) {
			showAccountDetails(parseResult);
		} else {
			if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN) {
				alertMessage(R.string.login_error, true);
			} else if (parseResult.parseResult == PARSE_RESULT.NO_INTERNET) {
				alertMessage(R.string.no_internet, true);
			} else if (parseResult.parseResult == PARSE_RESULT.CONN_FAIL) {
				alertMessage(R.string.conn_fail, true);
			} else if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN_TEMP_BLOCK) {
				alertMessage(R.string.temp_block, true);
			} else {
				alertMessage(R.string.error_parsing, false);
			}
			// No more automated tries before reviewing configuration.
			appService.deleteCachedAccountDetails();
		}
	}

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			dismissDialog(R.id.dialog_load);
			DetailsAvailableHandler.sendMessage(new Message());
		}
	};

	private ServiceConnection onService = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder rawBinder) {
			appService = ((BeltegoedService.LocalBinder) rawBinder).getService();

			getBeltegoed();
		}

		public void onServiceDisconnected(ComponentName className) {
			appService = null;
		}
	};

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
					account = new Account();
					account.setProvider(bundle.getString(AccountActivity.PROVIDER));
					account.setUsername(bundle.getString(AccountActivity.USERNAME));
					account.setPassword(bundle.getString(AccountActivity.PASSWORD));

					getBeltegoed();
				}
			}
			break;
		}
	}

	private void showAccountDetails(AccountDetails parseResults) {
		String providerName = parseResults.provider;
		String accountName = parseResults.accountType;
		AMOUNT_UNIT amountUnit = parseResults.amountUnit;

		ProgressBar amountBar = (ProgressBar) findViewById(R.id.amount);
		amountBar.setMax(parseResults.startAmount);
		amountBar.setProgress(parseResults.amountLeft);

		String extraAmountRaw = Tools.CentsToEuroString(parseResults.extraAmount);

		TextView account_type = (TextView) findViewById(R.id.account_type);
		account_type.setText(providerName + " " + accountName);

		TextView amount_text = (TextView) findViewById(R.id.amount_text);
		if (amountUnit == AMOUNT_UNIT.EURO) {
			String startAmountRaw = Tools.CentsToEuroString(parseResults.startAmount);
			String currentAmountRaw = Tools.CentsToEuroString(parseResults.amountLeft);
			amount_text.setText("€ " + currentAmountRaw + " van oorspronkelijke € " + startAmountRaw + " over.");
		} else {
			amount_text.setText(parseResults.amountLeft + "  van oorspronkelijke " + parseResults.startAmount
					+ " min over.");
		}

		TextView extra_text = (TextView) findViewById(R.id.extra_text);
		extra_text.setText("€ " + extraAmountRaw + " extra buiten bundel.");

		TextView bundle_dates = (TextView) findViewById(R.id.bundle_dates);

		bundle_dates.setText("Periode van " + Tools.DateToString(parseResults.startDate) + " tot "
				+ Tools.DateToString(parseResults.endDate));

		// Not all providers have this data (KPN)
		if (parseResults.lastProviderUpdate != null) {
			SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm");
			String lastUpdate = formatter.format(parseResults.lastProviderUpdate);
			TextView last_update = (TextView) findViewById(R.id.last_update);
			last_update.setText("Laatste bijgewerkt door provider: " + lastUpdate);
		}
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
		case R.id.menu_refresh:
			refreshBeltegoed();
			return true;
		}
		return false;
	}

	private void alertMessage(int text, final Boolean showSettings) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(text).setCancelable(false).setNeutralButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.cancel();
				if (showSettings) {
					showAccountSettings();
				}
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
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
