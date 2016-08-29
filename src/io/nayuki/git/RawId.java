/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


public final class RawId extends ObjectId {
	
	public RawId(String hexStr) {
		super(hexStr);
	}
	
	
	public RawId(byte[] bytes) {
		super(bytes);
	}
	
	
	public RawId(byte[] bytes, int off) {
		super(bytes, off);
	}
	
}
