package nl.dcentralize.beltegoed;


public class ParseResults {
	public enum PARSE_RESULT {
		NONE, NO_INTERNET, OK, INVALID_LOGIN, UNKNOWN
	};

	public PARSE_RESULT parseResult = PARSE_RESULT.NONE;
	private String errorMessage = "";
	private String logMessage = "";

	public String accountType;
	public String startAmountRaw;
	public String currentAmountRaw;
	public String extraAmountRaw;

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
