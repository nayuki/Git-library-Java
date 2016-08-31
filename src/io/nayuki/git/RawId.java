/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.lang.ref.WeakReference;


/**
 * A 160-bit (20-byte) SHA-1 hash with extra information. The object type is unknown,
 * and can be either a new type or one of the known types (commit, tree, blob, etc.)
 */
public final class RawId extends ObjectId {
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a raw object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string
	 * @param repo the repository to set (can be {@code null})
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	public RawId(String hexStr, WeakReference<Repository> repo) {
		super(hexStr, repo);
	}
	
	
	/**
	 * Constructs a raw object ID from the specified 20-byte array.
	 * @param bytes the byte array
	 * @param repo the repository to set (can be {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if array isn't length 20
	 */
	public RawId(byte[] bytes, WeakReference<Repository> repo) {
		super(bytes, repo);
	}
	
	
	/**
	 * Constructs a raw object ID from 20 bytes in the specified array starting at the specified offset.
	 * @param bytes the byte array
	 * @param off the offset to start at
	 * @param repo the repository to set (can be {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at that offset
	 */
	public RawId(byte[] bytes, int off, WeakReference<Repository> repo) {
		super(bytes, off, repo);
	}
	
}
