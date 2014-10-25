package nayugit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;


public final class Repository {
	
	private final File directory;
	
	
	
	public Repository(File dir) {
		if (dir == null)
			throw new NullPointerException();
		if (!dir.isDirectory())
			throw new IllegalArgumentException("Repository directory does not exist");
		if (!dir.getName().endsWith(".git"))
			throw new IllegalArgumentException("Invalid Git repository directory name");
		if (!new File(dir, "HEAD").isFile()
				|| !new File(dir, "objects").isDirectory()
				|| !new File(dir, "refs").isDirectory())
			throw new IllegalArgumentException("Invalid repository format");
		
		directory = dir;
	}
	
	
	
	public File getDirectory() {
		return directory;
	}
	
	
	public boolean containsObject(ObjectId id) throws IOException, DataFormatException {
		if (getLooseObjectFile(id).isFile())
			return true;
		for (PackfileReader pfr : listPackfiles()) {
			if (pfr.containsObject(id))
				return true;
		}
		return false;
	}
	
	
	public byte[] readRawObject(ObjectId id) throws IOException, DataFormatException {
		File file = getLooseObjectFile(id);
		byte[] result = null;
		if (file.isFile()) {
			// Read from loose object store
			InputStream in = new InflaterInputStream(new FileInputStream(file));
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[1024];
				while (true) {
					int n = in.read(buf);
					if (n == -1)
						break;
					out.write(buf, 0, n);
				}
				result = out.toByteArray();
			} finally {
				in.close();
			}
			
		} else {
			// Scan pack files
			for (PackfileReader pfr : listPackfiles()) {
				result = pfr.readRawObject(id);
				if (result != null)
					break;
			}
		}
		
		if (result != null && !Sha1.getHash(result).equals(id))
			throw new DataFormatException("Hash of data mismatches object ID");
		return result;
	}
	
	
	public GitObject readObject(ObjectId id) throws IOException, DataFormatException {
		if (getLooseObjectFile(id).isFile()) {
			// Read object bytes and extract header
			byte[] bytes = readRawObject(id);
			int index = 0;
			while (index < bytes.length && bytes[index] != 0)
				index++;
			if (index >= bytes.length)
				throw new DataFormatException("Invalid object header");
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
			if (!Integer.toString(length).equals(parts[1]))  // Check for non-canonical number representations like -0, 007, etc.
				throw new DataFormatException("Invalid data length string");
			if (length != bytes.length)
				throw new DataFormatException("Data length mismatch");
			
			// Select object type
			if (type.equals("blob"))
				return new BlobObject(bytes);
			if (type.equals("tree"))
				return new TreeObject(bytes);
			if (type.equals("commit"))
				return new CommitObject(bytes);
			else
				throw new DataFormatException("Unknown object type: " + type);
			
		} else {
			for (PackfileReader pfr : listPackfiles()) {
				GitObject result = pfr.readObject(id);
				if (result != null)
					return result;
			}
			return null;  // Not found
		}
	}
	
	
	public void writeObject(GitObject obj) throws IOException {
		writeRawObject(obj.toBytes());
	}
	
	
	public void writeRawObject(byte[] obj) throws IOException {
		File file = getLooseObjectFile(Sha1.getHash(obj));
		if (file.isFile())
			return;  // Object already exists in the loose objects database; no work to do
		File dir = file.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		
		OutputStream out = new DeflaterOutputStream(new FileOutputStream(file));
		boolean success = false;
		try {
			out.write(obj);
			success = true;
		} finally {
			out.close();
			if (!success)
				file.delete();
		}
	}
	
	
	private Collection<PackfileReader> listPackfiles() {
		Collection<PackfileReader> result = new ArrayList<PackfileReader>();
		for (File item : new File(directory, "objects" + File.separator + "pack").listFiles()) {
			String name = item.getName();
			if (item.isFile() && name.startsWith("pack-") && name.endsWith(".idx")) {
				File packfile = new File(item.getParentFile(), name.substring(0, name.length() - 3) + "pack");
				if (packfile.isFile())
					result.add(new PackfileReader(item, packfile));
			}
		}
		return result;
	}
	
	
	private File getLooseObjectFile(ObjectId id) {
		return new File(directory, "objects" + File.separator + id.hexString.substring(0, 2) + File.separator + id.hexString.substring(2));
	}
	
}
