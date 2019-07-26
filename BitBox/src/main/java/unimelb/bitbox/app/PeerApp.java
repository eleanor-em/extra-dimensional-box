package unimelb.bitbox.app;

import unimelb.bitbox.server.PeerServer;
import unimelb.bitbox.util.config.Configuration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to initialise the peer-to-peer application.
 *
 * @author Aaron Harwood
 * @author Eleanor McMurtry
 */
public class PeerApp {
	private static final Logger log = Logger.getLogger(PeerApp.class.getName());

    private PeerApp() {}

    public static void main (String[] args) {
        String configFile = "configuration.properties";
        if (args.length > 0) {
            configFile = args[0];
        }

        try {
            Configuration.load(configFile);

            // We don't want to show all the detailed log information unless we're set to debug mode.
            if (Configuration.getMode().equals("debug")) {
                System.setProperty("java.util.logging.SimpleFormatter.format",
                        "[%1$tc] %2$s %4$s: %5$s%n");

                Logger.getLogger("").setLevel(Level.FINE);
                Logger.getLogger("").getHandlers()[0].setLevel(Level.FINE);
            } else {
                System.setProperty("java.util.logging.SimpleFormatter.format",
                        "[%1$tc] %5$s%n");
            }

            log.info("BitBox Peer starting...");

            PeerServer.initialise();
        } catch (Exception e) {
            log.severe("failed initialising server");
            e.printStackTrace();
        }
    }
}
