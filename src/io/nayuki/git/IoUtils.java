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


// Contains functions for frequently used I/O idioms.
final class IoUtils {
	
	// Reads and returns the next unsigned byte (range 0 to 255) from the input stream,
	// or throws an exception if the end of the stream is reached.
	public static int readUnsignedNoEof(InputStream in) throws IOException {
		int b = in.read();
		if (b == -1)
			throw new EOFException();
		return b & 0xFF;
	}
	
	
	// Skips the given number of bytes in the given input stream, or throws EOFException
	// if the end of stream is reached before that number of bytes was skipped.
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
