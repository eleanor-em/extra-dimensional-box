package unimelb.bitbox.app;

import unimelb.bitbox.server.PeerServer;

import java.util.logging.Logger;

/**
 * Class to initialise the peer-to-peer application.
 */
public class PeerApp
{
	private static final Logger log = Logger.getLogger(PeerApp.class.getName());

    private PeerApp() {}

    public static void main (String[] args) {
    	System.setProperty("java.util.logging.SimpleFormatter.format",
                "[%1$tc] %2$s %4$s: %5$s%n");
        log.info("BitBox Peer starting...");

        try {
            PeerServer.initialise();
        } catch (Exception e) {
            log.severe("failed initialising server");
            e.printStackTrace();
        }
    }
}
