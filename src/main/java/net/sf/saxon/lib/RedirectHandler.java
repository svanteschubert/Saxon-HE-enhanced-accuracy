package net.sf.saxon.lib;

import net.sf.saxon.trans.XPathException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;

public class RedirectHandler {
    public static URLConnection resolveConnection(URL url) throws IOException {
        HashSet<URI> seen = new HashSet<>();
        int count = 100;

        try {
            URI absoluteURI = url.toURI();

            while (true) {
                if (seen.contains(absoluteURI)) {
                    throw new IOException("Redirect loop on " + absoluteURI);
                }
                if (count <= 0) {
                    throw new IOException("Too many redirects on " + absoluteURI);
                }
                seen.add(absoluteURI);
                count--;

                URLConnection connection = absoluteURI.toURL().openConnection();
                connection.setRequestProperty("Accept-Encoding", "gzip");
                connection.connect();

                if (connection instanceof HttpURLConnection) {
                    HttpURLConnection conn = (HttpURLConnection) connection;
                    if (conn.getResponseCode() >= 300 && conn.getResponseCode() < 400) {
                        String loc = conn.getHeaderField("location");
                        absoluteURI = absoluteURI.resolve(loc);
                    } else {
                        return connection;
                    }
                } else {
                    return connection;
                }
            }
        } catch (URISyntaxException use) {
            throw new IOException("Failed to parse URI " + url);
        }
    }
}
