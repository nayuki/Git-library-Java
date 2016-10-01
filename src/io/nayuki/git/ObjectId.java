/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;


/**
 * An immutable 160-bit (20-byte) SHA-1 hash.
 * It has subclasses to allow the {@link #read()} method to be convenient.
 * @see RawId
 * @see BlobId
 * @see TreeId
 * @see CommitId
 * @see TagId
 */
public abstract class ObjectId implements Comparable<ObjectId> {
	
	/*---- Public constants ----*/
	
	/**
	 * The number of bytes in a SHA-1 hash, which is defined to be 20.
	 */
	public static final int NUM_BYTES = 20;
	
	
	
	/*---- Fields ----*/
	
	/**
	 * The 40-character (NUM_BYTES * 2) hexadecimal representation of the hash, in lowercase.
	 * For example, "0123456789abcdef0123456789abcdef01234567".
	 */
	public final String hexString;
	
	// Not null, and always length 20 (NUM_BYTES).
	private final byte[] bytes;
	
	
	
	/*---- Constructors ----*/
	
	// Note: Constructors are package-private to prevent foreign subclasses, which could break immutability.
	
	/**
	 * Constructs an object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string (not {@code null})
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	ObjectId(String hexStr) {
		this(hexHashToBytes(hexStr));
	}
	
	
	/**
	 * Constructs an object ID from the specified 20-byte array.
	 * @param bytes the byte array (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if array isn't length 20
	 */
	ObjectId(byte[] bytes) {
		this(checkHashLength(bytes), 0);
	}
	
	
	/**
	 * Constructs an object ID from 20 bytes in the specified array starting at the specified offset.
	 * @param bytes the byte array (not {@code null})
	 * @param off the offset to start at
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at that offset
	 */
	ObjectId(byte[] bytes, int off) {
		if (bytes == null)
			throw new NullPointerException();
		if (off < 0 || bytes.length - off < NUM_BYTES)
			throw new IndexOutOfBoundsException();
		
		this.bytes = Arrays.copyOfRange(bytes, off, off + NUM_BYTES);
		StringBuilder sb = new StringBuilder();
		for (byte b : this.bytes)
			sb.append(HEX_DIGITS[(b >>> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
		hexString = sb.toString();
	}
	
	
	/* Private helper methods and constants for constructors */
	
	private static byte[] hexHashToBytes(String str) {
		if (str == null)
			throw new NullPointerException();
		if (!HEX_STRING_PATTERN.matcher(str).matches())
			throw new IllegalArgumentException("Invalid hexadecimal hash");
		
		byte[] result = new byte[NUM_BYTES];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte)Integer.parseInt(str.substring(i * 2, (i + 1) * 2), 16);
		return result;
	}
	
	
	private static byte[] checkHashLength(byte[] bytes) {
		if (bytes.length != NUM_BYTES)
			throw new IllegalArgumentException("Invalid array length");
		return bytes;
	}
	
	
	private static final Pattern HEX_STRING_PATTERN = Pattern.compile("[0-9a-fA-F]{" + (NUM_BYTES * 2) + "}");
	
	private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the hash byte at the specified index, requiring 0 &le; index &lt; 20.
	 * @param index the byte index to read from
	 * @return the hash byte at the index
	 * @throws IndexOutOfBoundsException if the byte index is out of range
	 */
	public final byte getByte(int index) {
		if (index < 0 || index >= NUM_BYTES)
			throw new IndexOutOfBoundsException();
		return bytes[index];
	}
	
	
	/**
	 * Returns a new copy of the array of hash bytes.
	 * @return an array of hash bytes (not {@code null})
	 */
	public final byte[] getBytes() {
		return bytes.clone();
	}
	
	
	/**
	 * Reads the object data for this object ID from the specified repository.
	 * Note that subclasses of {@code ObjectId} will override this method
	 * and specify a subclass of {@code GitObject} as the return type, for convenience.
	 * @param repo the repository to read from (not {@code null})
	 * @return the object data, or {@code null} if not found in the repo
	 * @throws IOException if an I/O exception occurred
	 * @throws DataFormatException if malformed data was encountered during reading
	 */
	public GitObject read(Repository repo) throws IOException, DataFormatException {
		if (repo == null)
			throw new NullPointerException();
		return repo.readObject(this);
	}
	
	
	/**
	 * Tests whether the specified object is an {@code ObjectId} with the same hash bytes.
	 * @param obj the object to test equality with
	 * @return {@code true} if and only if the given object is
	 * an {@code ObjectId} with the same array of hash byte values
	 */
	public final boolean equals(Object obj) {
		return (obj instanceof ObjectId) && Arrays.equals(bytes, ((ObjectId)obj).bytes);
	}
	
	
	/**
	 * Returns the hash code of this object. The formula is subject to change.
	 * @code the hash code of this object
	 */
	public final int hashCode() {
		return bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24);
	}
	
	
	/**
	 * Compares this hash to the given hash in standard big-endian order.
	 * @param other the object to compare to
	 * @return a negative number if {@code this < other}, zero if
	 * {@code this == other}, or a positive number if {@code this > other}
	 */
	public final int compareTo(ObjectId other) {
		return hexString.compareTo(other.hexString);
	}
	
	
	/**
	 * Returns a string representation of this object ID. The format is subject to change.
	 * @return a string representation of this object ID
	 */
	public String toString() {
		return String.format(getClass().getSimpleName() + "(%s)", hexString);
	}
	
}
