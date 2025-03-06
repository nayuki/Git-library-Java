/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;


/**
 * An immutable 160-bit, 40-hex-digit, 20-byte SHA-1 hash.
 * Its subclasses make the return type of the {@link #read()} method more convenient.
 * To enforce immutability, subclasses cannot be defined outside this package.
 * @see BlobId
 * @see TreeId
 * @see CommitId
 * @see TagId
 * @see GitObject
 */
public class ObjectId implements Comparable<ObjectId> {
	
	/*---- Public constants ----*/
	
	/**
	 * The number of bytes in a SHA-1 hash, which is defined to be 20.
	 * @see #getBytes()
	 */
	public static final int NUM_BYTES = 20;
	
	/**
	 * The number of hexadecimal digits in a SHA-1 hash, which is defined to be 40.
	 * @see #toHexadecimal()
	 */
	public static final int NUM_HEX_DIGITS = NUM_BYTES * 2;
	
	
	
	/*---- Fields ----*/
	
	// Not null, and always length 20 (NUM_BYTES).
	private final byte[] bytes;
	
	
	
	/*---- Constructors ----*/
	
	// Note: Constructors are package-private to prevent foreign subclasses.
	
	/**
	 * Constructs an object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string (not {@code null})
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	ObjectId(String hexStr) {
		this(HEX_FORMAT.parseHex(hexStr));
	}
	
	
	/**
	 * Constructs an object ID from the specified 20-byte array.
	 * @param bytes the byte array (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if the array isn't length 20
	 */
	ObjectId(byte[] bytes) {
		this(checkHashLength(bytes), 0);
	}
	
	
	/**
	 * Constructs an object ID from 20 bytes in the
	 * specified array starting at the specified offset.
	 * @param bytes the byte array (not {@code null})
	 * @param off the offset to start at
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at the offset
	 */
	ObjectId(byte[] bytes, int off) {
		Objects.requireNonNull(bytes);
		Objects.checkFromIndexSize(off, NUM_BYTES, bytes.length);
		this.bytes = Arrays.copyOfRange(bytes, off, off + NUM_BYTES);
	}
	
	
	/* Private helper methods and constants for constructors */
	
	private static byte[] checkHashLength(byte[] bytes) {
		if (bytes.length != NUM_BYTES)
			throw new IllegalArgumentException("Invalid array length");
		return bytes;
	}
	
	
	private static final HexFormat HEX_FORMAT = HexFormat.of();
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the hash byte at the specified index, requiring 0 &le; index &lt; 20.
	 * @param index the byte index to read from
	 * @return the hash byte at the index
	 * @throws IndexOutOfBoundsException if the byte index is out of range
	 * @see #getBytes()
	 */
	public final byte getByte(int index) {
		return bytes[index];
	}
	
	
	/**
	 * Returns a new copy of the array of hash bytes.
	 * @return an array of hash bytes (not {@code null})
	 * @see #getByte()
	 */
	public final byte[] getBytes() {
		return bytes.clone();
	}
	
	
	/**
	 * Returns the {@link #NUM_HEX_DIGITS}-character lowercase hexadecimal string
	 * representation of this hash. For example, "0123456789abcdef0123456789abcdef01234567".
	 * @return the 40-lowercase-digit hexadecimal string representing this hash (not {@code null})
	 */
	public final String toHexadecimal() {
		return HEX_FORMAT.formatHex(this.bytes);
	}
	
	
	/**
	 * Reads the object data for this object ID from the specified repository.
	 * Note that subclasses of {@code ObjectId} will override this method
	 * and specify a subclass of {@code GitObject} as the return type, for convenience.
	 * @param repo the repository to read from (not {@code null})
	 * @return the object data (not {@code null})
	 * @throws IllegalArgumentException if no object with the ID was found in the repository
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public GitObject read(Repository repo) throws IOException {
		Objects.requireNonNull(repo);
		return repo.readObject(this);
	}
	
	
	/**
	 * Tests whether the specified object is an {@code ObjectId} with the same hash bytes. Both sides
	 * of the comparison are treated as a {@code ObjectId} and the exact subclass is irrelevant.
	 * @param obj the object to test equality with
	 * @return {@code true} if and only if the given object is an {@code
	 * ObjectId} (or subclass) with the same array of hash byte values
	 */
	@Override public final boolean equals(Object obj) {
		return obj instanceof ObjectId other
			&& Arrays.equals(bytes, other.bytes);
	}
	
	
	/**
	 * Returns the hash code of this object. The formula is subject to change.
	 * @code the hash code of this object
	 */
	@Override public final int hashCode() {
		return bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24);
	}
	
	
	/**
	 * Compares this hash to the specified hash using the standard order.
	 * Both arrays are treated as unsigned bytes and compared lexicographically.
	 * @param other the object ID to compare to
	 * @return a negative number if {@code this < other}, zero if
	 * {@code this == other}, or a positive number if {@code this > other}
	 */
	@Override public final int compareTo(ObjectId other) {
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] != other.bytes[i])
				return Byte.toUnsignedInt(bytes[i])	- Byte.toUnsignedInt(other.bytes[i]);
		}
		return 0;
	}
	
	
	/**
	 * Returns a string representation of this object ID. The format is subject to change.
	 * @return a string representation of this object ID
	 * @see #toHexadecimal()
	 */
	@Override public String toString() {
		return String.format(getClass().getSimpleName() + "(%s)", toHexadecimal());
	}
	
}
