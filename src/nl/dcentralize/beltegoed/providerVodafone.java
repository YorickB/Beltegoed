package nl.dcentralize.beltegoed;

import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.dcentralize.beltegoed.AccountDetails.PARSE_RESULT;
import nl.dcentralize.beltegoed.AccountDetails.PARSE_STEP;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

public class providerVodafone {

	public static String verifyAccount(String username, String password) {
		Pattern p = Pattern.compile("^06[a0-9]{8}$");
		Matcher m = p.matcher(username);
		if (m.find()) {
			// Input ok, like "0612345678"
		} else {
			return "Onjuiste gebruikersnaam. Formaat is: 0612345678";
		}

		if (password.length() == 0) {
			return "Onjuist wachtwoord. Formaat is: niet leeg";
		}
		return null;
	}

	public static AccountDetails ParseVodafone(String username, String password) {
		AccountDetails parseResult = new AccountDetails();
		parseResult.provider = "Vodafone";
		parseResult.appendLogMessage(PARSE_STEP.INIT, "");
		HttpResponse response;

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();

			// Part 1. Get the login form.
			HttpGet httpget = new HttpGet("https://my.vodafone.nl/prive/my_vodafone?errormessage=&errorcode=");
			try {
				response = httpclient.execute(httpget);
			} catch (IOException e) {
				parseResult.parseResult = PARSE_RESULT.UNKNOWN;
				parseResult.setErrorMessage(e.getCause().getMessage());
				return parseResult;
			}

			HttpEntity entity = response.getEntity();

			String id = null;
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.LOGIN_FORM, str);

				if (str.contains("Helaas is de Vodafone website tijdelijk niet bereikbaar.")) {
					// Funny vodafone webdevs. In the comments it says:
					// Yes! We've reached infinite improbability!
					parseResult.parseResult = PARSE_RESULT.CONN_FAIL;
					return parseResult;
				}

