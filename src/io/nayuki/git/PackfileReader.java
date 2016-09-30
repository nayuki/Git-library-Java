/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;


/**
 * Manages the state and logic for reading from a Git pack file and index file.
 * A helper class for {@link FileRepository}. Currently, pack file reader objects
 * are immutable and hold no resources or file handles outside of method calls.
 */
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
		Object[] pair = readObjectHeaderless(id);
		return GitObject.addHeader((String)pair[0], (byte[])pair[1]);
	}
	
	
	public GitObject readObject(ObjectId id) throws IOException, DataFormatException {
		Object[] pair = readObjectHeaderless(id);
		byte[] bytes = (byte[])pair[1];
		switch ((String)pair[0]) {
			case "blob"  :  return new BlobObject  (bytes);
			case "tree"  :  return new TreeObject  (bytes);
			case "commit":  return new CommitObject(bytes);
			default:  throw new AssertionError();
		}
	}
	
	
	/* Low-level read methods */
	
	// Reads the index file to find the pack file byte offset of the given hash,
	// returning an integer value if found or null if not found.
	private Long readDataOffset(ObjectId id) throws IOException, DataFormatException {
		final int HEADER_LEN = 8;
		final int FANOUT_LEN = 256 * 4;
		
		try (RandomAccessFile raf = new RandomAccessFile(indexFile, "r")) {
			// Check file header; this logic only supports version 2 indexes
			byte[] b = new byte[4];
			raf.readFully(b);
			if (b[0] != (byte)0xFF || b[1] != 't' || b[2] != 'O' || b[3] != 'c')
				throw new DataFormatException("Pack index header expected");
			if (raf.readInt() != 2)
				throw new DataFormatException("Index version 2 expected");
			
			// Read pack size
			raf.seek(HEADER_LEN + FANOUT_LEN - 4);
			long totalObjects = raf.readInt() & 0xFFFFFFFFL;
			
			// Skip over some index entries based on head byte
			int headByte = id.getByte(0) & 0xFF;
			long objectOffset = 0;
			if (headByte > 0) {
				raf.seek(HEADER_LEN + (headByte - 1) * 4);
				objectOffset = raf.readInt() & 0xFFFFFFFFL;
			}
			
			// Find object ID in index (which is in ascending order)
			raf.seek(HEADER_LEN + FANOUT_LEN + objectOffset * ObjectId.NUM_BYTES);
			b = new byte[ObjectId.NUM_BYTES];
			while (true) {
				if (objectOffset >= totalObjects)
					return null;  // Not found
				raf.readFully(b);
				int cmp = new CommitId(b).compareTo(id);
				if (cmp == 0)
					break;
				else if (cmp > 0)
					return null;  // Not found
				// Else cmp < 0
				objectOffset++;
			}
			
			// Read the data packfile offset of the object
			long offsetTablesStart = HEADER_LEN + FANOUT_LEN + totalObjects * (ObjectId.NUM_BYTES + 4);
			raf.seek(offsetTablesStart + objectOffset * 4);
			long result = raf.readInt();
			if (result < 0) {  // Most significant bit is set; do more processing
				raf.seek(offsetTablesStart + totalObjects * 4 + (result & 0x7FFFFFFF) * 8);
				result = raf.readLong();
			}
			return result;
		}
	}
	
	
	private Object[] readObjectHeaderless(ObjectId id) throws IOException, DataFormatException {
		// Read byte data
		int typeIndex;
		byte[] bytes;
		try (RandomAccessFile raf = new RandomAccessFile(packFile, "r")) {
			Object[] temp = readObjectHeaderless(raf, readDataOffset(id));
			typeIndex = (Integer)temp[0];
			bytes = (byte[])temp[1];
		}
		if (typeIndex >>> 3 != 0)
			throw new AssertionError();
		
		// Check hash
		String typeName = TYPE_NAMES[typeIndex];
		if (typeName == null)
			throw new DataFormatException("Unknown object type: " + typeIndex);
		Sha1 hasher = new Sha1();
		hasher.update((typeName + " " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
		hasher.update(bytes);
		if (!Arrays.equals(hasher.getHash(), id.getBytes()))
			throw new DataFormatException("Hash of data mismatches object ID");
		return new Object[]{typeName, bytes};
	}
	
	
	private Object[] readObjectHeaderless(RandomAccessFile raf, long byteOffset) throws IOException, DataFormatException {
		if (byteOffset < 0)
			throw new IllegalArgumentException();
		raf.seek(byteOffset);
		
		// Read decompressed size and type
		int typeAndSize = decodeTypeAndSize(raf);
		int type = typeAndSize & 7;  // 3-bit unsigned
		if (type == 0 || type == 5 || type == 7)
			throw new DataFormatException("Unknown object type: " + type);
		int size = typeAndSize >>> 3;
		
		// Read delta offset
		int deltaOffset;
		if (type == 6)
			deltaOffset = decodeOffsetDelta(raf);
		else
			deltaOffset = -1;
		
		// Decompress data
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Inflater inf = new Inflater(false);
		try {
			byte[] inbuf  = new byte[1024];
			byte[] outbuf = new byte[1024];
			while (true) {
				int outn = inf.inflate(outbuf);
				if (outn > 0)
					out.write(outbuf, 0, outn);
				else if (inf.needsInput()) {
					int inn = raf.read(inbuf);
					if (inn == -1)
						throw new EOFException();
					inf.setInput(inbuf, 0, inn);
				} else if (inf.finished())
					break;
				else
					throw new DataFormatException();
			}
		} finally {
			inf.end();
		}
		byte[] data = out.toByteArray();
		if (data.length != size)
			throw new DataFormatException("Data length mismatch");
		
		// Handle delta encoding
		if (type == 6) {
			// Recurse
			Object[] temp = readObjectHeaderless(raf, byteOffset - deltaOffset);
			type = (Integer)temp[0];
			byte[] base = (byte[])temp[1];
			
			// Decode delta header
			DataInputStream deltaIn = new DataInputStream(new ByteArrayInputStream(data));
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
					deltaIn.readFully(buf);
					out.write(buf);
				} else {  // Copy
					int off = 0;
					for (int i = 0; i < 4; i++) {
						if (((op >>> i) & 1) != 0)
							off |= deltaIn.readUnsignedByte() << (i * 8);
					}
					int len = 0;
					for (int i = 0; i < 3; i++) {
						if (((op >>> (i + 4)) & 1) != 0)
							len |= deltaIn.readUnsignedByte() << (i * 8);
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
	
	
	/* Byte-level integer decoding functions */
	
	private static int decodeTypeAndSize(DataInput in) throws IOException, DataFormatException {
		int b = in.readUnsignedByte();
		int type = (b >>> 4) & 7;
		long size = b & 0xF;
		
		for (int i = 0; (b & 0x80) != 0; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			b = in.readUnsignedByte();
			size |= (b & 0x7FL) << (i * 7 + 4);
		}
		
		long result = size << 3 | type;
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	private static int decodeOffsetDelta(DataInput in) throws IOException, DataFormatException {
		long result = 0;
		for (int i = 0; ; i++) {
			if (i >= 5)
				throw new DataFormatException("Variable-length integer too long");
			int b = in.readUnsignedByte();
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
	
	
	private static int decodeDeltaHeaderInt(DataInput in) throws IOException, DataFormatException {
		long result = 0;
		for (int i = 0; ; i++) {
			if (i >= 6)
				throw new DataFormatException("Variable-length integer too long");
			int b = in.readUnsignedByte();
			result |= (b & 0x7FL) << (i * 7);
			if ((b & 0x80) == 0)
				break;
		}
		
		if ((int)result != result)
			throw new DataFormatException("Variable-length integer too large");
		return (int)result;
	}
	
	
	private static final String[] TYPE_NAMES = {
		// 0,        1,      2,      3,    4,    5,    6,    7:  Type indices
		null, "commit", "tree", "blob", null, null, null, null};
	
}
