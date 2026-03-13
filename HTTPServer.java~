import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {

    public TCPServer() {}

    public static void main(String[] args) throws IOException {import java.io.*;
import java.net.*;

public class TCPServer {

    public static void main(String[] args) throws IOException {

        int port = 8080;

        System.out.println("Opening server on port " + port);

        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {

            System.out.println("Waiting for client...");
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected!");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            Writer writer = new OutputStreamWriter(
                    clientSocket.getOutputStream());

            // Read first line of HTTP request
            String requestLine = reader.readLine();
            System.out.println("Request: " + requestLine);

            if (requestLine == null) {
                clientSocket.close();
                continue;
            }

            String[] parts = requestLine.split(" ");

            String method = parts[0];
            String path = parts[1];

            // Read remaining headers
            String line;
            while (!(line = reader.readLine()).isEmpty()) {
                System.out.println("Header: " + line);
            }

            // Check method
            if (!method.equals("GET")) {

                writer.write("HTTP/1.1 400 Bad Request\r\n");
                writer.write("Content-Type: text/html\r\n\r\n");
                writer.write("<html><body><h1>400 Bad Request</h1></body></html>");

            }
            else if (path.equals("/") || path.equals("/index.html")) {

                writer.write("HTTP/1.1 200 OK\r\n");
                writer.write("Content-Type: text/html\r\n\r\n");

                writer.write("<html>");
                writer.write("<body>");
                writer.write("<h1>Hello from my Java Web Server</h1>");
                writer.write("</body>");
                writer.write("</html>");

            }
            else {

                writer.write("HTTP/1.1 400 Bad Request\r\n");
                writer.write("Content-Type: text/html\r\n\r\n");

                writer.write("<html>");
                writer.write("<body>");
                writer.write("<h1>400 Page Not Found</h1>");
                writer.write("</body>");
                writer.write("</html>");
            }

            writer.flush();

            clientSocket.close();
        }
    }
}

	// Port numbers will be discussed in detail in lecture 5
	int port = 4567;

	// The server side is slightly more complex
	// First we have to create a ServerSocket
        System.out.println("Opening the server socket on port " + port);
        ServerSocket serverSocket = new ServerSocket(port);

	// The ServerSocket listens and then creates as Socket object
	// for each incoming connection.
        System.out.println("Server waiting for client...");
        Socket clientSocket = serverSocket.accept();
        System.out.println("Client connected!");
	
	// Like files, we use readers and writers for convenience
	BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
	Writer writer = new OutputStreamWriter(clientSocket.getOutputStream());

	// We can read what the client has said
	String message = reader.readLine();
	System.out.println("The client said : " + message);

	// Sending a message to the client at the other end of the socket
	System.out.println("Sending a message to the client");
	writer.write("Nice to meet you\n");
	writer.flush();
	// To make better use of bandwidth, messages are not sent
	// until the flush method is used

	// Close down the connection
	clientSocket.close();
    }
}
