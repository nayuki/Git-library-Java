/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


final class IoUtils {
	
	public static int readUnsignedNoEof(InputStream in) throws IOException {
		int b = in.read();
		if (b == -1)
			throw new EOFException();
		return b & 0xFF;
	}
	
	
	public static void skipFully(InputStream in, long skip) throws IOException {
		if (skip < 0)
			throw new IllegalArgumentException();
		while (skip > 0) {
			long n = in.skip(skip);
			if (n <= 0)
				throw new EOFException();
			skip -= n;
		}
	}
	
}
