package unimelb.bitbox;

import unimelb.bitbox.clients.ClientServer;
import unimelb.bitbox.util.Configuration;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

public class Peer 
{
	private static Logger log = Logger.getLogger(Peer.class.getName());
    public static void main( String[] args ) throws IOException, NumberFormatException, NoSuchAlgorithmException
    {
    	System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        
        new ServerMain();

        new ClientServer(Integer.parseInt(Configuration.getConfigurationValue("clientPort")));
    }
}
