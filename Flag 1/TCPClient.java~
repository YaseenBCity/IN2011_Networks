import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient {

    public TCPClient() {}

    public static void main(String[] args) throws IOException {

        // IP Addresses
        String IPAddressString = "10.200.51.18";
        InetAddress host = InetAddress.getByName(IPAddressString);

        // Port 2011
        int port = 2011;

        // This is where we create a socket object
        // That creates the TCP conection
        System.out.println("TCPClient connecting to " + host.toString() + ":" + port);
        Socket clientSocket = new Socket(host, port);

        // Reader
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

        // We can read what the server has said
        String response = reader.readLine();
        System.out.println("The server said : " + response);

        // Close down the connection
        clientSocket.close();
    }
}
