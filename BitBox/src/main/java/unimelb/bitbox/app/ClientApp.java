package unimelb.bitbox.app;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import unimelb.bitbox.client.ClientCommand;
import unimelb.bitbox.util.network.Conversion;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import java.io.IOException;
import java.net.Socket;

/**
 * @author Eleanor McMurtry
 */
public class ClientApp {
    private static final int DEFAULT_PORT = 21979;

    public static void main(String[] args) throws IOException {
        Options options = new Options();
        options.addOption("p", "port", true, "port of the server to connect to");
        options.addOption("c", "config-file", true, "config file to use");
        options.addOption("j", "java", true, "path to Java");
        options.addOption("b", "bitbox", true, "path to Bitbox");

        Socket socket = null;

        try {
            var parsedArgs = new DefaultParser().parse(options, args);
            if (parsedArgs.getArgs().length < 1) {
                throw new ParseException("missing command");
            }
            String commandStr = parsedArgs.getArgs()[0].toUpperCase();
            if (!ClientCommand.isValid(commandStr)) {
                throw new ParseException("invalid command: " + commandStr);
            }
            var command = ClientCommand.valueOf(commandStr);

            if (command == ClientCommand.START) {
                /*String configFile = parsedArgs.getOptionValue('c', "configuration.properties");
                String javaCommand = parsedArgs.getOptionValue('j', "java");
                String bitbox = parsedArgs.getOptionValue('b');
                if (bitbox == null) {
                    throw new ParseException("Missing Bitbox path");
                }
                String toRun = "start \"\" \"" + javaCommand + "\" -cp " + bitbox + " unimelb.bitbox.app.PeerApp " + configFile;
                System.out.println(toRun);

                var proc = new ProcessBuilder("cmd", "/k", toRun).start();
                var in = proc.getInputStream();
                int ch;
                while ((ch = in.read()) != -1) {
                    System.out.print((char) ch);
                }
                proc.waitFor();*/
                System.out.println("Not yet implemented. On Windows, try:");
                System.out.println("start \"\" javaw -cp BITBOX unimelb.bitbox.app.PeerApp configuration.properties");
            } else {
                String portString = parsedArgs.getOptionValue('p', Integer.toString(DEFAULT_PORT));
                if (!Conversion.isInteger(portString)) {
                    throw new ParseException("invalid port: " + portString);
                }

                int port = Integer.parseInt(portString);
                if (port < 0 || port >= Short.MAX_VALUE * 2) {
                    throw new ParseException("invalid port: " + port);
                }

                try {
                    socket = new Socket("localhost", port);
                } catch (IOException e) {
                    System.out.println("error connecting to server: " + e.getMessage());
                    return;
                }

                try (var out = socket.getOutputStream();
                     var in = socket.getInputStream()) {
                    out.write((command + "\n").getBytes());
                    if (command != ClientCommand.STOP) {
                        try {
                            var result = JSONDocument.parse(new String(in.readAllBytes())).get();
                            if (!result.getBoolean("status").get()) {
                                System.out.println("error:");
                                System.out.println(result.getString("message"));
                            } else {
                                if (command == ClientCommand.PING) {
                                    System.out.println("OK");
                                } else {
                                    var peers = result.getStringArray("peers").get();
                                    System.out.println("Peers:");
                                    for (var peer : peers) {
                                        System.out.println("\t" + peer);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            System.out.println("malformed response: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (ParseException e) {
            System.out.println("Incorrect arguments: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
