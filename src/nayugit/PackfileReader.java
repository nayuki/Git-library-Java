package nayugit;

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
		
		InputStream packIn = new FileInputStream(packFile);
		try {
			// Skip fully
			while (byteOffset > 0) {
				long n = packIn.skip(byteOffset);
				if (n <= 0)
					throw new EOFException();
				byteOffset -= n;
			}
			
			// Read decompressed size
			int b = packIn.read();
			if (b == -1)
				throw new EOFException();
			int type = (b >>> 4) & 7;
			int size = b & 0xF;
			boolean hasNext = (b & 0x80) != 0;
			for (int shift = 4; hasNext; shift += 7) {
				if (shift + 7 > 32)
					throw new DataFormatException("Variable-length integer too long");
				b = packIn.read();
				if (b == -1)
					throw new EOFException();
				size |= (b & 0x7F) << shift;
				hasNext = (b & 0x80) != 0;
			}
			
			// Decompress data
			InputStream in = new InflaterInputStream(packIn);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try {
				byte[] buf = new byte[1024];
				while (true) {
					int n = in.read(buf);
					if (n == -1)
						break;
					out.write(buf, 0, n);
				}
			} finally {
				in.close();
			}
			byte[] data = out.toByteArray();
			if (data.length != size)
				throw new DataFormatException("Data length mismatch");
			return new Object[]{type, data};
		} finally {
			packIn.close();
		}
	}
	
	
	private static int toInt32(byte[] b, int off) {
		return b[off + 0] << 24 | (b[off + 1] & 0xFF) << 16 | (b[off + 2] & 0xFF) << 8 | (b[off + 3] & 0xFF);
	}
	
}
