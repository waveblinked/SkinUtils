package com.alexander.skinutils.skin;

public record SkinData(String value, String signature, long timestamp) {

    public SkinData(String value, String signature) {
        this(value, signature, System.currentTimeMillis());
    }

    public boolean isExpired(long expiryMillis) {
        return System.currentTimeMillis() - timestamp > expiryMillis;
    }
}
