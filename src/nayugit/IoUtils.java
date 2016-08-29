/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package nayugit;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;


final class IoUtils {
	
	public static int readUnsignedNoEof(InputStream in) throws IOException {
		int b = in.read();
		if (b == -1)
			throw new EOFException();
		return b & 0xFF;
	}
	
	
	public static int readInt32(RandomAccessFile raf) throws IOException {
		byte[] b = new byte[4];
		raf.readFully(b);
		return b[0] << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
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
