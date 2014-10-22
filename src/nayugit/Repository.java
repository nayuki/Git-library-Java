package nayugit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
	
}