				String[] split = str.split("\" name=\"ID\"");
				id = split[0].substring(split[0].length() - 32, split[0].length());
				entity.consumeContent();
			}

			// Part 2. Perform the login and send the ID from the form
			// along.
			HttpPost httpost = new HttpPost("https://login.vodafone.nl/signon?provider=myvodafone");
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("ID", id));
			nvps.add(new BasicNameValuePair("assertionconsumerurl", "Prive/My_Vodafone"));
			nvps.add(new BasicNameValuePair("loginerrorurl", "prive/my_vodafone"));
			nvps.add(new BasicNameValuePair("password", password));
			nvps.add(new BasicNameValuePair("username", username));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

			try {
				response = httpclient.execute(httpost);
			} catch (ClientProtocolException e) {
				String errorMessage = e.getCause().getMessage();
				if (errorMessage.contains("errorcode=UE_LOGIN_TIMEOUT")) {
					// Je inlogtijd is verstreken. Probeer het nog een keer.
				} else if (errorMessage.contains("errorcode=UE_LAST_ATTEMPT")) {
					// De inloggegevens zijn niet juist. Je hebt nog 1
					// inlogpoging. Daarna wordt het inloggen geblokkeerd.
				} else if (errorMessage.contains("errorcode=UE_ACCOUNT_BLOCKED")) {
					// De inloggegevens zijn niet juist. Je kunt nu 15 minuten
					// lang niet inloggen
					parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN_TEMP_BLOCK;
					parseResult.setErrorMessage(e.getCause().getMessage());
					return parseResult;
				}

				parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN;
				parseResult.setErrorMessage(e.getCause().getMessage());
				return parseResult;
			}
			entity = response.getEntity();

			String str = "";
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.LOGGED_IN, str);

				entity.consumeContent();
			}

			Boolean prepaid = str.contains("myvodafone.costcontrol.prepaid.main.credit.value");

			if (prepaid) {
				// Part 3a. [Prepaid] is directly visible after login.
				// 3-12-2010
				final String validUntil = str.split("myvodafone.costcontrol.prepaid.main.credit.value")[1]
						.split("is geldig tot <span class=\"bold petrol\">")[1].split("</span>")[0];
				// 29,33
				final String startAmountRaw = str.split("myvodafone.costcontrol.prepaid.main.credit.value")[1]
						.split("&euro;&nbsp;")[1].split("</span>")[0];
				int startAmountCents = Integer.parseInt(startAmountRaw.replace(",", ""));

				parseResult.accountType = "Prepaid";
				parseResult.startAmount = startAmountCents;
				parseResult.amountLeft = startAmountCents;
				parseResult.extraAmount = 0;
				SimpleDateFormat df1 = new SimpleDateFormat("d-M-yyyy");
				parseResult.startDate = df1.parse(validUntil);
				parseResult.endDate = df1.parse(validUntil);
			} else {
				// Part 3b. [Sim-only] Perform the cost control lookup.
				httpost = new HttpPost("https://my.vodafone.nl/Prive/My_Vodafone");
				nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair("_fp", "info"));
				nvps.add(new BasicNameValuePair("_fs", "es_prive_my vodafone_verbruik en prijsplan_costcontrol"));
				nvps.add(new BasicNameValuePair("_st", ""));
				nvps.add(new BasicNameValuePair("getdata", "true"));

				httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
				response = httpclient.execute(httpost);
				entity = response.getEntity();

				if (entity != null) {
					str = Tools.convertStreamToString(entity.getContent());
					parseResult.appendLogMessage(PARSE_STEP.ACCOUNT_DETAILS, str);
					if (str.contains("global.our.apology")) {
						// Vodafone is doing maintenance.
						// "op dit moment is het niet mogelijk om je tegoed en verbruik te tonen. Probeer het op een later tijdstip nogmaals."
						// XXX: handle this case
					}

					// Basis abonnement 50,00
					final String accountType = str.split("myvodafone.dashboard.usage.priceplan.mijnabonnement")[1]
							.split("</h1>")[1].split("<br>")[0];
					// 50,00
					final String startAmountRaw = str.split("myvodafone.dashboard.usage.priceplan.tegoed.start ")[1]
							.split("&euro;&nbsp;")[1].split("</span>")[0];
					int startAmountCents = Integer.parseInt(startAmountRaw.replace(",", ""));

					// Trailing space needs to stay
					// 29,00
					final String currentAmountRaw = str.split("myvodafone.dashboard.usage.priceplan.tegoed.start ")[1]
							.split("&euro;&nbsp;")[2].split("</span>")[0];
					int currentAmountCents = Integer.parseInt(currentAmountRaw.replace(",", ""));

					// 3,13
					final String extraAmountRaw = str.split("myvodafone.dashboard.usage.priceplan.buiten")[1]
							.split("&euro;&nbsp;")[1].split("</span>")[0];
					int extraAmountCents = Integer.parseInt(extraAmountRaw.replace(",", ""));

					/*
					 * // 19 november&nbsp;2009 final String lastUpdateDate =
					 * str .split("bundle.timestamp.datePrefix")[1]
					 * .split("<nobr>")[1].split("</nobr>")[0].replace("&nbsp",
					 * "");
					 */
					// 19-11-2009
					final String lastUpdateDate = str.split("myvodafone.dashboard.cost.textpart.two -->")[1]
							.split("</span>")[0];
					// 12:57
					final String lastUpdateTime = str.split("bundle.timestamp.timePrefix")[1].split("<nobr>")[1]
							.split("</nobr>")[0];

					entity.consumeContent();

					parseResult.accountType = accountType;
					parseResult.startAmount = startAmountCents;
					parseResult.amountLeft = currentAmountCents;
					parseResult.extraAmount = extraAmountCents;
					Calendar calendar = Calendar.getInstance();
					int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
					int minDay = calendar.getActualMinimum(Calendar.DAY_OF_MONTH);
					int month = calendar.get(Calendar.MONTH) + 1;
					int year = calendar.get(Calendar.YEAR);
					SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd");
					parseResult.startDate = df1.parse(year + "-" + month + "-" + minDay);
					parseResult.endDate = df1.parse(year + "-" + month + "-" + maxDay);

					SimpleDateFormat df2 = new SimpleDateFormat("dd-MM-yyyy HH:mm");
					parseResult.lastProviderUpdate = df2.parse(lastUpdateDate + " " + lastUpdateTime);
				}
			}

			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();

		} catch (UnknownHostException e) {
			parseResult.parseResult = PARSE_RESULT.NO_INTERNET;
			if (e.getCause() == null) {
				parseResult.setErrorMessage(e.getMessage());
			} else {
				parseResult.setErrorMessage(e.getCause().getMessage());
			}
		} catch (Exception e) {
			parseResult.parseResult = PARSE_RESULT.UNKNOWN;
			if (e.getCause() == null) {
				parseResult.setErrorMessage(e.getMessage());
			} else {
				parseResult.setErrorMessage(e.getCause().getMessage());
			}
			return parseResult;
		}

		parseResult.appendLogMessage(PARSE_STEP.SUCCESS, "");
		parseResult.parseResult = PARSE_RESULT.OK;
		return parseResult;
	}
}
