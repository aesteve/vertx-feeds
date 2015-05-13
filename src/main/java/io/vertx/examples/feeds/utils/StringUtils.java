package io.vertx.examples.feeds.utils;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class StringUtils {

    private MessageDigest sha256;
    private SecureRandom secureRandom;

    public StringUtils() {
        secureRandom = new SecureRandom();
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException nsae) {
            nsae.printStackTrace();
            throw new RuntimeException("No such algorithm : SHA-256. Something's really wrong in source code.");
        }
    }

    public String hash256(String str) {
        sha256.reset();
        try {
            sha256.update(str.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
            throw new RuntimeException("UTF-8 is not supported by this platform");
        }
        byte[] digest = sha256.digest();
        return toHexString(digest);
    }

    public String toHexString(byte[] bytes) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xff & bytes[i]);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public String generateToken() {
        return new BigInteger(130, secureRandom).toString(32);
    }
}
