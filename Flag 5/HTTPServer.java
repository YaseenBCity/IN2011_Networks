import java.io.*;
import java.net.*;

public class HTTPServer {

    public static void main(String[] args) throws IOException {

        int port = 18080;

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
