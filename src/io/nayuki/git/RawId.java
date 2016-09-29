/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


/**
 * An immutable 160-bit (20-byte) SHA-1 hash. The object type is unknown,
 * and can be either a new type or one of the known types (commit, tree, blob, etc.)
 */
public final class RawId extends ObjectId {
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a raw object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	public RawId(String hexStr) {
		super(hexStr);
	}
	
	
	/**
	 * Constructs a raw object ID from the specified 20-byte array.
	 * @param bytes the byte array
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if array isn't length 20
	 */
	public RawId(byte[] bytes) {
		super(bytes);
	}
	
	
	/**
	 * Constructs a raw object ID from 20 bytes in the specified array starting at the specified offset.
	 * @param bytes the byte array
	 * @param off the offset to start at
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at that offset
	 */
	public RawId(byte[] bytes, int off) {
		super(bytes, off);
	}
	
}
