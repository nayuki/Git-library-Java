/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


public final class CommitId extends ObjectId {
	
	public CommitId(String hexStr) {
		super(hexStr);
	}
	
	
	public CommitId(byte[] bytes) {
		super(bytes);
	}
	
	
	public CommitId(byte[] bytes, int off) {
		super(bytes, off);
	}
	
}
