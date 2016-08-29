/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


public final class BlobObject extends GitObject {
	
	public byte[] data;
	
	
	
	public BlobObject(byte[] data) {
		if (data == null)
			throw new NullPointerException();
		this.data = data.clone();
	}
	
	
	
	public byte[] toBytes() {
		if (data == null)
			throw new NullPointerException();
		return addHeader("blob", data);
	}
	
	
	public String toString() {
		return String.format("BlobObject(length=%d)", data.length);
	}
	
}
