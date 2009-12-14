package nl.dcentralize.beltegoed;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class providerKPN {
	public static String verifyAccount(String username, String password) {
		if (username.length() == 0) {
			return "Onjuiste gebruikersnaam. Formaat is: niet leeg";
		}

		if (password.length() == 0) {
			return "Onjuist wachtwoord. Formaat is: niet leeg";
		}
		return null;
	}

	public static AccountDetails ParseKPN(String username, String password) {
		AccountDetails parseResult = new AccountDetails();
		parseResult.provider = "KPN";
		parseResult.appendLogMessage(PARSE_STEP.INIT, "");

		try {
			DefaultHttpClient httpclient = new DefaultHttpClient();
			HttpResponse response;
			HttpEntity entity;
			HttpGet httpget;
			List<NameValuePair> nvps;
			HttpPost httpost;

			// Part 1. Perform the login.
			httpost = new HttpPost("https://www.kpn.com/web/restricted/form?formelement=512663");
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
				parseResult.appendLogMessage(PARSE_STEP.LOGGED_IN, str);

				entity.consumeContent();
			}

			// Part 1. Get telephone number.
			httpget = new HttpGet("https://www.kpn.com/web/form/show?source=form&formelement=886373");

			response = httpclient.execute(httpget);
			entity = response.getEntity();

			String mobilenr = null;
			if (entity != null) {
				// Do something useful with the entity and, when done, call
				// consumeContent() to make sure the connection can be
				// re-used
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.EXTRA, str);

				if (str.contains("sessionlost")) {
					parseResult.parseResult = PARSE_RESULT.INVALID_LOGIN;
					parseResult.setErrorMessage(str);
					return parseResult;
				}

				// Seen 2 possible repsonses.
				// <response><mco_mobile_nr_list_split>0612345678;</mco_mobile_nr_list_split><user_id>1234567</user_id></response>
				// <response><mco_mobile_nr_list_split>06;0612345678;</mco_mobile_nr_list_split><user_id>1234567</user_id></response>
				String[] numbers = str.split("<mco_mobile_nr_list_split>")[1].split(";</mco_mobile_nr_list_split>")[0]
						.split(";");
				for (int i = 0; i < numbers.length; i++) {
					if (numbers[i].length() == 10) {
						mobilenr = numbers[i];
					}
				}

				entity.consumeContent();
			}

			// Part 3. Perform the cost control lookup.
			httpost = new HttpPost("https://www.kpn.com/web/form/show?formelement=812470&source=form");
			nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("mobilenr", mobilenr));

			httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
			response = httpclient.execute(httpost);
			entity = response.getEntity();

			if (entity != null) {
				String str = Tools.convertStreamToString(entity.getContent());
				parseResult.appendLogMessage(PARSE_STEP.ACCOUNT_DETAILS, str);

				// The XML line positions are not fixed.
				// I.e. extra fields are added on bundle over usage.
				// So need to do proper XML parsing, not simple line splitting.
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();

				// Needs to read from stream, and the stream can only be
				// consumed once, so create a new one.
				InputStream is = new ByteArrayInputStream(str.getBytes());
				Document dom = builder.parse(is);

				Element root = dom.getDocumentElement();

				// 2009-12-20
				NodeList items = root.getElementsByTagName("ns0:END_DATE");
				// We need not the value of an Element node, but the value of an
				// Element node's first child.
				String endDateRaw = items.item(0).getFirstChild().getNodeValue();
				SimpleDateFormat df1 = new SimpleDateFormat("yyyy-MM-dd");
				Date endDate = df1.parse(endDateRaw);

				// 2009-11-18
				items = root.getElementsByTagName("ns0:START_DATE");
				String startDateRaw = items.item(0).getFirstChild().getNodeValue();
				Date startDate = df1.parse(startDateRaw);

				// Flexibel 22,50
				items = root.getElementsByTagName("ns0:DESCRIPTION");
				// The first DESCRIPTION is (probably) the Flexibel one, not the
				// Surf en Mail Totaal
				final String accountType = items.item(0).getFirstChild().getNodeValue();

				// 23,50 (May be more than the monthly bundle, including last
				// months leftovers)
				items = root.getElementsByTagName("ns0:SIZE");
				// 2350.0
				String centstartAmountRaw = items.item(0).getFirstChild().getNodeValue();
				int startAmountCents = Integer.parseInt(centstartAmountRaw
						.substring(0, centstartAmountRaw.length() - 2));
				
				// 19,60
				items = root.getElementsByTagName("ns0:COST");
				// 196.0
				String centUsedAmountRaw = items.item(0).getFirstChild().getNodeValue();
				int usedAmountCents = Integer.parseInt(centUsedAmountRaw.substring(0, centUsedAmountRaw.length() - 2));
				// Note, KPN doesn't provide what amount is left, just what is
				// used. Calculate the former.
				int currentAmountCents = startAmountCents - usedAmountCents;

				items = root.getElementsByTagName("ns0:OVER_BUNDLE_COST");
				int extraAmountCents = 0;
				if (items.getLength() > 0) {
					String centextraAmountRaw = items.item(0).getFirstChild().getNodeValue();
					extraAmountCents = Integer.parseInt(centextraAmountRaw
							.substring(0, centextraAmountRaw.length() - 2));
				}

				entity.consumeContent();

				parseResult.accountType = accountType;
				parseResult.startAmount = startAmountCents;
				parseResult.amountLeft = currentAmountCents;
				parseResult.extraAmount = extraAmountCents;
				parseResult.endDate = endDate;
				parseResult.startDate = startDate;
				// Volgens FAQ: update vindt elke nacht plaats. Update verbruik
				// buiten bundel nog later (onbekend).
				parseResult.lastProviderUpdate = null;
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
