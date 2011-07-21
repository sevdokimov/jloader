package com.ess.jloader.packer;

/**
 * @author Sergey Evdokimov
 */
public class Config {

    private boolean removeSourceDebugExtensionAttribute;
    private boolean removeInvisibleAnnotation;

    public boolean isRemoveSourceDebugExtensionAttribute() {
        return removeSourceDebugExtensionAttribute;
    }

    public void setRemoveSourceDebugExtensionAttribute(boolean removeSourceDebugExtensionAttribute) {
        this.removeSourceDebugExtensionAttribute = removeSourceDebugExtensionAttribute;
    }

    public boolean isRemoveInvisibleAnnotation() {
        return removeInvisibleAnnotation;
    }

    public void setRemoveInvisibleAnnotation(boolean removeInvisibleAnnotation) {
        this.removeInvisibleAnnotation = removeInvisibleAnnotation;
    }
}
