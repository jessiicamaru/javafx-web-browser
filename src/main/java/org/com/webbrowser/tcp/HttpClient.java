package org.com.webbrowser.tcp;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class HttpClient {

    public static HttpResponse fetch(String host, String path) throws IOException {
        try (Socket socket = new Socket(host, 80);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.print("GET " + path + " HTTP/1.1\r\n");
            out.print("Host: " + host + "\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.flush();

            String statusLine = in.readLine();
            if (statusLine == null) {
                throw new IOException("No response from server");
            }

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isEmpty()) break;
                int colon = line.indexOf(":");
                if (colon > 0) {
                    String key = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    headers.put(key, value);
                }
            }

            StringBuilder bodyBuilder = new StringBuilder();
            while ((line = in.readLine()) != null) {
                bodyBuilder.append(line).append("\n");
            }

            return new HttpResponse(statusLine, headers, bodyBuilder.toString());
        }
    }

}
