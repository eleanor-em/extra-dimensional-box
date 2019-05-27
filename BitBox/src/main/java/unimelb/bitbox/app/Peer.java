package unimelb.bitbox.app;

import unimelb.bitbox.server.ServerMain;

import java.util.logging.Logger;

public class Peer 
{
	private static Logger log = Logger.getLogger(Peer.class.getName());
    public static void main (String[] args) {
    	System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");
        
        try {
            new ServerMain();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }
}
