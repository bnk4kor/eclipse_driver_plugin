package com.bosch.eclipsedriver.server;

import java.util.ArrayList;
import java.util.List;

/** Builds the driver's own launcher ini from the install's ini: own configuration area, optional workspace, token and ready file. */
public final class LauncherIni {
    private LauncherIni() {
    }

    public static List<String> build(List<String> original, String configDir, String dataDir, String token, String readyFile) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < original.size(); i++) {
            String line = original.get(i).trim();
            if ((configDir != null && line.equals("-configuration")) || (dataDir != null && line.equals("-data"))) {
                i++;
                continue;
            }
            out.add(original.get(i));
        }
        int at = indexOfFirst(out, "-vm", "-vmargs");
        // a null configDir keeps the install's own configuration area (install mode)
        List<String> own = new ArrayList<>();
        if (configDir != null) {
            own.addAll(List.of("-configuration", configDir));
        }
        if (dataDir != null) {
            own.add("-data");
            own.add(dataDir);
        }
        out.addAll(at < 0 ? out.size() : at, own);

        List<String> props = List.of("-Declipse.driver.token=" + token, "-Declipse.driver.ready=" + readyFile);
        int vmargs = indexOfFirst(out, "-vmargs");
        if (vmargs < 0) {
            out.add("-vmargs");
            out.addAll(props);
        } else {
            out.addAll(vmargs + 1, props);
        }
        return out;
    }

    private static int indexOfFirst(List<String> lines, String... keys) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            for (String k : keys) {
                if (line.equals(k)) {
                    return i;
                }
            }
        }
        return -1;
    }
}
