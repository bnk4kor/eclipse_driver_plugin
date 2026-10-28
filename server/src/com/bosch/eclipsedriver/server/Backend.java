package com.bosch.eclipsedriver.server;

import java.util.Map;

/** Executes a validated catalog call on the connected target. */
public interface Backend {
    Object call(String tool, Map<String, Object> args, String instance);
}
