package nl.dcentralize.beltegoed;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;

public class CustomExceptionHandler implements UncaughtExceptionHandler {

	private UncaughtExceptionHandler defaultUEH;

	public CustomExceptionHandler() {
		this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();

	}

	public void uncaughtException(Thread t, Throwable e) {
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);

		Tools.writeToSD("Beltegoed-error-log.txt", result.toString());

		printWriter.close();

		defaultUEH.uncaughtException(t, e);
	}
}
