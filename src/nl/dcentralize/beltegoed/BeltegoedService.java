package nl.dcentralize.beltegoed;

import java.util.Date;

import nl.dcentralize.beltegoed.AccountDetails.AMOUNT_UNIT;
import nl.dcentralize.beltegoed.AccountDetails.PARSE_RESULT;
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
	public static final String BROADCAST_ACTION = "nl.dcentralize.beltegoed.AccountDetailsUpdateEvent";
	private Account currentlyFetchedAccount = null;
	private AccountDetails cachedAccountDetails = null;
	private Intent broadcast = new Intent(BROADCAST_ACTION);
	private final Binder binder = new LocalBinder();
	// Retry the login process at most BURST_TRYCOUNT times.
	private static final int BURST_TRYCOUNT = 3;
	private int tryCount;
	private FetchCreditDetailsTask task;

	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return (binder);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	synchronized public AccountDetails getCachedAccountDetails(Account account) {
		if (cachedAccountDetails == null) {
			cachedAccountDetails = getLastKnownAccountDetails(account);
		}

		return cachedAccountDetails;
	}

	public void deleteCachedAccountDetails() {
		cachedAccountDetails = null;
	}

	public void refreshAccountDetails(Account account) {
		if (task != null) {
			// A task is running, check if this is a new account.
			if (account.equals(currentlyFetchedAccount)) {
				return;
			}

			// Cancel the running task.
			task.cancel(true);
			task = null;
		}

		currentlyFetchedAccount = account;
		task = new FetchCreditDetailsTask();
		task.execute(account);
	}

	public class LocalBinder extends Binder {
		BeltegoedService getService() {
			return (BeltegoedService.this);
		}
	}

	private AccountDetails getLastKnownAccountDetails(Account account) {
		try {
			SharedPreferences settings = getSharedPreferences(AccountActivity.PREFS_NAME, 0);

			AccountDetails accountDetails = new AccountDetails();
			if (settings.getLong(AccountActivity.LAST_LOGIN, -1) > 0) {
				accountDetails.parseResult = PARSE_RESULT.CACHED;
				accountDetails.provider = settings.getString(AccountActivity.CACHED_PROVIDER, null);
				accountDetails.username = settings.getString(AccountActivity.CACHED_USERNAME, null);
				accountDetails.password = settings.getString(AccountActivity.CACHED_PASSWORD, null);

				if (!account.getProvider().equals(accountDetails.provider)
						|| !account.getUsername().equals(accountDetails.username)
						|| !account.getPassword().equals(accountDetails.password)) {
					// This is not the right account
					return null;
				}

				accountDetails.accountType = settings.getString(AccountActivity.ACCOUNT_TYPE, null);
				accountDetails.amountUnit = AMOUNT_UNIT.valueOf(settings.getString(AccountActivity.AMOUNT_UNIT,
						AMOUNT_UNIT.EURO.name()));
				accountDetails.startAmount = settings.getInt(AccountActivity.START_AMOUNT, 0);
				accountDetails.amountLeft = settings.getInt(AccountActivity.AMOUNT_LEFT, 0);
				accountDetails.extraAmount = settings.getInt(AccountActivity.EXTRA_AMOUNT, 0);
				accountDetails.startDate = Tools.StringToDate(settings.getString(AccountActivity.START_DATE, null));
				accountDetails.endDate = Tools.StringToDate(settings.getString(AccountActivity.END_DATE, null));
				accountDetails.lastProviderUpdate = Tools.StringToDate(settings.getString(
						AccountActivity.LAST_PROVIDER_UPDATE, null));
				return accountDetails;
			} else {
				return null;
			}
		} catch (Exception e) {
			// May fail if settings from a previous version (different
			// preference keys) are read.
			return null;
		}
	}

	private void updateAccountDetails(AccountDetails parseResult, Boolean invalidate) {
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
		editor.putString(AccountActivity.CACHED_PROVIDER, parseResult.provider);
		editor.putString(AccountActivity.CACHED_USERNAME, parseResult.username);
		editor.putString(AccountActivity.CACHED_PASSWORD, parseResult.password);
		editor.putString(AccountActivity.ACCOUNT_TYPE, parseResult.accountType);
		editor.putString(AccountActivity.AMOUNT_UNIT, parseResult.amountUnit.name());
		editor.putInt(AccountActivity.START_AMOUNT, parseResult.startAmount);
		editor.putInt(AccountActivity.AMOUNT_LEFT, parseResult.amountLeft);
		editor.putInt(AccountActivity.EXTRA_AMOUNT, parseResult.extraAmount);
		editor.putString(AccountActivity.START_DATE, Tools.DateToString(parseResult.startDate));
		editor.putString(AccountActivity.END_DATE, Tools.DateToString(parseResult.endDate));
		editor.putString(AccountActivity.LAST_PROVIDER_UPDATE, Tools.DateToString(parseResult.lastProviderUpdate));
		editor.commit();
	}

	public static AccountDetails fetchCreditDetails(Account account) {
		AccountDetails parseResult;
		if (account.getProvider().equals(AccountActivity.PROVIDER_VODAFONE)) {
			parseResult = providerVodafone.ParseVodafone(account.getUsername(), account.getPassword());
		} else if (account.getProvider().equals(AccountActivity.PROVIDER_KPN)) {
			parseResult = providerKPN.ParseKPN(account.getUsername(), account.getPassword());
		} else if (account.getProvider().equals(AccountActivity.PROVIDER_TMOBILE)) {
			parseResult = providerTMobile.ParseTMobile(account.getUsername(), account.getPassword());
		} else {
			parseResult = null;
		}
		parseResult.username = account.getUsername();
		parseResult.password = account.getPassword();

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
				AccountDetails parseResult = fetchCreditDetails(account);

				if (parseResult != null) {

					// Always update the new account details, either with actual
					// account information, or with the error.
					synchronized (this) {
						cachedAccountDetails = parseResult;
					}

					if (parseResult.parseResult == PARSE_RESULT.OK) {
						updateAccountDetails(parseResult, false);

						// We're done. Stop trying now.
						tryCount = BURST_TRYCOUNT + 1;
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

						if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN
								|| parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN_TEMP_BLOCK) {
							// Only invalidate when the login was incorrect.
							// In case of other failures, just wait until times
							// get better.
							updateAccountDetails(parseResult, true);
							// An incorrect login will fail forever, stop trying
							// now.
							tryCount = BURST_TRYCOUNT + 1;
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