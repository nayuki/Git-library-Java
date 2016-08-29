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
import java.lang.ref.WeakReference;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterInputStream;


final class PackfileReader {
	
	private final File indexFile;
	private final File packFile;
	
	private final WeakReference<Repository> sourceRepo;
	
	
	
	public PackfileReader(File index, File pack, WeakReference<Repository> srcRepo) {
		if (index == null || pack == null)
			throw new NullPointerException();
		if (!index.isFile() || !pack.isFile())
			throw new IllegalArgumentException("File does not exist");
		indexFile = index;
		packFile = pack;
		sourceRepo = srcRepo;
	}
	
	
	
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
		int type = (Integer)pair[0];
		byte[] data = (byte[])pair[1];
		
		// Check hash
		Sha1 hasher = new Sha1();
		hasher.update((TYPE_NAMES[type] + " " + data.length + "\0").getBytes("US-ASCII"));
		hasher.update(data);
		if (!hasher.getHash().equals(id))
			throw new DataFormatException("Hash of data mismatches object ID");
		
		// Parse object
		if (type == 1)
			return new CommitObject(data, sourceRepo);
		else if (type == 2)
			return new TreeObject(data, sourceRepo);
		else if (type == 3)
			return new BlobObject(data);
		else
			throw new DataFormatException("Unknown object type: " + type);
	}
	
	
	/* Low-level read methods */
	
	private Long readDataOffset(ObjectId id) throws IOException, DataFormatException {
		RandomAccessFile indexRaf = new RandomAccessFile(indexFile, "r");
		try {
			// Check header; this logic only supports version 2 indexes
			byte[] b = new byte[4];
			indexRaf.readFully(b);
			if (b[0] != -1 || b[1] != 't' || b[2] != 'O' || b[3] != 'c')
				throw new DataFormatException("Pack index header expected");
			if (IoUtils.readInt32(indexRaf) != 2)
				throw new DataFormatException("Index version 2 expected");
			
			// Read pack size
			if (indexRaf.skipBytes(255 * 4) != 255 * 4)
				throw new EOFException();
			int totalObjects = IoUtils.readInt32(indexRaf);
			
			// Skip over some index entries based on head byte
			int headByte = id.getByte(0) & 0xFF;
			int objectOffset = 0;
			if (headByte > 0) {
				indexRaf.seek(8 + (headByte - 1) * 4);
				objectOffset = IoUtils.readInt32(indexRaf);
			}
			
			// Find object ID in index (which is in ascending order)
			indexRaf.seek(8 + 256 * 4 + objectOffset * ObjectId.NUM_BYTES);
			b = new byte[ObjectId.NUM_BYTES];
			while (true) {
				if (objectOffset >= totalObjects)
					return null;  // Not found
				indexRaf.readFully(b);
				ObjectId temp = new CommitId(b, sourceRepo);
				int cmp = temp.compareTo(id);
				if (cmp == 0)
					break;
				else if (cmp > 0)
					return null;  // Not found
				objectOffset++;
			}
			
			// Read the data packfile offset of the object
			indexRaf.seek(8 + 256 * 4 + totalObjects * ObjectId.NUM_BYTES + totalObjects * 4 + objectOffset * 4);
			return (long)IoUtils.readInt32(indexRaf);
			
		} finally {
			indexRaf.close();
		}
	}
	
	
	private Object[] readObjectHeaderless(long byteOffset) throws IOException, DataFormatException {
		if (byteOffset < 0)
			throw new IllegalArgumentException();
		InputStream in = new FileInputStream(packFile);
		try {
			IoUtils.skipFully(in, byteOffset);
			
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
								off |= IoUtils.readUnsignedNoEof(deltaIn) << (i * 8);
						}
						int len = 0;
						for (int i = 0; i < 3; i++) {
							if (((op >>> (i + 4)) & 1) != 0)
								len |= IoUtils.readUnsignedNoEof(deltaIn) << (i * 8);
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
		} finally {
			in.close();
		}
	}
	
	
	/* Byte-level integer decoding functions */
	
	private static int decodeTypeAndSize(InputStream in) throws IOException, DataFormatException {
		int b = IoUtils.readUnsignedNoEof(in);
		int type = (b >>> 4) & 7;
		long size = b & 0xF;
		
		for (int i = 0; (b & 0x80) != 0; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			b = IoUtils.readUnsignedNoEof(in);
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
			int b = IoUtils.readUnsignedNoEof(in);
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
			int b = IoUtils.readUnsignedNoEof(in);
			result |= (b & 0x7FL) << (i * 7);
			if ((b & 0x80) == 0)
				break;
		}
		
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	private static final String[] TYPE_NAMES = {null, "commit", "tree", "blob", null, null, null, null};
	
}
