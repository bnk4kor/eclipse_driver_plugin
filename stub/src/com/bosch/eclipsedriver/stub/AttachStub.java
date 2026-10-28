package com.bosch.eclipsedriver.stub;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Java agent loaded by the attach mechanism into a running Eclipse JVM (FR-S1 attach).
 * Agent argument: token|readyFile|agentBundleJar. It sets the agent's enable properties, finds the OSGi system
 * bundle context through EclipseStarter and installs and starts the agent bundle. Failures go to readyFile.error.
 */
public final class AttachStub {
    private static final String SYMBOLIC_NAME = "com.bosch.eclipsedriver.agent";

    private AttachStub() {
    }

    public static void agentmain(String args, Instrumentation inst) {
        String[] parts = args.split("\\|", 3);
        try {
            System.setProperty("eclipse.driver.token", parts[0]);
            System.setProperty("eclipse.driver.ready", parts[1]);
            start(systemBundleContext(inst), parts[2]);
        } catch (Throwable t) {
            try {
                Files.writeString(Path.of(parts[1] + ".error"), String.valueOf(t), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // nothing more to do: the server times out and reports
            }
        }
    }

    private static Object systemBundleContext(Instrumentation inst) throws ReflectiveOperationException {
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().equals("org.eclipse.core.runtime.adaptor.EclipseStarter")) {
                Object ctx = c.getMethod("getSystemBundleContext").invoke(null);
                if (ctx != null) {
                    return ctx;
                }
            }
        }
        throw new IllegalStateException("no running OSGi framework found (EclipseStarter not loaded)");
    }

    private static void start(Object context, String agentJar) throws ReflectiveOperationException {
        ClassLoader loader = context.getClass().getClassLoader();
        Class<?> ctxClass = Class.forName("org.osgi.framework.BundleContext", false, loader);
        Class<?> bundleClass = Class.forName("org.osgi.framework.Bundle", false, loader);
        Method getName = bundleClass.getMethod("getSymbolicName");
        Object bundle = null;
        for (Object b : (Object[]) ctxClass.getMethod("getBundles").invoke(context)) {
            if (SYMBOLIC_NAME.equals(getName.invoke(b))) {
                bundle = b;
            }
        }
        if (bundle == null) {
            bundle = ctxClass.getMethod("installBundle", String.class).invoke(context, "reference:" + Path.of(agentJar).toUri());
        } else {
            // an installed but dormant agent must restart so its activator sees the enable properties
            bundleClass.getMethod("stop").invoke(bundle);
        }
        bundleClass.getMethod("start").invoke(bundle);
    }
}
