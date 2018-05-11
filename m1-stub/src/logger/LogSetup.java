package logger;

import java.io.IOException;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * Represents the initialization for the server logging with Log4J.
 */
public class LogSetup {

	public static final String UNKNOWN_LEVEL = "UnknownLevel";
	private static Logger logger = Logger.getRootLogger();
	private String logdir;
	
	/**
	 * Initializes the logging for the echo server. Logs are appended to the 
	 * console output and written into a separated server log file at a given 
	 * destination.
	 * 
	 * @param logdir the destination (i.e. directory + filename) for the 
	 * 		persistent logging information.
	 * @throws IOException if the log destination could not be found.
	 */
	public LogSetup(String logdir, Level level) throws IOException {
		this.logdir = logdir;
		initialize(level);
	}

	private void initialize(Level level) throws IOException {
		PatternLayout layout = new PatternLayout( "%d{ISO8601} %-5p [%t] %c: %m%n" );
		FileAppender fileAppender = new FileAppender( layout, logdir, true );		
	    
	    ConsoleAppender consoleAppender = new ConsoleAppender(layout);
		logger.addAppender(consoleAppender);
		logger.addAppender(fileAppender);
		logger.setLevel(level);
	}
	
	public static boolean isValidLevel(String levelString) {
		boolean valid = false;
		
		if(levelString.equals(Level.ALL.toString())) {
			valid = true;
		} else if(levelString.equals(Level.DEBUG.toString())) {
			valid = true;
		} else if(levelString.equals(Level.INFO.toString())) {
			valid = true;
		} else if(levelString.equals(Level.WARN.toString())) {
			valid = true;
		} else if(levelString.equals(Level.ERROR.toString())) {
			valid = true;
		} else if(levelString.equals(Level.FATAL.toString())) {
			valid = true;
		} else if(levelString.equals(Level.OFF.toString())) {
			valid = true;
		}
		
		return valid;
	}
	
	public static String getPossibleLogLevels() {
		return "ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF";
	}
}
