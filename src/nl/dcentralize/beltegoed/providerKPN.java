package nl.dcentralize.beltegoed;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import nl.dcentralize.beltegoed.ParseResults.PARSE_RESULT;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

public class providerKPN {

	public static ParseResults ParseKPN(String username, String password) {
		ParseResults parseResult = new ParseResults();

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpResponse response;
			HttpEntity entity;
			HttpGet httpget;
			List<NameValuePair> nvps;
			HttpPost httpost;

			// Part 1. Perform the login.
			httpost = new HttpPost(
					"https://www.kpn.com/web/restricted/form?formelement=512663");
			nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("usr_password", password));
			nvps.add(new BasicNameValuePair("usr_name", username));
			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

			response = httpclient.execute(httpost);

			entity = response.getEntity();
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.setLogMessage(str);

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
				parseResult.setLogMessage(str);

				if (str.contains("sessionlost")) {
					parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN;
					parseResult.setErrorMessage(str);
					return parseResult;
				}

				String[] split = str.split("mco_mobile_nr_list_split>");
				mobilenr = split[1].substring(0, 10);

				entity.consumeContent();
			}

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
				parseResult.setLogMessage(str);

				String[] lines = str.split("\n");
				// Flexibel 20
				final String accountType = lines[11].split("<ns0:DESCRIPTION>")[1]
						.split("</ns0:DESCRIPTION>")[0];

				String centstartAmountRaw = lines[40].split("<ns0:SIZE>")[1]
						.split(".0</ns0:SIZE>")[0];
				final String startAmountRaw = centstartAmountRaw
						.substring(0, 2)
						+ "," + centstartAmountRaw.substring(2, 4);

				String centcurrentAmountRaw = lines[17]
						.split("ns1:Factor=\"0.01\">")[1]
						.split(".0</ns0:COST>")[0];
				final String currentAmountRaw = centcurrentAmountRaw.substring(
						0, 2)
						+ "," + centcurrentAmountRaw.substring(2, 4);

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

		parseResult.parseResult = PARSE_RESULT.OK;
		return parseResult;
	}

}
