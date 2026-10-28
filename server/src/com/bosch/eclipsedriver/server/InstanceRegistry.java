package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.util.LinkedHashMap;
import java.util.Map;

/** Connected instances by id. The first connected instance is the default. */
public final class InstanceRegistry {
    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private int counter;

    public synchronized String nextId() {
        return "i" + (++counter);
    }

    public synchronized void add(Instance instance) {
        instances.put(instance.id(), instance);
    }

    public synchronized Instance remove(String id) {
        return instances.remove(id);
    }

    public synchronized java.util.List<Instance> all() {
        return new java.util.ArrayList<>(instances.values());
    }

    public synchronized Instance resolve(String id) {
        if (instances.isEmpty()) {
            throw new ToolError(ErrorCode.NOT_CONNECTED, "no instance is connected")
                    .hint("call connect first (see describe_tool connect)");
        }
        if (id == null) {
            return instances.values().iterator().next();
        }
        Instance found = instances.get(id);
        if (found == null) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no connected instance '" + id + "'")
                    .hint("connected instances: " + instances.keySet());
        }
        return found;
    }
}
