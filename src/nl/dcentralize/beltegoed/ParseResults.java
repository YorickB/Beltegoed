package nl.dcentralize.beltegoed;

public class ParseResults {
	public enum PARSE_RESULT { NONE, OK, INVALID_LOGIN, UNKNOWN };

	public PARSE_RESULT parseResult = PARSE_RESULT.NONE;
	public String errorMessage = null;
}
 