package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calls the agent's RPC endpoint (POST /rpc with the session token). */
public final class AgentClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient http = HttpClient.newHttpClient();

    @SuppressWarnings("unchecked")
    public Object call(Instance instance, String method, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method", method);
        body.put("params", params);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + instance.port() + "/rpc"))
                .timeout(REQUEST_TIMEOUT)
                .header("X-Driver-Token", instance.token())
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ToolError(ErrorCode.NOT_CONNECTED, "agent of instance " + instance.id() + " is not reachable: " + e.getMessage())
                    .hint("the instance may have exited; call list_instances or connect again");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolError(ErrorCode.INTERNAL_ERROR, "interrupted while calling the agent");
        }
        Map<String, Object> reply;
        try {
            reply = (Map<String, Object>) Json.parse(response.body());
        } catch (IllegalArgumentException | ClassCastException e) {
            throw new ToolError(ErrorCode.INTERNAL_ERROR, "agent sent an unreadable reply (HTTP " + response.statusCode() + ")");
        }
        if (Boolean.TRUE.equals(reply.get("ok"))) {
            return reply.get("result");
        }
        throw toToolError((Map<String, Object>) reply.get("error"));
    }

    private static ToolError toToolError(Map<String, Object> error) {
        ToolError e = new ToolError(String.valueOf(error.get("code")), String.valueOf(error.get("message")));
        for (Map.Entry<String, Object> entry : error.entrySet()) {
            switch (entry.getKey()) {
                case "code", "message" -> { }
                case "hints" -> {
                    for (Object h : (List<?>) entry.getValue()) {
                        e.hint(String.valueOf(h));
                    }
                }
                default -> e.with(entry.getKey(), entry.getValue());
            }
        }
        return e;
    }
}
