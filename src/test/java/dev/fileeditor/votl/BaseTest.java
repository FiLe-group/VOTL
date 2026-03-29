package dev.fileeditor.votl;


import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

public class BaseTest {

	private static final Logger logger = (Logger) LoggerFactory.getLogger(BaseTest.class);

	public static Logger getLogger() {
		return logger;
	}

}
