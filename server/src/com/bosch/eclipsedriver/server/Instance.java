package com.bosch.eclipsedriver.server;

/** One connected Eclipse instance. */
public record Instance(String id, long pid, int port, String token, String install, String workspace, boolean launchedByDriver) {
}
