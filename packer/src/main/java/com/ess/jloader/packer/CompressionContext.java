package com.ess.jloader.packer;

import org.objectweb.asm.ClassReader;

import java.util.Collection;

/**
 * @author Sergey Evdokimov
 */
public class CompressionContext {

    private LiteralsCache literalsCache;
    private VersionCache versionCache;

    public CompressionContext(Collection<ClassReader> classes) {
        literalsCache = new LiteralsCache(classes);
        versionCache = new VersionCache(classes);
    }

    public LiteralsCache getLiteralsCache() {
        return literalsCache;
    }

    public VersionCache getVersionCache() {
        return versionCache;
    }
}
