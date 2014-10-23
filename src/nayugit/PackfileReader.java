package nayugit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterInputStream;


public final class PackfileReader {
	
	private final File indexFile;
	private final File packFile;
	
	
	
	public PackfileReader(File index, File pack) {
		if (index == null || pack == null)
			throw new NullPointerException();
		indexFile = index;
		packFile = pack;
	}
	
	
	
	public Object[] readRawObject(ObjectId id) throws IOException, DataFormatException {
		RandomAccessFile indexRaf = new RandomAccessFile(indexFile, "r");
		int byteOffset;
		try {
			// Only supports version 2 indexes
			byte[] b;
			
			// Check header
			b = new byte[8];
			indexRaf.readFully(b);
			if (b[0] != -1 || b[1] != 't' || b[2] != 'O' || b[3] != 'c')
				throw new DataFormatException("Pack index header expected");
			if (toInt32(b, 4) != 2)
				throw new DataFormatException("Index version 2 expected");
			
			// Read pack size
			if (indexRaf.skipBytes(255 * 4) != 255 * 4)
				throw new EOFException();
			b = new byte[4];
			indexRaf.readFully(b);
			int totalObjects = toInt32(b, 0);
			
			// Find object ID in index
			int headByte = id.getByte(0) & 0xFF;
			int objectOffset = 0;
			if (headByte > 0) {
				indexRaf.seek(8 + (headByte - 1) * 4);
				b = new byte[4];
				indexRaf.readFully(b);
				objectOffset = toInt32(b, 0);
			}
			indexRaf.seek(8 + 256 * 4 + objectOffset * 20);
			b = new byte[20];
			while (true) {
				indexRaf.readFully(b);
				ObjectId temp = new ObjectId(b);
				int cmp = temp.compareTo(id);
				if (cmp == 0)
					break;
				else if (cmp > 0)
					return null;
				objectOffset++;
			}
			b = new byte[4];
			indexRaf.seek(8 + 256 * 4 + totalObjects * 20 + totalObjects * 4 + objectOffset * 4);
			indexRaf.readFully(b);
			byteOffset = toInt32(b, 0);
		} finally {
			indexRaf.close();
		}
		
		return readRawObject(byteOffset);
	}
	
	
	private Object[] readRawObject(int byteOffset) throws IOException, DataFormatException {
		if (byteOffset < 0)
			throw new IllegalArgumentException();
		InputStream in = new FileInputStream(packFile);
		try {
			// Skip fully
			int toSkip = byteOffset;
			while (toSkip > 0) {
				long n = in.skip(toSkip);
				if (n <= 0)
					throw new EOFException();
				toSkip -= n;
			}
			
			// Read decompressed size
			int typeAndSize = decodeTypeAndSize(in);
			int type = typeAndSize & 7;
			if (type == 0 || type == 5)
				throw new DataFormatException("Unknown object type: " + type);
			int size = typeAndSize >>> 3;
			
			int deltaOffset;
			if (type == 6)
				deltaOffset = decodeOffsetDelta(in);
			else
				deltaOffset = -1;
			
			// Decompress data
			InputStream inflateIn = new InflaterInputStream(in);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				byte[] buf = new byte[1024];
				while (true) {
					int n = inflateIn.read(buf);
					if (n == -1)
						break;
					out.write(buf, 0, n);
				}
			} finally {
				inflateIn.close();
			}
			byte[] data = out.toByteArray();
			if (data.length != size)
				throw new DataFormatException("Data length mismatch");
			
			if (type == 6) {
				Object[] temp = readRawObject(byteOffset - deltaOffset);
				type = (Integer)temp[0];
				byte[] base = (byte[])temp[1];
				InputStream deltaIn = new ByteArrayInputStream(data);
				int baseLen = decodeDeltaHeader(deltaIn);
				if (baseLen != base.length)
					throw new DataFormatException("Base data length mismatch");
				int dataLen = decodeDeltaHeader(deltaIn);
				
				out = new ByteArrayOutputStream();
				while (true) {
					int op = deltaIn.read();
					if (op == -1)
						break;
					if ((op & 0x80) == 0) {  // Insert
						byte[] buf = new byte[op];
						int n = deltaIn.read(buf);
						if (n != buf.length)
							throw new EOFException();
						out.write(buf);
					} else {  // Copy
						int off = 0;
						for (int i = 0; i < 4; i++) {
							if (((op >>> i) & 1) != 0)
								off |= readByteNoEof(deltaIn) << (i * 8);
						}
						int len = 0;
						for (int i = 0; i < 3; i++) {
							if (((op >>> (i + 4)) & 1) != 0)
								len |= readByteNoEof(deltaIn) << (i * 8);
						}
						if (len == 0)
							len = 0x10000;
						out.write(base, off, len);
					}
				}
				data = out.toByteArray();
				if (data.length != dataLen)
					throw new DataFormatException("Data length mismatch");
			}
			
			return new Object[]{type, data};
		} finally {
			in.close();
		}
	}
	
	
	private static int decodeTypeAndSize(InputStream in) throws IOException, DataFormatException {
		int b = readByteNoEof(in);
		int type = (b >>> 4) & 7;
		long size = b & 0xF;
		
		for (int i = 0; ; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			b = readByteNoEof(in);
			size |= (b & 0x7FL) << (i * 7 + 4);
			if ((b & 0x80) == 0)
				break;
		}
		
		long result = size << 3 | type;
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	private static int decodeOffsetDelta(InputStream in) throws IOException, DataFormatException {
		long result = 0;
		for (int i = 0; ; i++) {
			if (i >= 5)
				throw new DataFormatException("Variable-length integer too long");
			int b = readByteNoEof(in);
			result |= b & 0x7F;
			if ((b & 0x80) == 0)
				break;
			result++;
			result <<= 7;
		}
		
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	private static int decodeDeltaHeader(InputStream in) throws IOException, DataFormatException {
		long result = 0;
		for (int i = 0; ; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			int b = readByteNoEof(in);
			result |= (b & 0x7FL) << (i * 7);
			if ((b & 0x80) == 0)
				break;
		}
		
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	private static int readByteNoEof(InputStream in) throws IOException {
		int b = in.read();
		if (b == -1)
			throw new EOFException();
		return b & 0xFF;  // Convert to unsigned
	}
	
	
	private static int toInt32(byte[] b, int off) {
		return b[off + 0] << 24 | (b[off + 1] & 0xFF) << 16 | (b[off + 2] & 0xFF) << 8 | (b[off + 3] & 0xFF);
	}
	
}
