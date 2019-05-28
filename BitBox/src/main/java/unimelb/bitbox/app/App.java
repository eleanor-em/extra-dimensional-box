package unimelb.bitbox.app;

import unimelb.bitbox.server.PeerServer;

import java.util.logging.Logger;

public class App
{
	private static Logger log = Logger.getLogger(App.class.getName());
    public static void main (String[] args) {
    	System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        
        try {
            new PeerServer();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }
}
