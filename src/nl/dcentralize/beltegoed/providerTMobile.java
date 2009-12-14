package nl.dcentralize.beltegoed;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import nl.dcentralize.beltegoed.AccountDetails.AMOUNT_UNIT;
import nl.dcentralize.beltegoed.AccountDetails.PARSE_RESULT;
import nl.dcentralize.beltegoed.AccountDetails.PARSE_STEP;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

public class providerTMobile {
	public static String verifyAccount(String username, String password) {
		if (username.length() == 0) {
			return "Onjuiste gebruikersnaam. Formaat is: niet leeg";
		}

		if (password.length() == 0) {
			return "Onjuist wachtwoord. Formaat is: niet leeg";
		}
		return null;
	}

	public static AccountDetails ParseTMobile(String username, String password) {
		AccountDetails parseResult = new AccountDetails();
		parseResult.provider = "T-Mobile";
		parseResult.appendLogMessage(PARSE_STEP.INIT, "");

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpResponse response;
			HttpEntity entity;
			HttpGet httpget;
			List<NameValuePair> nvps;
			HttpPost httpost;

			// Part 1. Get the login form.
			httpget = new HttpGet("https://www.t-mobile.nl/persoonlijk/htdocs/page/homepage.aspx");

			response = httpclient.execute(httpget);
			entity = response.getEntity();

			String viewstate = null;
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.LOGIN_FORM, str);

				// XML is not valid, so split.
				viewstate = str.split("__VIEWSTATE\" value=\"")[1].split("\"")[0];

				entity.consumeContent();
			}

			// Part 2. Perform the login.
			httpost = new HttpPost(
					"https://www.t-mobile.nl/persoonlijk/htdocs/page/homepage.aspx?vid=PersoonlijkLogin&redirectUrl=&originalUrl=");
			nvps = new ArrayList<NameValuePair>();
			nvps
					.add(new BasicNameValuePair(
							"ctl00$ctl00$ScriptManager1",
							"ctl00$ctl00$ctl03$relatedLinkRepeater$ctl00$ctl00$LoginWizard$modalPopupUpdatePanel|ctl00$ctl00$ctl03$relatedLinkRepeater$ctl00$ctl00$LoginWizard$loginControl$loginButton"));
			nvps.add(new BasicNameValuePair("ctl00_ctl00_ScriptManager1_HiddenField", ";;AjaxControlToolkit, Version"));
			nvps.add(new BasicNameValuePair("__EVENTTARGET",
					"ctl00$ctl00$ctl03$relatedLinkRepeater$ctl00$ctl00$LoginWizard$loginControl$loginButton"));
			nvps.add(new BasicNameValuePair("__EVENTARGUMENT", ""));
			nvps.add(new BasicNameValuePair("__VIEWSTATE", viewstate));
			nvps.add(new BasicNameValuePair("ctl00$ctl00$ctl01$ctl00$itemRepeater$ctl03$ctl00$SearchBox$searchTextBox",
					"Zoekwoord"));
			nvps.add(new BasicNameValuePair(
					"ctl00$ctl00$ctl03$relatedLinkRepeater$ctl00$ctl00$LoginWizard$loginControl$usernameTextBox",
					username));
			nvps.add(new BasicNameValuePair(
					"ctl00$ctl00$ctl03$relatedLinkRepeater$ctl00$ctl00$LoginWizard$loginControl$passwordTextBox",
					password));
			nvps.add(new BasicNameValuePair("loginButtonClickCheck", "true"));
			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

			response = httpclient.execute(httpost);

			entity = response.getEntity();
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.LOGGED_IN, str);

				if (str.contains("wc136 ErrorMessage")) {
					// De gebruikersnaam en/of het wachtwoord is onjuist. Let op
					// kleine letters en hoofdletters.
					parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN;
					parseResult.setErrorMessage(str);
					return parseResult;
				}

				entity.consumeContent();
			}

			// Part 3. Get callstatus view (details).
			httpget = new HttpGet("https://www.t-mobile.nl/My_T-mobile/htdocs/page/calling/status/callstatusview.aspx");

			response = httpclient.execute(httpget);
			entity = response.getEntity();

			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.ACCOUNT_DETAILS, str);

				// 19-11-2009
				final String lastUpdateDate = str.split("UpdateDateColumn")[1].split("<b>")[1].split("</b>")[0];

				// "i-300" and "i-300 SMS" ordering varies.
				String account1 = str.split("UpdateDateColumn")[1].split("contentGrey4'>&nbsp;")[1].split("</span>")[0];
				String account2 = null;
				try {
					account2 = str.split("UpdateDateColumn")[1].split("contentGrey4'>&nbsp;")[2].split("</span>")[0];
				} catch (Exception e) {
					// May or may not have SMS bundle
				}

				String callBundle = null;
				String smsBundle = null;
				if (account1.contains("SMS")) {
					smsBundle = account1;
					callBundle = account2;
				} else if (account2 != null && account2.contains("SMS")) {
					smsBundle = account2;
					callBundle = account1;
				} else {
					// No SMS bundle
					callBundle = account1;
				}

				// i-300
				final String accountType = callBundle;

				// 300
				String startAmountRaw = "999";
				try {
					startAmountRaw = accountType.split("i-")[1];
				} catch (Exception e) {
				}
				int startAmount = Integer.parseInt(startAmountRaw);

				// 66
				final String currentAmountRaw = str.split("Resterende belminuten voor deze maand:")[1].split("bold\">")[1]
						.split(":")[0];
				int currentAmount = Integer.parseInt(currentAmountRaw);

				// 2,32
				final String extraAmountRaw = str.split("UsageCostColumn")[1].split("€ ")[1].split("</strong>")[0];
				int extraAmountCents = Integer.parseInt(extraAmountRaw.replace(",", ""));
						
				entity.consumeContent();

				parseResult.accountType = accountType;
				parseResult.amountUnit = AMOUNT_UNIT.MINUTES;
				parseResult.startAmount = startAmount;
				parseResult.amountLeft = currentAmount;
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
				// XXX: Tijd is ook op te halen, van persoonlijke landing page
				parseResult.lastProviderUpdate = df2.parse(lastUpdateDate + " " + "00:00");
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
