package nl.dcentralize.beltegoed;

import java.util.Date;

import nl.dcentralize.beltegoed.AccountDetails.AMOUNT_UNIT;
import nl.dcentralize.beltegoed.AccountDetails.PARSE_RESULT;
import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

// More info from: The Busy Coder's Guide to Advanced Android Development (1.2)
// Show up at home -> Crafting App Widgets (pg 49)
public class BeltegoedWidget extends AppWidgetProvider {
	public Boolean updatedOnce = false;

	@Override
	public void onReceive(Context ctxt, Intent intent) {
		if (intent.getAction() == null) {
			ctxt.startService(new Intent(ctxt, BeltegoedIntentService.class));
		} else {
			super.onReceive(ctxt, intent);
		}
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
		// onUpdate is called each 12 hours, as defined in widget_provider.xml
		context.startService(new Intent(context, BeltegoedIntentService.class));
	}

	// Using IntentService instead of Service is right here. The IntentService
	// can be
	// started multiple times, with each start representing a distinct piece of
	// work to be
	// accomplished. No need to start/stop this kind of service by ourselves.
	public static class BeltegoedIntentService extends IntentService {
		private static Boolean updatedOnce = false;

		public BeltegoedIntentService() {
			super("BeltegoedWidget$BeltegoedIntentService");
		}

		@Override
		public void onHandleIntent(Intent intent) {
			ComponentName me = new ComponentName(this, BeltegoedWidget.class);
			AppWidgetManager mgr = AppWidgetManager.getInstance(this);
			if (needsUpdate()) {
				// Quickly show at least some text to get rid of default android
				// message "Problem loading widget".
				mgr.updateAppWidget(me, showLoading(this));
				mgr.updateAppWidget(me, buildUpdate(this));
			}
		}

		private RemoteViews showLoading(Context context) {
			RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget);
			updateViews.setTextViewText(R.id.amount_left, getText(R.string.loading_account));
			return (updateViews);
		}

		private Boolean needsUpdate() {
			// After the widget is put on the home screen, it needs one query.
			if (!updatedOnce) {
				updatedOnce = true;
				return true;
			}

			// Don't query the service too fast (after each app resume), which
			// is everytime the device
			// returns from sleep as it's a home screen widget.
			SharedPreferences settings = getSharedPreferences(AccountActivity.PREFS_NAME, 0);
			Long lastlogin = settings.getLong(AccountActivity.LAST_LOGIN, -1);

			long currentts = (new Date()).getTime();
			// 43200000 Milliseconds -> 12 h
			if (lastlogin > 0 && (lastlogin + 43200000) > currentts) {
				// Last login was recent enough (earlier than 12h ago)
				return false;
			}

			// Needs an update
			return true;
		}

		private RemoteViews buildUpdate(Context context) {
			RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.widget);

			SharedPreferences settings = getSharedPreferences(AccountActivity.PREFS_NAME, 0);
			Account account = new Account();
			account.setUsername(settings.getString(AccountActivity.USERNAME, ""));
			account.setPassword(settings.getString(AccountActivity.PASSWORD, ""));
			account.setProvider(settings.getString(AccountActivity.PROVIDER, ""));
			Long lastlogin = settings.getLong(AccountActivity.LAST_LOGIN, -1);
			if (lastlogin > 0) {
				AccountDetails parseResult = BeltegoedService.fetchCreditDetails(account);

				if (parseResult != null) {
					if (parseResult.parseResult == PARSE_RESULT.OK) {
						updateViews.setProgressBar(R.id.amount, parseResult.startAmount, parseResult.amountLeft, false);

						String text = "Onbekend";
						if (parseResult.amountLeft == 0) {
							if (parseResult.amountUnit == AMOUNT_UNIT.EURO) {
								text = "€ -" + Tools.CentsToEuroString(parseResult.extraAmount) + " buiten";
							} else if (parseResult.amountUnit == AMOUNT_UNIT.MINUTES) {
								text = "Alles verbruikt.";
							}
						} else {
							if (parseResult.amountUnit == AMOUNT_UNIT.EURO) {
								text = "€ " + Tools.CentsToEuroString(parseResult.amountLeft) + " over";
							} else if (parseResult.amountUnit == AMOUNT_UNIT.MINUTES) {
								text = parseResult.amountLeft + " min. over";
							}
						}

						updateViews.setTextColor(R.id.amount_left, R.drawable.solid_black);
						updateViews.setTextViewText(R.id.amount_left, text);
					} else {
						// Failed to update GUI, keep data but make text color
						// red to indicate old data
						updateViews.setTextColor(R.id.amount_left, R.drawable.solid_dark_red);
					}
				}
			} else {
				// No account configured
				updateViews.setTextViewText(R.id.amount_left, getText(R.string.configure_required));
			}

			return (updateViews);
		}
	}
}