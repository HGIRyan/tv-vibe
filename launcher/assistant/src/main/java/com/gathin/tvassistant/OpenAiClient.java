package com.gathin.tvassistant;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;

final class OpenAiClient {
    private final String key;
    private final AppCatalog catalog;
    OpenAiClient(String key, AppCatalog catalog) { this.key = key; this.catalog = catalog; }

    String transcribe(byte[] wav) throws Exception {
        String boundary = "hey-tv-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "model", "gpt-transcribe");
        field(body, boundary, "response_format", "json");
        field(body, boundary, "prompt", "TV voice controls. The wake phrase is Hey TV. "
            + "Available app names include " + catalog.names() + ". "
            + "Transcribe only speech actually present; do not add a wake phrase or a command.");
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"command.wav\"\r\n"
            + "Content-Type: audio/wav\r\n\r\n");
        body.write(wav);
        write(body, "\r\n--" + boundary + "--\r\n");
        return request("audio/transcriptions", "multipart/form-data; boundary=" + boundary, body.toByteArray())
            .getString("text");
    }

    CommandPolicy.Selection decide(String command) throws Exception {
        return CommandPolicy.parseDecision(decisionResponse(command), catalog);
    }

    JSONObject decisionResponse(String command) throws Exception {
        return request("decisions", "application/json",
            CommandPolicy.decisionRequest(command, catalog).toString().getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject request(String path, String contentType, byte[] body) throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL("https://api.openai.com/v1/" + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + key);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setFixedLengthStreamingMode(body.length);
        try {
            try (var output = connection.getOutputStream()) { output.write(body); }
            int status = connection.getResponseCode();
            // Never log API bodies, transcripts, or headers (including error responses).
            if (status < 200 || status >= 300) throw new IllegalStateException("OpenAI HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (bytes.size() + count > 1024 * 1024) throw new IllegalStateException("Response too large");
                    bytes.write(chunk, 0, count);
                }
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    private static void field(ByteArrayOutputStream body, String boundary, String name, String value) {
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }
    private static void write(ByteArrayOutputStream body, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        body.write(bytes, 0, bytes.length);
    }
}
