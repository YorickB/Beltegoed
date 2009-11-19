package nl.dcentralize.beltegoed;

import java.text.SimpleDateFormat;

import nl.dcentralize.beltegoed.ParseResults.PARSE_RESULT;

import android.app.Activity;
import android.app.AlertDialog;
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
	private static final String TAG = "BeltegoedActivity";
	private Account account;

	static final int ACCOUNT_REQUEST = 100;

	private BeltegoedService appService = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.beltegoed);

		showAccountSettings();

		bindService(new Intent(this, BeltegoedService.class), onService,
				Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onResume() {
		super.onResume();

		registerReceiver(receiver, new IntentFilter(
				BeltegoedService.BROADCAST_ACTION));
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

	// This function does GUI tasks from a non-GUI thread.
	public Handler DetailsAvailableHandler = new Handler() {

		public void handleMessage(Message msg) {
			ParseResults parseResult = appService.getBeltegoed();

			if (parseResult.parseResult == PARSE_RESULT.OK) {
				showAccountDetails(parseResult);
			} else {
				if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN) {
					alertInvalidLogin();
				} else {
					alertUnknownError();
				}
			}
		}
	};

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			DetailsAvailableHandler.sendMessage(new Message());
		}
	};

	private ServiceConnection onService = new ServiceConnection() {
		public void onServiceConnected(ComponentName className,
				IBinder rawBinder) {
			appService = ((BeltegoedService.LocalBinder) rawBinder)
					.getService();

			appService.setAccount(account);
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
					account.setProvider(bundle
							.getString(AccountActivity.PROVIDER));
					account.setUsername(bundle
							.getString(AccountActivity.USERNAME));
					account.setPassword(bundle
							.getString(AccountActivity.PASSWORD));

					appService.fetchBeltegoed(account);
				}
			}
			break;
		}
	}

	private void showAccountDetails(ParseResults parseResults) {
		String providerName = parseResults.provider;
		String accountName = parseResults.accountType;
		String startAmountRaw = parseResults.startAmountRaw;
		String currentAmountRaw = parseResults.amountLeftRaw;
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

		// Not all providers have this data (KPN)
		if (parseResults.lastUpdate != null) {
			SimpleDateFormat formatter = new SimpleDateFormat(
					"dd-MM-yyyy HH:mm");
			String lastUpdate = formatter.format(parseResults.lastUpdate);
			TextView last_update = (TextView) findViewById(R.id.last_update);
			last_update.setText("Laatste bijgewerkt door provider: "
					+ lastUpdate);
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
		}
		return false;
	}
 
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

}