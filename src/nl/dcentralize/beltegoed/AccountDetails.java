package nl.dcentralize.beltegoed;

import java.util.Date;

public class AccountDetails {
	public enum PARSE_RESULT {
		NONE, CACHED, NO_INTERNET, CONN_FAIL, OK, INVALID_LOGIN, TEMP_BLOCK, UNKNOWN
	};

	public enum PARSE_STEP {
		INIT, LOGIN_FORM, LOGGED_IN, EXTRA, ACCOUNT_DETAILS, SUCCESS
	};

	public enum AMOUNT_UNIT {
		EURO, MINUTES
	};

	public PARSE_RESULT parseResult = PARSE_RESULT.NONE;
	private String errorMessage = "";
	private String logMessage = "";

	// Provider ("Vodafone" or "KPN")
	public String provider;
	public String username;
	public String password;
	
	// Provider specific account type ("Basis abonnement 50,00")
	public String accountType;
	public AMOUNT_UNIT amountUnit = AMOUNT_UNIT.EURO; 
	// Amount (cents) of the start at a period (could include last periods
	// leftovers).
	public int startAmount;
	// Amount (cents) left of this period.
	public int amountLeft;
	// Amount (euro) outside bundle (either over or extra)
	public int extraAmount;
	// This period started at date (Vodafone always 1st of a month, KPN: e.g.
	// 2009-10-20)
	public Date startDate;
	// This period ends at date (Vodafone always last day of a month, KPN: e.g.
	// 2009-11-18)
	public Date endDate;
	// Last update
	public Date lastProviderUpdate = null;

	public void setErrorMessage(String errorMessage) {
		if (errorMessage != null) {
			this.logMessage += "Error reported: " + errorMessage;			
			this.errorMessage = errorMessage;
		}
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void appendLogMessage(PARSE_STEP step, String logMessage) {
		if (logMessage != null) {
			this.logMessage += "Step: " + step.name() + "Content: "
					+ logMessage;
		}
	}

	public String getLogMessage() {
		return logMessage;
	}
}
