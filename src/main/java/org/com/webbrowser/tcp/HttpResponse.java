package org.com.webbrowser.tcp;

import java.util.Map;

public class HttpResponse {
    private Map<String, String> headers;
    private String body;
    private String statusLine;

    public HttpResponse(String statusLine, Map<String, String> headers, String body) {
        this.statusLine = statusLine;
        this.headers = headers;
        this.body = body;
    }

    public String getStatusLine() {
        return statusLine;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
