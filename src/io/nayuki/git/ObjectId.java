/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;


// An immutable 160-bit (20-byte) SHA-1 hash
public abstract class ObjectId implements Comparable<ObjectId> {
	
	public static final int NUM_BYTES = 20;
	
	
	public final String hexString;  // 40 characters (NUM_BYTES * 2) of 0-9 and lowercase a-f
	private final byte[] bytes;     // 20 bytes (NUM_BYTES)
	
	private final WeakReference<Repository> sourceRepo;
	
	
	
	// Accepts uppercase and lowercase
	ObjectId(String hexStr, WeakReference<Repository> srcRepo) {
		if (hexStr == null)
			throw new NullPointerException();
		if (!HEX_STRING_PATTERN.matcher(hexStr).matches())
			throw new IllegalArgumentException();
		
		bytes = new byte[NUM_BYTES];
		for (int i = 0; i < bytes.length; i++)
			bytes[i] = (byte)Integer.parseInt(hexStr.substring(i * 2, (i + 1) * 2), 16);
		
		char[] chars = hexStr.toCharArray();
		for (int i = 0; i < chars.length; i++) {  // Normalize to lowercase
			if (chars[i] >= 'A' && chars[i] <= 'F')
				chars[i] -= 'A' - 'a';
		}
		hexString = new String(chars);
		sourceRepo = srcRepo;
	}
	
	
	// Array must be 20 bytes long
	ObjectId(byte[] bytes, WeakReference<Repository> srcRepo) {
		if (bytes == null)
			throw new NullPointerException();
		if (bytes.length != NUM_BYTES)
			throw new IllegalArgumentException("Invalid array length");
		
		this.bytes = bytes.clone();
		StringBuilder sb = new StringBuilder();
		for (byte b : this.bytes)
			sb.append(HEX_DIGITS[(b >>> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
		hexString = sb.toString();
		sourceRepo = srcRepo;
	}
	
	
	// Array can be any length, only requiring (off + 20 <= bytes.length)
	ObjectId(byte[] bytes, int off, WeakReference<Repository> srcRepo) {
		if (bytes == null)
			throw new NullPointerException();
		if (off < 0 || bytes.length - off < NUM_BYTES)
			throw new IndexOutOfBoundsException();
		
		this.bytes = Arrays.copyOfRange(bytes, off, off + NUM_BYTES);
		StringBuilder sb = new StringBuilder();
		for (byte b : this.bytes)
			sb.append(HEX_DIGITS[(b >>> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
		hexString = sb.toString();
		sourceRepo = srcRepo;
	}
	
	
	
	public byte getByte(int index) {
		if (index < 0 || index >= NUM_BYTES)
			throw new IndexOutOfBoundsException();
		return bytes[index];
	}
	
	
	public byte[] getBytes() {
		return bytes.clone();
	}
	
	
	public GitObject read() throws IOException, DataFormatException {
		return getSourceRepository().readObject(this);
	}
	
	
	public Repository getSourceRepository() {
		if (sourceRepo == null)
			throw new IllegalStateException("Source repository set to null");
		Repository result = sourceRepo.get();
		if (result == null)
			throw new IllegalStateException("Weak reference to repository expired");
		return result;
	}
	
	
	public boolean equals(Object obj) {
		if (!(obj instanceof ObjectId))
			return false;
		return Arrays.equals(bytes, ((ObjectId)obj).bytes);
	}
	
	
	public int hashCode() {
		return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
	}
	
	
	public int compareTo(ObjectId other) {
		return hexString.compareTo(other.hexString);
	}
	
	
	public String toString() {
		return String.format("ObjectId(value=%s)", hexString);
	}
	
	
	
	private static final Pattern HEX_STRING_PATTERN = Pattern.compile("[0-9a-fA-F]{" + (NUM_BYTES * 2) + "}");
	private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
	
}
