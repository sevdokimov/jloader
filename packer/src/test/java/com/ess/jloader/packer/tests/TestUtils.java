package com.ess.jloader.packer.tests;

import java.io.File;

/**
 * @author Sergey Evdokimov
 */
public class TestUtils {

    public static File getJarByMarker(String marker) {
        String path = Thread.currentThread().getContextClassLoader().getResource(marker).getPath();
        int idx = path.lastIndexOf("!/");
        assert idx > 0;

        path = path.substring(0, idx);
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }

        return new File(path);
    }

    public static File createTmpPAckFile(String prefix) {
        return new File(System.getProperty("java.io.tmpdir") + "/" + prefix + ".j");
    }

}
