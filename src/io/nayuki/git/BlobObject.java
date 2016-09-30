/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


/**
 * A simple wrapper around a mutable byte array. Mutable structure.
 * @see BlobId
 * @see TreeObject
 */
public final class BlobObject extends GitObject {
	
	/*---- Fields ----*/
	
	/**
	 * The payload data. If a blob object was created by reading from a repository, then this value is not {@code null}.
	 * Likewise, when writing out a {@code BlobObject} to a repository, this value must not be {@code null}.
	 */
	public byte[] data;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a blank blob object with the data initially set to {@code null}.
	 */
	public BlobObject() {
		data = null;
	}
	
	
	/**
	 * Constructs a blob object with the data initially set to a clone of the specified array.
	 * @param b the byte array to clone (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 */
	public BlobObject(byte[] b) {
		if (b == null)
			throw new NullPointerException();
		data = b.clone();
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the raw byte serialization of the current state of this blob object, including a lightweight header.
	 * @return the raw byte serialization of this object (not {@code null})
	 * @throws IllegalStateException if the data field is {@code null}
	 */
	public byte[] toBytes() {
		checkState();
		return addHeader("blob", data);
	}
	
	
	/**
	 * Returns the hash ID of the current state of this blob object.
	 * @return the hash ID of this blob object (not {@code null})
	 */
	public BlobId getId() {
		return new BlobId(Sha1.getHash(toBytes()));
	}
	
	
	/**
	 * Returns a string representation of this blob object. The format is subject to change.
	 * @return a string representation of this blob object
	 */
	public String toString() {
		return String.format("BlobObject(length=%d)", data.length);
	}
	
	
	private void checkState() {
		if (data == null)
			throw new IllegalStateException("Data field is null");
	}
	
}
