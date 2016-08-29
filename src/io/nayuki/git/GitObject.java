/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.nio.charset.StandardCharsets;


/**
 * A hashable object that can be read from and written to a Git {@link Repository}.
 * Generally speaking, subclasses of {@code GitObject} are mutable.
 */
public abstract class GitObject {
	
	/*---- Constructors ----*/
	
	// Only allows subclassing within this package.
	GitObject() {}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the raw byte serialization of this object, including a lightweight header.
	 * @return the raw byte serialization of this object
	 */
	public abstract byte[] toBytes();
	
	
	/**
	 * Returns the hash ID of the current state of this object.
	 * @return the hash ID of this object
	 */
	public ObjectId getId() {
		return new RawId(Sha1.getHash(toBytes()), null);
	}
	
	
	
	/*---- Static helper functions ----*/
	
	// Returns a new byte array containing some header fields prepended to the given byte data.
	static byte[] addHeader(String type, byte[] data) {
		byte[] header = String.format("%s %d\0", type, data.length).getBytes(StandardCharsets.US_ASCII);
		byte[] result = new byte[header.length + data.length];
		System.arraycopy(header, 0, result, 0, header.length);
		System.arraycopy(data, 0, result, header.length, data.length);
		return result;
	}
	
}
