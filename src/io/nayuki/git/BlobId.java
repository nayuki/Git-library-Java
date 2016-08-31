/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.zip.DataFormatException;


/**
 * A 160-bit (20-byte) SHA-1 hash with extra information. The hash value should reflect a blob object.
 */
public final class BlobId extends ObjectId {
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a blob object ID from the specified hexadecimal string.
	 * @param hexStr the hexadecimal string
	 * @param repo the repository to set (can be {@code null})
	 * @throws NullPointerException if the string is {@code null}
	 * @throws IllegalArgumentException if the string isn't length 40 or has characters outside {0-9, a-f, A-F}
	 */
	public BlobId(String hexStr, WeakReference<Repository> repo) {
		super(hexStr, repo);
	}
	
	
	/**
	 * Constructs a blob object ID from the specified 20-byte array.
	 * @param bytes the byte array
	 * @param repo the repository to set (can be {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if array isn't length 20
	 */
	public BlobId(byte[] bytes, WeakReference<Repository> repo) {
		super(bytes, repo);
	}
	
	
	/**
	 * Constructs a blob object ID from 20 bytes in the specified array starting at the specified offset.
	 * @param bytes the byte array
	 * @param off the offset to start at
	 * @param repo the repository to set (can be {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IndexOutOfBoundsException if the offset is negative,
	 * or there are fewer than 20 bytes remaining starting at that offset
	 */
	public BlobId(byte[] bytes, int off, WeakReference<Repository> repo) {
		super(bytes, off, repo);
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Reads the object data for this object ID from the associated repository.
	 * @return the object data (not {@code null})
	 * @throws IOException if an I/O exception occurred
	 * @throws DataFormatException if malformed data was encountered during reading
	 * @throws ClassCastException if an object was successfully read but its type is not a blob object
	 */
	public BlobObject read() throws IOException, DataFormatException {
		return (BlobObject)super.read();
	}
	
}
