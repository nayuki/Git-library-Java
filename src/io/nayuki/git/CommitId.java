/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import java.io.IOException;


/**
 * An immutable 160-bit (20-byte) SHA-1 hash, whose value should reflect a commit object.
 * @see CommitObject
 * @see Reference
 */
public final class CommitId extends ObjectId {
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a commit object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string (not {@code null})
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	public CommitId(String hexStr) {
		super(hexStr);
	}
	
	
	/**
	 * Constructs a commit object ID from the specified 20-byte array.
	 * @param bytes the byte array (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if array isn't length 20
	 */
	public CommitId(byte[] bytes) {
		super(bytes);
	}
	
	
	/**
	 * Constructs a commit object ID from 20 bytes in the specified array starting at the specified offset.
	 * @param bytes the byte array (not {@code null})
	 * @param off the offset to start at
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at that offset
	 */
	public CommitId(byte[] bytes, int off) {
		super(bytes, off);
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Reads the object data for this object ID from the specified repository.
	 * @param repo the repository to read from (not {@code null})
	 * @return the object data (not {@code null})
	 * @throws IllegalArgumentException if no object with the ID was found in the repository
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 * @throws ClassCastException if an object was successfully read but its type is not a commit object
	 */
	public CommitObject read(Repository repo) throws IOException {
		return (CommitObject)super.read(repo);
	}
	
}
