package nl.dcentralize.beltegoed;


public class ParseResults {
	public enum PARSE_RESULT {
		NONE, NO_INTERNET, OK, INVALID_LOGIN, UNKNOWN
	};

	public PARSE_RESULT parseResult = PARSE_RESULT.NONE;
	private String errorMessage = "";
	private String logMessage = "";

	// Provider ("Vodafone" or "KPN")
	public String provider;
	// Provider specific account type ("Basis abonnement 50,00")
	public String accountType;
	// Amount (euro) of the start at a period (could include last periods leftovers).
	public String startAmountRaw;
	// Amount (euro) left of this period.
	public String currentAmountRaw;
	// Amount (euro) outside bundle (either over or extra)
	public String extraAmountRaw;
	// This period started at date (Vodafone always 1st of a month, KPN: e.g. 2009-10-20)
	public String startDateRaw;
	// This period ends at date (Vodafone always last day of a month, KPN: e.g. 2009-11-18)
	public String endDateRaw;
	
	public void setErrorMessage(String errorMessage) {
		if (errorMessage != null) {
			this.errorMessage = errorMessage;
		}
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setLogMessage(String logMessage) {
		if (logMessage != null) {
			this.logMessage = logMessage;
		}
	}

	public String getLogMessage() {
		return logMessage;
	}
}
