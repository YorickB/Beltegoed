package nl.dcentralize.beltegoed;

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
	private ParseResults forecast = null;
	private Intent broadcast = new Intent(BROADCAST_ACTION);
	private final Binder binder = new LocalBinder();
	private Account account;

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

	public void setAccount(Account acc) {
		account = acc;
	}

	synchronized public ParseResults getBeltegoed() {
		return (forecast);
	}

	public void fetchBeltegoed(Account account) {
		if (forecast != null) {
			// We already have credit details. Return these, update later.
			sendBroadcast(broadcast);
		}
		new FetchForecastTask().execute(account);
	}

	public class LocalBinder extends Binder {
		BeltegoedService getService() {
			return (BeltegoedService.this);
		}
	}

	class FetchForecastTask extends AsyncTask<Account, Void, Void> {

		@Override
		protected Void doInBackground(Account... accounts) {
			Account account = accounts[0];
			final ParseResults parseResult;
			if (account.getProvider().equals(AccountActivity.PROVIDER_VODAFONE)) {
				parseResult = providerVodafone.ParseVodafone(account
						.getUsername(), account.getPassword());
			} else if (account.getProvider().equals(
					AccountActivity.PROVIDER_KPN)) {
				parseResult = providerKPN.ParseKPN(account.getUsername(),
						account.getPassword());
			} else {
				parseResult = null;
			}

			SharedPreferences settings = getSharedPreferences(
					AccountActivity.PREFS_NAME, 0);
			SharedPreferences.Editor editor = settings.edit();

			if (parseResult != null) {
				if (parseResult.parseResult == PARSE_RESULT.OK) {
					// Save the login timestamp so we know the login succeeded.
					long currentts = (new Date()).getTime();

					editor.putLong(AccountActivity.LAST_LOGIN, currentts);
					editor.commit();

					synchronized (this) {
						forecast = parseResult;
					}

					sendBroadcast(broadcast);
				} else {
					// We've got a problem, log as much as we can.
					String logDump = "Beltegoed app debug log. ";

					String versionName = "";
					try {
						PackageInfo pi = getPackageManager().getPackageInfo(
								getPackageName(), 0);
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
					logDump += "\nError message: "
							+ parseResult.getErrorMessage();
					logDump += "\nLog message: " + parseResult.getLogMessage();

					Tools.writeToSD("Beltegoed-error-log.txt", logDump);

					// Make sure the login is invalidated.
					editor.putLong(AccountActivity.LAST_LOGIN, -1);
					editor.commit();

					if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN) {
						// InvalidLoginHandler.sendMessage(new Message());
					} else {
						// UnknownErrorHandler.sendMessage(new Message());
					} 

					synchronized (this) {
						forecast = null;
					}

					sendBroadcast(broadcast);
				}
			}

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