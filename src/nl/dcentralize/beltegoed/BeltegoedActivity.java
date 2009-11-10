package nl.dcentralize.beltegoed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import nl.dcentralize.beltegoed.ParseResults.PARSE_RESULT;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import com.nullwire.trace.ExceptionHandler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ProgressBar;
import android.widget.TextView;

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

	private void showAccountDetails(String accountName, String startAmountRaw,
			String currentAmountRaw, String extraAmountRaw) {

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
		account_type.setText(accountName);

		TextView amount_text = (TextView) findViewById(R.id.amount_text);
		amount_text.setText("€" + currentAmountRaw + " van €" + startAmountRaw
				+ " over.");

		TextView extra_text = (TextView) findViewById(R.id.extra_text);
		extra_text.setText("€" + extraAmountRaw + " buiten bundel gebruikt.");
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

	private String convertStreamToString(InputStream is) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is),
				8 * 1024);
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

	public ParseResults ParseVodafone() {
		ParseResults parseResult = new ParseResults();

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();

			// Part 1. Get the login form.
			HttpGet httpget = new HttpGet(
					"https://my.vodafone.nl/prive/my_vodafone?errormessage=&errorcode=");

			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();

			Log.i(TAG, "Login form GET:" + response.getStatusLine());

			String id = null;
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = convertStreamToString(entity.getContent());
				Tools.writeToSD("Beltegoed-Vodafone-Part1.txt", str);

				String[] split = str.split("\" name=\"ID\"");
				id = split[0].substring(split[0].length() - 32, split[0]
						.length());
				entity.consumeContent();
			}

			// Part 2. Perform the login and send the ID from the form
			// along.
			HttpPost httpost = new HttpPost(
					"https://login.vodafone.nl/signon?provider=myvodafone");
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("ID", id));
			nvps.add(new BasicNameValuePair("assertionconsumerurl",
					"Prive/My_Vodafone"));
			nvps.add(new BasicNameValuePair("loginerrorurl",
					"prive/my_vodafone"));
			nvps.add(new BasicNameValuePair("password", password));
			nvps.add(new BasicNameValuePair("username", username));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

			try {
				response = httpclient.execute(httpost);
			} catch (ClientProtocolException e) {
				parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN;
				parseResult.errorMessage = e.getCause().getMessage();
				return parseResult;
			}
			entity = response.getEntity();

			Log.i(TAG, "Submitted login form GET:" + response.getStatusLine());
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = convertStreamToString(entity.getContent());
				Tools.writeToSD("Beltegoed-Vodafone-Part2.txt", str);

				entity.consumeContent();
			}

			List<Cookie> cookies = httpclient.getCookieStore().getCookies();
			String msisdn = null;
			if (cookies.isEmpty()) {
				Log.e(TAG, "No cookies?");
			} else {
				for (int i = 0; i < cookies.size(); i++) {
					if (cookies.get(i).getName().equals(
							"_preLoginRequestParameters")) {
						String preLoginRequestParameters = cookies.get(i)
								.getValue();
						msisdn = preLoginRequestParameters.split("MSISDN")[1]
								.split("&ID")[0];
					}
				}
			}

			// Part 3. Perform the cost control lookup.
			httpost = new HttpPost("https://my.vodafone.nl/Prive/My_Vodafone");
			nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("_fp", "info"));
			nvps.add(new BasicNameValuePair("_fs",
					"es_prive_my vodafone_verbruik en prijsplan_costcontrol"));
			nvps.add(new BasicNameValuePair("_st", ""));
			nvps.add(new BasicNameValuePair("getdata", "true"));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			response = httpclient.execute(httpost);
			entity = response.getEntity();

			Log.i(TAG, "Cost control POST:" + response.getStatusLine());
			if (entity != null) {
				String str = convertStreamToString(entity.getContent());
				Tools.writeToSD("Beltegoed-Vodafone-Part3.txt", str);

				// Basis abonnement 50,00
				final String accountType = str
						.split("myvodafone.dashboard.usage.priceplan.mijnabonnement")[1]
						.split("</h1>")[1].split("<br>")[0];
				// 50,00
				final String startAmountRaw = str
						.split("myvodafone.dashboard.usage.priceplan.tegoed.start ")[1]
						.split("&euro;&nbsp;")[1].split("</span>")[0];
				// Trailing space needs to stay
				// 29,00
				final String currentAmountRaw = str
						.split("myvodafone.dashboard.usage.priceplan.tegoed.start ")[1]
						.split("&euro;&nbsp;")[2].split("</span>")[0];
				// 3,13
				final String extraAmountRaw = str
						.split("myvodafone.dashboard.usage.priceplan.buiten")[1]
						.split("&euro;&nbsp;")[1].split("</span>")[0];

				entity.consumeContent();

				BeltegoedActivity.this.runOnUiThread(new Runnable() {
					public void run() {
						showAccountDetails(accountType, startAmountRaw,
								currentAmountRaw, extraAmountRaw);
					}
				});
			}

			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();

		} catch (UnknownHostException e) {
			Log.e(TAG, "Internet probably not available", e);
			Tools.writeToSD("Beltegoed-Vodafone-error.txt", e.getCause()
					.getMessage());
		} catch (Exception e) {
			Tools.writeToSD("Beltegoed-Vodafone-error.txt", e.getCause()
					.getMessage());
			parseResult.parseResult = PARSE_RESULT.UNKNOWN;
			parseResult.errorMessage = e.getCause().getMessage();
			return parseResult;
		}

		parseResult.parseResult = PARSE_RESULT.OK;
		return parseResult;
	}

	private class LocalParseTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			showDialog(R.id.dialog_load);
		}

		@Override
		protected Void doInBackground(Void... params) {
			ParseResults parseResult = null;
			if (provider.equals(AccountActivity.PROVIDER_VODAFONE)) {
				parseResult = ParseVodafone();
			}

			if (parseResult != null) {
				if (parseResult.parseResult == PARSE_RESULT.INVALID_LOGIN) {
					BeltegoedActivity.this.runOnUiThread(new Runnable() {
						public void run() {
							new AlertDialog.Builder(BeltegoedActivity.this)
									.setMessage("Login/password incorrect")
									.show();
						}
					});
				} else if (parseResult.parseResult != PARSE_RESULT.OK) {
					BeltegoedActivity.this.runOnUiThread(new Runnable() {
						public void run() {
							new AlertDialog.Builder(BeltegoedActivity.this)
									.setMessage(getText(R.string.error_parsing))
									.show();
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