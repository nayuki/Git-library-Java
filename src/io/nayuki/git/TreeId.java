/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import java.io.IOException;


/**
 * An immutable 160-bit SHA-1 hash, whose value should reflect a tree object.
 * @see TreeObject
 */
public final class TreeId extends ObjectId {
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a tree object ID from the specified 20-byte array.
	 * @param b the byte array (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if the array isn't length 20
	 */
	public TreeId(byte[] b) {
		super(b);
	}
	
	
	/**
	 * Constructs a tree object ID from 20 bytes in the
	 * specified array starting at the specified offset.
	 * @param b the byte array (not {@code null})
	 * @param off the offset to start at
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at the offset
	 */
	public TreeId(byte[] b, int off) {
		super(b, off);
	}
	
	
	/**
	 * Constructs a tree object ID from the specified hexadecimal string.
	 * @param hex the hexadecimal string (not {@code null})
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	public TreeId(String hex) {
		super(hex);
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Reads the object data for this object ID from the specified repository.
	 * @param repo the repository to read from (not {@code null})
	 * @return the object data (not {@code null})
	 * @throws IllegalArgumentException if no object with the ID was found in the repository
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 * @throws ClassCastException if an object was successfully read but its type is not a tree object
	 */
	@Override public TreeObject read(Repository repo) throws IOException {
		return (TreeObject)super.read(repo);
	}
	
}
