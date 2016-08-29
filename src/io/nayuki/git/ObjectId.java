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


/**
 * A 160-bit (20-byte) SHA-1 hash with extra information. It has subtypes and an associated
 * repository to allow the {@link #read()} method to be convenient. The hash value is immutable,
 * but the weak reference and underlying repository object can change states.
 */
public abstract class ObjectId implements Comparable<ObjectId> {
	
	/*---- Public static constants ----*/
	
	public static final int NUM_BYTES = 20;
	
	
	
	/*---- Fields ----*/
	
	/** The 40-character (NUM_BYTES * 2) hexadecimal representation of the hash, in lowercase. */
	public final String hexString;
	
	// Not null, and always length 20 (NUM_BYTES).
	private final byte[] bytes;
	
	// Can be null.
	private final WeakReference<Repository> sourceRepo;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs an object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string
	 * @param srcRepo the repository to read from
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
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
	
	
	/**
	 * Constructs an object ID from the specified 20-byte array.
	 * @param bytes the byte array
	 * @param srcRepo the repository to read from
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if array isn't length 20
	 */
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
	
	
	/**
	 * Constructs an object ID from 20 bytes in the specified array starting at the specified offset.
	 * @param bytes the byte array
	 * @param off the offset to start at
	 * @param srcRepo the repository to read from
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at that offset
	 */
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
	
	
	/* Helper definitions for constructors */
	
	private static final Pattern HEX_STRING_PATTERN = Pattern.compile("[0-9a-fA-F]{" + (NUM_BYTES * 2) + "}");
	
	private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the hash byte at the specified index, requiring 0 &le; index &lt; 20.
	 * @param index the byte index to read from
	 * @return the hash byte at the index
	 * @throws IndexOutOfBoundsException if the byte index is out of range
	 */
	public byte getByte(int index) {
		if (index < 0 || index >= NUM_BYTES)
			throw new IndexOutOfBoundsException();
		return bytes[index];
	}
	
	
	/**
	 * Returns a new copy of the array of hash bytes.
	 * @return an array of hash bytes
	 */
	public byte[] getBytes() {
		return bytes.clone();
	}
	
	
	/**
	 * Either returns the associated repository (not {@code null}) or throws an exception.
	 * @return the associated repository (not {@code null})
	 * @throws IllegalStateException if the reference object is
	 * itself {@code null} or the weak reference has expired
	 */
	public Repository getSourceRepository() {
		if (sourceRepo == null)
			throw new IllegalStateException("Source repository set to null");
		Repository result = sourceRepo.get();
		if (result == null)
			throw new IllegalStateException("Weak reference to repository expired");
		return result;
	}
	
	
	/**
	 * Reads the object data for this object ID from the associated repository.
	 * @return the object data (not {@code null})
	 * @throws IOException if an I/O exception occurred
	 * @throws DataFormatException if malformed data was encountered during reading
	 */
	public GitObject read() throws IOException, DataFormatException {
		return getSourceRepository().readObject(this);
	}
	
	
	/**
	 * Tests whether the specified object is an {@code ObjectId} with the same hash bytes.
	 * @param obj the object to test equality with
	 * @return {@code true} if and only if the given object is
	 * an {@code ObjectId} with the same array of hash byte values
	 */
	public boolean equals(Object obj) {
		if (!(obj instanceof ObjectId))
			return false;
		return Arrays.equals(bytes, ((ObjectId)obj).bytes);
	}
	
	
	/**
	 * Returns the hash code of this object. The formula is subject to change.
	 * @code the hash code of this object
	 */
	public int hashCode() {
		return bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24);
	}
	
	
	/**
	 * Compares this hash to the given hash in standard big-endian order.
	 * @param other the object to compare to
	 * @return a negative number if {@code this < other}, zero if
	 * {@code this == other}, or a positive number if {@code this > other}
	 */
	public int compareTo(ObjectId other) {
		return hexString.compareTo(other.hexString);
	}
	
	
	/**
	 * Returns a string representation of this object ID. The format is subject to change.
	 * @return a string representation of this object ID
	 */
	public String toString() {
		return String.format("ObjectId(value=%s)", hexString);
	}
	
}
