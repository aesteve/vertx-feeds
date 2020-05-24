package io.vertx.examples.feeds.utils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class StringUtils {

	private MessageDigest sha256;
	private final SecureRandom secureRandom;

	public StringUtils() {
		secureRandom = new SecureRandom();
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException nsae) {
			throw new RuntimeException("No such algorithm : SHA-256. Something's really wrong in source code.", nsae);
		}
	}

	public String hash256(String str) {
		sha256.reset();
        sha256.update(str.getBytes(StandardCharsets.UTF_8));
        byte[] digest = sha256.digest();
		return toHexString(digest);
	}

	private String toHexString(byte[] bytes) {
		StringBuilder hexString = new StringBuilder();
		for (byte aByte : bytes) {
			String hex = Integer.toHexString(0xff & aByte);
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
