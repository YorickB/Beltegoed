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

public class providerVodafone {

	public static ParseResults ParseVodafone(String username, String password) {
		ParseResults parseResult = new ParseResults();

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();

			// Part 1. Get the login form.
			HttpGet httpget = new HttpGet(
					"https://my.vodafone.nl/prive/my_vodafone?errormessage=&errorcode=");

			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
 
			String id = null;
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

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
 
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used 
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

				entity.consumeContent();
			}

			List<Cookie> cookies = httpclient.getCookieStore().getCookies();
			String msisdn = null;
			if (cookies.isEmpty()) {
				// No cookies? XXX
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
 
			if (entity != null) { 
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.logMessage = str;

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
