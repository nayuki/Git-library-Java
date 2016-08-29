/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.UnsupportedEncodingException;


public abstract class GitObject {
	
	public GitObject() {}
	
	
	
	public abstract byte[] toBytes();
	
	
	public ObjectId getId() {
		return new RawId(Sha1.getHash(toBytes()));
	}
	
	
	static byte[] addHeader(String type, byte[] data) {
		try {
			byte[] header = String.format("%s %d\0", type, data.length).getBytes("US-ASCII");
			byte[] result = new byte[header.length + data.length];
			System.arraycopy(header, 0, result, 0, header.length);
			System.arraycopy(data, 0, result, header.length, data.length);
			return result;
		} catch (UnsupportedEncodingException e) {
			throw new AssertionError(e);
		}
	}
	
}
