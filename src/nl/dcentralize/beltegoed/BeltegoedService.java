package nl.dcentralize.beltegoed;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import nl.dcentralize.beltegoed.ParseResults.PARSE_RESULT;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;

public class BeltegoedService extends Service {
	private static final String TAG = "BeltegoedService";
	public static final int NOTIF_ID = 1337;
	public static final String SHUTDOWN = "SHUTDOWN";
	public static final String BROADCAST_ACTION = "nl.dcentralize.beltegoed.ForecastUpdateEvent";
	private ParseResults creditDetails = null;
	private Intent broadcast = new Intent(BROADCAST_ACTION);
	private final Binder binder = new LocalBinder();
	// Retry the login process at most BURST_TRYCOUNT times.
	private static final int BURST_TRYCOUNT = 3;
	private int tryCount;
	// XXX: Define this as provider specific setting.
	private static final int REFRESH_ON_SUCCESS = 60 * 60 * 24; // Daily
	private static final int REFRESH_ON_FAILURE = 60 * 60; // Hourly

	@Override
	public void onCreate() {
		super.onCreate();

		creditDetails = getAccountDetails();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return (binder);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	synchronized public ParseResults getBeltegoed() {
		return (creditDetails);
	}

	public void fetchBeltegoed(Account account, Boolean invalidateCache) {
		if (invalidateCache) {
			creditDetails = null;
		}
		if (creditDetails != null) {
			// We already have credit details. Return these, update later.
			sendBroadcast(broadcast);
		}
		new FetchCreditDetailsTask().execute(account);
	}

	public class LocalBinder extends Binder {
		BeltegoedService getService() {
			return (BeltegoedService.this);
		}
	}

	private ParseResults getAccountDetails() {

		SharedPreferences settings = getSharedPreferences(AccountActivity.PREFS_NAME, 0);

		ParseResults parseResult = new ParseResults();
		if (settings.getLong(AccountActivity.LAST_LOGIN, -1) > 0) {
			parseResult.provider = settings.getString(AccountActivity.PROVIDER, null);
			parseResult.parseResult = PARSE_RESULT.CACHED;
			parseResult.accountType = settings.getString(AccountActivity.ACCOUNT_TYPE, null);
			parseResult.startAmountRaw = settings.getString(AccountActivity.START_AMOUNT, null);
			parseResult.amountLeftRaw = settings.getString(AccountActivity.AMOUNT_LEFT, null);
			parseResult.extraAmountRaw = settings.getString(AccountActivity.EXTRA_AMOUNT, null);
			parseResult.startDateRaw = settings.getString(AccountActivity.START_DATE, null);
			parseResult.endDateRaw = settings.getString(AccountActivity.END_DATE, null);
			SimpleDateFormat df1 = new SimpleDateFormat("dd-MM-yyyy HH:mm");
			try {
				parseResult.lastUpdate = df1.parse(settings.getString(AccountActivity.LAST_UPDATE, null));
			} catch (ParseException e) {
			}
			return parseResult;
		} else {
			return null;
		}

	}

	private void updateAccountDetails(ParseResults parseResult, Boolean invalidate) {
		SharedPreferences settings = getSharedPreferences(AccountActivity.PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();

		if (invalidate) {
			// Make sure the login is invalidated.
			editor.putLong(AccountActivity.LAST_LOGIN, -1);
		} else {
			// Save the login timestamp so we know the login succeeded.
			long currentts = (new Date()).getTime();
			editor.putLong(AccountActivity.LAST_LOGIN, currentts);
		}
		editor.putString(AccountActivity.PROVIDER, parseResult.provider);
		editor.putString(AccountActivity.ACCOUNT_TYPE, parseResult.accountType);
		editor.putString(AccountActivity.START_AMOUNT, parseResult.startAmountRaw);
		editor.putString(AccountActivity.AMOUNT_LEFT, parseResult.amountLeftRaw);
		editor.putString(AccountActivity.EXTRA_AMOUNT, parseResult.extraAmountRaw);
		editor.putString(AccountActivity.START_DATE, parseResult.startDateRaw);
		editor.putString(AccountActivity.END_DATE, parseResult.endDateRaw);
		if (parseResult.lastUpdate != null) {
			SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm");
			editor.putString(AccountActivity.LAST_UPDATE, formatter.format(parseResult.lastUpdate));
		}
		editor.commit();
	}

	private ParseResults fetchCreditDetails(Account account) {
		ParseResults parseResult;
		if (account.getProvider().equals(AccountActivity.PROVIDER_VODAFONE)) {
			parseResult = providerVodafone.ParseVodafone(account.getUsername(), account.getPassword());
		} else if (account.getProvider().equals(AccountActivity.PROVIDER_KPN)) {
			parseResult = providerKPN.ParseKPN(account.getUsername(), account.getPassword());
		} else if (account.getProvider().equals(AccountActivity.PROVIDER_TMOBILE)) {
			parseResult = providerTMobile.ParseTMobile(account.getUsername(), account.getPassword());
		} else {
			parseResult = null;
		}
		return parseResult;
	}

	class FetchCreditDetailsTask extends AsyncTask<Account, Void, Void> {
		@Override
		protected Void doInBackground(Account... accounts) {
			Account account = accounts[0];

			tryCount = 0;
			// Try at least BURST_TRYCOUNT before giving up this cycle
			while (tryCount < BURST_TRYCOUNT) {
				tryCount += 1;
				ParseResults parseResult = fetchCreditDetails(account);

				if (parseResult != null) {
					if (parseResult.parseResult == PARSE_RESULT.OK) {
						updateAccountDetails(parseResult, false);

						synchronized (this) {
							creditDetails = parseResult;
						}

						sendBroadcast(broadcast);
						return (null);
					} else {
						// We've got a problem, log as much as we can.
						String logDump = "Beltegoed app debug log. ";

						String versionName = "";
						try {
							PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
							versionName = pi.versionName;
						} catch (PackageManager.NameNotFoundException e) {
						}
						logDump += versionName;
						if (parseResult.provider != null) {
							logDump += "\nProvider: " + parseResult.provider;
						}
						if (parseResult.accountType != null) {
							logDump += "\nAccount type: " + parseResult.accountType;
						}
						logDump += "\nError message: " + parseResult.getErrorMessage();
						logDump += "\nLog message: " + parseResult.getLogMessage();

						Tools.writeToSD("Beltegoed-error-log.txt", logDump);

						updateAccountDetails(parseResult, true);
  
						synchronized (this) {
							creditDetails = null;
						}
					}
				}
			}

			sendBroadcast(broadcast);
			return (null);
		}

		@Override
		protected void onProgressUpdate(Void... unused) {
			// not needed here
		}

		@Override
		protected void onPostExecute(Void unused) {
			// not needed here
		}
	}
}