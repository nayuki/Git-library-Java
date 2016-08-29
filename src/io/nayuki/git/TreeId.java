/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;


public class TreeId extends ObjectId {
	
	public TreeId(String hexStr) {
		super(hexStr);
	}
	
	
	public TreeId(byte[] bytes) {
		super(bytes);
	}
	
	
	public TreeId(byte[] bytes, int off) {
		super(bytes, off);
	}
	
}
