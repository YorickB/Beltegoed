package nl.dcentralize.beltegoed;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import nl.dcentralize.beltegoed.ParseResults.PARSE_RESULT;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.util.Log;

public class providerKPN {

	public static ParseResults ParseKPN(String username, String password) {
		ParseResults parseResult = new ParseResults();

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpResponse response;
			HttpEntity entity;

			// Part 1. Get the login form.
			HttpGet httpget = new HttpGet(
					"http://www.kpn.com/klantenservice.htm");

			response = httpclient.execute(httpget);
			entity = response.getEntity();

			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

				entity.consumeContent();
			}

			// Part 2. Perform the login and send the ID from the form
			// along. (may this be skipped?)
			HttpPost httpost = new HttpPost(
					"https://access.kpn.com/CAUT/AuthenticationServlet");
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("AUTHMETHOD", "UserPassword"));
			nvps
					.add(new BasicNameValuePair(
							"HiddenURI",
							"https://www.kpn.com/kpn/restricted/formhandler?form=1801594&amp;formelement=1801351"));
			nvps
					.add(new BasicNameValuePair(
							"RedirectOnFailure",
							"https://www.kpn.com/kpn/formhandler?form=1801594&amp;formelement=loginfailed&amp;pageid=2001200"));
			nvps.add(new BasicNameValuePair("showloginform", "false"));
			nvps.add(new BasicNameValuePair("formpartcode", "showloginform"));
			nvps
					.add(new BasicNameValuePair("onErrorMessage",
							"Uw gebruikersnaam en/of wachtwoord is niet bekend. Probeer het nogmaals."));
			nvps.add(new BasicNameValuePair("saveusername", "save"));
			// <input id="loginSubmit" class="submit"
			// src="/v2/static/kpncom/images/submit_on_green.gif"
			// onclick="s.sa('kpncomprod,kpncom-cs-2');s.pageName='mijnkpn:inloggen:f4:submit';s.prop14 = '';s.tl( this, 'o', s.pageName);"
			// type="image">
			nvps.add(new BasicNameValuePair("LOCALE", "en_US"));
			nvps.add(new BasicNameValuePair("usr_password", password));
			nvps.add(new BasicNameValuePair("usr_name", username));
			nvps
					.add(new BasicNameValuePair(
							"swfrmsig",
							"e7e388ed9114cdefba766d9b6fb68fa9b5c056b86305cc570e3e9706260852613beaa9323ec52553b373364c06d6c37b6d501b1e593c6e58b8db962561758a3b51fc8bcc6749f4c88cc63c29a62095b2755846001561fce1ac5ee347cd75754206da22df9bf4261868769722e202fbb60f2486b19002b518d01e507464ebbaa6d94cbb0cf31b24c888ac387054860d6ef1168828e6f2b614ccd7cea32897e90f221c32ebb841357edaeb4e486abc4007"));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

			try {
				response = httpclient.execute(httpost);
			} catch (ClientProtocolException e) {

			}
			entity = response.getEntity();

			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

				entity.consumeContent();
			}

			// Part 2a. Perform the login and send the ID from the form
			// along.
			httpost = new HttpPost(
					"https://www.kpn.com/web/restricted/form?formelement=512663");
			nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("usr_password", password));
			nvps.add(new BasicNameValuePair("usr_name", username));
			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

			try {
				response = httpclient.execute(httpost);
			} catch (ClientProtocolException e) {
			}
			entity = response.getEntity();
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

				entity.consumeContent();
			}

			// Part 1. Get telephone number.
			httpget = new HttpGet(
					"https://www.kpn.com/web/form/show?source=form&formelement=886373");

			response = httpclient.execute(httpget);
			entity = response.getEntity();

			String mobilenr = null;
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

				if (str.contains("sessionlost")) {
					parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN;
					parseResult.errorMessage = str;
					return parseResult;
				}

				String[] split = str.split("mco_mobile_nr_list_split>");
				mobilenr = split[1].substring(0, 10);

				entity.consumeContent();
			}

			/*
			 * String customer_nr=null; if (entity != null) { // Do something
			 * useful with the entity and, when done, call // consumeContent()
			 * to make sure the connection can be // re-used String str =
			 * Tools.convertStreamToString(entity.getContent());
			 * parseResult.logMessage = str;
			 * 
			 * String[] split = str .split("g_customerId\" select=\"'");
			 * customer_nr = split[1].split("'")[0]; entity.consumeContent(); }
			 * 
			 * // Part x. Get mobile number by customer_nr httpost = new
			 * HttpPost(
			 * "https://www.kpn.com/web/form/show?formelement=812437&source=form"
			 * ); nvps = new ArrayList<NameValuePair>(); nvps.add(new
			 * BasicNameValuePair("customer_nr", customer_nr));
			 * 
			 * httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			 * response = httpclient.execute(httpost); entity =
			 * response.getEntity();
			 * 
			 * if (entity != null) { String str =
			 * Tools.convertStreamToString(entity.getContent());
			 * parseResult.logMessage = str;
			 * 
			 * // Basis abonnement 50,00 final String accountType = str
			 * .split("myvodafone.dashboard.usage.priceplan.mijnabonnement")[1]
			 * .split("</h1>")[1].split("<br>")[0];
			 */
			// Part 3. Perform the cost control lookup.
			httpost = new HttpPost(
					"https://www.kpn.com/web/form/show?formelement=812470&source=form");
			nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("mobilenr", mobilenr));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			response = httpclient.execute(httpost);
			entity = response.getEntity();

			if (entity != null) {
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

				String[] lines = str.split("\n");
				// Flexibel 20
				final String accountType = lines[11].split("<ns0:DESCRIPTION>")[1]
						.split("</ns0:DESCRIPTION>")[0];

				// 2000
				String centstartAmountRaw = lines[40].split("<ns0:SIZE>")[1]
						.split(".0</ns0:SIZE>")[0];
				final String startAmountRaw = centstartAmountRaw
						.substring(0, 2)
						+ "," + centstartAmountRaw.substring(2, 4);

				// 3427
				String centcurrentAmountRaw = lines[17]
						.split("ns1:Factor=\"0.01\">")[1]
						.split(".0</ns0:COST>")[0];
				// 34,26
				final String currentAmountRaw = centcurrentAmountRaw.substring(
						0, 2)
						+ "," + centcurrentAmountRaw.substring(2, 4);

				// 34,26

				// 3,13
				// 1427.0
				String centextraAmountRaw = lines[45]
						.split("ns1:Factor=\"0.01\">")[1]
						.split(".0</ns0:OVER_BUNDLE_COST>")[0];
				final String extraAmountRaw = centextraAmountRaw
						.substring(0, 2)
						+ "," + centextraAmountRaw.substring(2, 4);

				entity.consumeContent();

				parseResult.accountType = accountType;
				parseResult.startAmountRaw = startAmountRaw;
				parseResult.currentAmountRaw = currentAmountRaw;
				parseResult.extraAmountRaw = extraAmountRaw;
			}

			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			httpclient.getConnectionManager().shutdown();

		} catch (UnknownHostException e) {
			parseResult.parseResult = PARSE_RESULT.NO_INTERNET;
			parseResult.errorMessage = e.getCause().getMessage();
		} catch (Exception e) {
			parseResult.parseResult = PARSE_RESULT.UNKNOWN;
			parseResult.errorMessage = e.getCause().getMessage();
			return parseResult;
		}

		parseResult.parseResult = PARSE_RESULT.OK;
		return parseResult;
	}

}
