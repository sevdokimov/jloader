package com.ess.jloader.packer.tests;

import com.ess.jloader.packer.JarPacker;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class PackFreeMarker {

    @Test
    public void packFreeMarker() throws IOException {
        File freeMarkerJar = TestUtils.getJarByMarker("freemarker/core/Assignment.class");

        File packedFreeMarker = TestUtils.createTmpPAckFile("packedFreeMarker");
        JarPacker.pack(freeMarkerJar, packedFreeMarker);

        System.out.printf("Src: %d, Result: %d,  (%d%%)", freeMarkerJar.length(), packedFreeMarker.length(), packedFreeMarker.length() * 100 / freeMarkerJar.length());
    }

}
