/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


public final class BlobId extends ObjectId {
	
	public BlobId(String hexStr) {
		super(hexStr);
	}
	
	
	public BlobId(byte[] bytes) {
		super(bytes);
	}
	
	
	public BlobId(byte[] bytes, int off) {
		super(bytes, off);
	}
	
}
