/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterInputStream;


final class PackfileReader {
	
	/*---- Fields ----*/
	
	private final File indexFile;
	private final File packFile;
	
	
	
	/*---- Constructors ----*/
	
	public PackfileReader(File index, File pack) {
		if (index == null || pack == null)
			throw new NullPointerException();
		if (!index.isFile() || !pack.isFile())
			throw new IllegalArgumentException("File does not exist");
		indexFile = index;
		packFile = pack;
	}
	
	
	
	/*---- Methods ----*/
	
	/* High-level query methods */
	
	public boolean containsObject(ObjectId id) throws IOException, DataFormatException {
		return readDataOffset(id) != null;
	}
	
	
	public byte[] readRawObject(ObjectId id) throws IOException, DataFormatException {
		Object[] pair = readObjectHeaderless(readDataOffset(id));
		int type = (Integer)pair[0];
		String typeStr = TYPE_NAMES[type];
		if (typeStr == null)
			throw new DataFormatException("Unknown object type: " + type);
		return GitObject.addHeader(typeStr, (byte[])pair[1]);
	}
	
	
	public GitObject readObject(ObjectId id) throws IOException, DataFormatException {
		// Read data
		Object[] pair = readObjectHeaderless(readDataOffset(id));
		int typeIndex = (Integer)pair[0];
		byte[] bytes = (byte[])pair[1];
		
		// Check hash
		String typeName = TYPE_NAMES[typeIndex];
		if (typeName == null)
			throw new DataFormatException("Unknown object type: " + typeIndex);
		Sha1 hasher = new Sha1();
		hasher.update((TYPE_NAMES[typeIndex] + " " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
		hasher.update(bytes);
		if (!Arrays.equals(hasher.getHash(), id.getBytes()))
			throw new DataFormatException("Hash of data mismatches object ID");
		
		// Parse object
		switch (typeName) {
			case "blob"  :  return new BlobObject  (bytes);
			case "tree"  :  return new TreeObject  (bytes);
			case "commit":  return new CommitObject(bytes);
			default:  throw new DataFormatException("Unknown object type: " + typeIndex);
		}
	}
	
	
	/* Low-level read methods */
	
	private Long readDataOffset(ObjectId id) throws IOException, DataFormatException {
		try (RandomAccessFile raf = new RandomAccessFile(indexFile, "r")) {
			// Check header; this logic only supports version 2 indexes
			byte[] b = new byte[4];
			raf.readFully(b);
			if (b[0] != -1 || b[1] != 't' || b[2] != 'O' || b[3] != 'c')
				throw new DataFormatException("Pack index header expected");
			if (raf.readInt() != 2)
				throw new DataFormatException("Index version 2 expected");
			
			// Read pack size
			if (raf.skipBytes(255 * 4) != 255 * 4)
				throw new EOFException();
			int totalObjects = raf.readInt();
			
			// Skip over some index entries based on head byte
			int headByte = id.getByte(0) & 0xFF;
			int objectOffset = 0;
			if (headByte > 0) {
				raf.seek(8 + (headByte - 1) * 4);
				objectOffset = raf.readInt();
			}
			
			// Find object ID in index (which is in ascending order)
			raf.seek(8 + 256 * 4 + objectOffset * ObjectId.NUM_BYTES);
			b = new byte[ObjectId.NUM_BYTES];
			while (true) {
				if (objectOffset >= totalObjects)
					return null;  // Not found
				raf.readFully(b);
				ObjectId temp = new CommitId(b);
				int cmp = temp.compareTo(id);
				if (cmp == 0)
					break;
				else if (cmp > 0)
					return null;  // Not found
				objectOffset++;
			}
			
			// Read the data packfile offset of the object
			raf.seek(8 + 256 * 4 + totalObjects * ObjectId.NUM_BYTES + totalObjects * 4 + objectOffset * 4);
			return (long)raf.readInt();
		}
	}
	
	
	private Object[] readObjectHeaderless(long byteOffset) throws IOException, DataFormatException {
		if (byteOffset < 0)
			throw new IllegalArgumentException();
		try (InputStream in = new FileInputStream(packFile)) {
			skipFully(in, byteOffset);
			
			// Read decompressed size and type
			int typeAndSize = decodeTypeAndSize(in);
			int type = typeAndSize & 7;  // 3-bit unsigned
			if (type == 0 || type == 5 || type == 7)
				throw new DataFormatException("Unknown object type: " + type);
			int size = typeAndSize >>> 3;
			
			// Read delta offset
			int deltaOffset;
			if (type == 6)
				deltaOffset = decodeOffsetDelta(in);
			else
				deltaOffset = -1;
			
			// Decompress data
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream inflateIn = new InflaterInputStream(in)) {
				byte[] buf = new byte[1024];
				while (true) {
					int n = inflateIn.read(buf);
					if (n == -1)
						break;
					out.write(buf, 0, n);
				}
			}
			byte[] data = out.toByteArray();
			if (data.length != size)
				throw new DataFormatException("Data length mismatch");
			
			// Handle delta encoding
			if (type == 6) {
				// Recurse
				Object[] temp = readObjectHeaderless(byteOffset - deltaOffset);
				type = (Integer)temp[0];
				byte[] base = (byte[])temp[1];
				
				// Decode delta header
				InputStream deltaIn = new ByteArrayInputStream(data);
				int baseLen = decodeDeltaHeaderInt(deltaIn);
				if (baseLen != base.length)
					throw new DataFormatException("Base data length mismatch");
				int dataLen = decodeDeltaHeaderInt(deltaIn);
				
				// Decode delta format
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
								off |= readUnsignedNoEof(deltaIn) << (i * 8);
						}
						int len = 0;
						for (int i = 0; i < 3; i++) {
							if (((op >>> (i + 4)) & 1) != 0)
								len |= readUnsignedNoEof(deltaIn) << (i * 8);
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
			
			// Done
			return new Object[]{type, data};
		}
	}
	
	
	/* Byte-level integer decoding functions */
	
	private static int decodeTypeAndSize(InputStream in) throws IOException, DataFormatException {
		int b = readUnsignedNoEof(in);
		int type = (b >>> 4) & 7;
		long size = b & 0xF;
		
		for (int i = 0; (b & 0x80) != 0; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			b = readUnsignedNoEof(in);
			size |= (b & 0x7FL) << (i * 7 + 4);
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
			int b = readUnsignedNoEof(in);
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
	
	
	private static int decodeDeltaHeaderInt(InputStream in) throws IOException, DataFormatException {
		long result = 0;
		for (int i = 0; ; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			int b = readUnsignedNoEof(in);
			result |= (b & 0x7FL) << (i * 7);
			if ((b & 0x80) == 0)
				break;
		}
		
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	// Reads and returns the next unsigned byte (range 0 to 255) from the input stream,
	// or throws an exception if the end of the stream is reached.
	private static int readUnsignedNoEof(InputStream in) throws IOException {
		int b = in.read();
		if (b == -1)
			throw new EOFException();
		return b & 0xFF;
	}
	
	
	// Skips the given number of bytes in the given input stream, or throws EOFException
	// if the end of stream is reached before that number of bytes was skipped.
	private static void skipFully(InputStream in, long skip) throws IOException {
		if (skip < 0)
			throw new IllegalArgumentException();
		while (skip > 0) {
			long n = in.skip(skip);
			if (n <= 0)
				throw new EOFException();
			skip -= n;
		}
	}
	
	
	private static final String[] TYPE_NAMES = {
		// 0,        1,      2,      3,    4,    5,    6,    7:  Type indices
		null, "commit", "tree", "blob", null, null, null, null};
	
}
