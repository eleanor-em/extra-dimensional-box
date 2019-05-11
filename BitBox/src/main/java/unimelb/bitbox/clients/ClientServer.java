package unimelb.bitbox.clients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class ClientServer {

    private int portNumber;

    public ClientServer(int portNumber) throws IOException{
        this.portNumber = portNumber;
        acceptConnections();
    }

    public void acceptConnections(){
        try (
                ServerSocket serverSocket = new ServerSocket(portNumber);
        ) {
            while (true){
                Socket clientSocket = serverSocket.accept();
                PrintWriter out =
                        new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));

                String inputLine, outputLine;

                // Initiate conversation with client
                ClientProtocol kkp = new ClientProtocol();
                outputLine = kkp.processInput(null);
                out.println(outputLine);

                while ((inputLine = in.readLine()) != null) {
                    outputLine = kkp.processInput(inputLine);
                    out.println(outputLine);
                    if (outputLine.equals("Bye."))
                        break;
                }
            }
        } catch (IOException e) {
            System.out.println("Exception caught when trying to listen on port "
                    + portNumber + " or listening for a connection");
            System.out.println(e.getMessage());
        }
    }
}
