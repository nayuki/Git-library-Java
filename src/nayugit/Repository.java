package nayugit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterInputStream;


public final class Repository {
	
	private final File directory;
	
	
	
	public Repository(File dir) {
		if (dir == null)
			throw new NullPointerException();
		directory = dir;
	}
	
	
	
	public File getDirectory() {
		return directory;
	}
	
	
	public byte[] readRawObject(ObjectId id) throws IOException {
		File file = new File(directory, "objects" + File.separator + id.hexString.substring(0, 2) + File.separator + id.hexString.substring(2));
		if (!file.isFile())
			throw new FileNotFoundException();
		InflaterInputStream in = new InflaterInputStream(new FileInputStream(file));
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			while (true) {
				int n = in.read(buf);
				if (n == -1)
					break;
				out.write(buf, 0, n);
			}
			return out.toByteArray();
		} finally {
			in.close();
		}
	}
	
	
	public GitObject readObject(ObjectId id) throws IOException, DataFormatException {
		// Read object bytes and extract header
		byte[] bytes = readRawObject(id);
		int index = 0;
		while (bytes[index] != 0)
			index++;
		String header = new String(bytes, 0, index, "US-ASCII");
		bytes = Arrays.copyOfRange(bytes, index + 1, bytes.length);
		
		// Parse header
		String[] parts = header.split(" ", -1);
		if (parts.length != 2)
			throw new DataFormatException("Invalid object header");
		String type = parts[0];
		int length = Integer.parseInt(parts[1]);
		if (length < 0)
			throw new DataFormatException("Negative data length");
		if (length != bytes.length)
			throw new DataFormatException("Data length mismatch");
		
		// Select object type
		if (type.equals("blob"))
			return new BlobObject(id, bytes);
		if (type.equals("tree"))
			return new TreeObject(id, bytes);
		else
			throw new DataFormatException("Unknown object type: " + type);
	}
	
}
