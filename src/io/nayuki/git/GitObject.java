/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.nio.charset.StandardCharsets;


public abstract class GitObject {
	
	GitObject() {}
	
	
	
	public abstract byte[] toBytes();
	
	
	public ObjectId getId() {
		return new RawId(Sha1.getHash(toBytes()), null);
	}
	
	
	static byte[] addHeader(String type, byte[] data) {
		byte[] header = String.format("%s %d\0", type, data.length).getBytes(StandardCharsets.US_ASCII);
		byte[] result = new byte[header.length + data.length];
		System.arraycopy(header, 0, result, 0, header.length);
		System.arraycopy(data, 0, result, header.length, data.length);
		return result;
	}
	
}
