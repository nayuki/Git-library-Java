/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;


/**
 * A repository based on files and directories in the file system.
 */
public final class FileRepository implements Repository {
	
	/*---- Fields ----*/
	
	private File directory;
	
	
	
	/*---- Constructors ----*/
	
	public FileRepository(File dir) {
		if (dir == null)
			throw new NullPointerException();
		if (!dir.isDirectory())
			throw new IllegalArgumentException("Repository directory does not exist");
		if (!new File(dir, "config").isFile() || !new File(dir, "objects").isDirectory())
			throw new IllegalArgumentException("Invalid repository format");
		directory = dir;
	}
	
	
	
	/*---- Methods ----*/
	
	public File getDirectory() {
		return directory;
	}
	
	
	/**
	 * Invalidates this repository object, which may close file streams and removed cached data.
	 * This must be called when finished using a repository.
	 * @throws IOException if an I/O exception occurred
	 */
	public void close() throws IOException {
		directory = null;
	}
	
	
	public boolean containsObject(ObjectId id) throws IOException, DataFormatException {
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		if (getLooseObjectFile(id).isFile())
			return true;
		for (PackfileReader pfr : listPackfiles()) {
			if (pfr.containsObject(id))
				return true;
		}
		return false;
	}
	
	
	private byte[] readRawObject(ObjectId id) throws IOException, DataFormatException {
		File file = getLooseObjectFile(id);
		byte[] result = null;
		if (file.isFile()) {
			// Read from loose object store
			try (InputStream in = new InflaterInputStream(new FileInputStream(file))) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[1024];
				while (true) {
					int n = in.read(buf);
					if (n == -1)
						break;
					out.write(buf, 0, n);
				}
				result = out.toByteArray();
			}
			
		} else {
			// Scan pack files
			for (PackfileReader pfr : listPackfiles()) {
				result = pfr.readRawObject(id);
				if (result != null)
					break;
			}
		}
		
		if (result != null && !Arrays.equals(Sha1.getHash(result), id.getBytes()))
			throw new DataFormatException("Hash of data mismatches object ID");
		return result;
	}
	
	
	public GitObject readObject(ObjectId id) throws IOException, DataFormatException {
		if (id == null)
			throw new NullPointerException();
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		
		if (getLooseObjectFile(id).isFile()) {
			// Read object bytes and extract header
			byte[] bytes = readRawObject(id);
			int index = 0;
			while (index < bytes.length && bytes[index] != 0)
				index++;
			if (index >= bytes.length)
				throw new DataFormatException("Invalid object header");
			String header = new String(bytes, 0, index, StandardCharsets.US_ASCII);
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
		if (obj == null)
			throw new NullPointerException();
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		writeRawObject(obj.toBytes());
	}
	
	
	private void writeRawObject(byte[] obj) throws IOException {
		File file = getLooseObjectFile(new RawId(Sha1.getHash(obj)));
		if (file.isFile())
			return;  // Object already exists in the loose objects database; no work to do
		File dir = file.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		
		boolean success = false;
		try (OutputStream out = new DeflaterOutputStream(new FileOutputStream(file))) {
			out.write(obj);
			success = true;
		}
		if (!success)
			file.delete();
	}
	
	
	private Collection<PackfileReader> listPackfiles() {
		Collection<PackfileReader> result = new ArrayList<>();
		for (File item : new File(new File(directory, "objects"), "pack").listFiles()) {
			String name = item.getName();
			if (item.isFile() && name.startsWith("pack-") && name.endsWith(".idx")) {
				File packfile = new File(item.getParentFile(), name.substring(0, name.length() - 3) + "pack");
				if (packfile.isFile())
					result.add(new PackfileReader(item, packfile));
			}
		}
		return result;
	}
	
	
	public Collection<Reference> listReferences() throws IOException, DataFormatException {
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		
		// Scan loose ref files
		Collection<Reference> result = new ArrayList<>();
		File headsDir = new File(new File(directory, "refs"), "heads");
		if (headsDir.isDirectory())
			listLooseReferences("heads", result);
		File remotesDir = new File(new File(directory, "refs"), "remotes");
		if (remotesDir.isDirectory()) {
			for (File item : remotesDir.listFiles()) {
				if (item.isDirectory())
					listLooseReferences("remotes/" + item.getName(), result);
			}
		}
		
		Set<String> names = new HashSet<>();
		for (Reference ref : result)
			names.add(ref.name);
		
		// Parse packed refs file
		for (Reference ref : parsePackedRefsFile()) {
			if (names.add(ref.name))
				result.add(ref);
		}
		return result;
	}
	
	
	public Reference readReference(String name) throws IOException, DataFormatException {
		Reference.checkName(name);
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		
		File looseRefFile = new File(new File(directory, "refs"), name);
		if (looseRefFile.isFile())
			return parseReferenceFile(name.substring(0, name.lastIndexOf('/')), looseRefFile);
		else {
			for (Reference ref : parsePackedRefsFile()) {
				if (ref.name.equals(name))
					return ref;
			}
			return null;
		}
	}
	
	
	public void writeReference(Reference ref) throws IOException {
		if (ref == null || ref.target == null)
			throw new NullPointerException();
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		
		File looseRefFile = new File(new File(directory, "refs"), ref.name);
		looseRefFile.getParentFile().mkdirs();
		boolean success = false;
		try (Writer out = new OutputStreamWriter(new FileOutputStream(looseRefFile), StandardCharsets.US_ASCII)) {
			out.write(ref.target.hexString + "\n");
			success = true;
		}
		if (!success)
			looseRefFile.delete();
	}
	
	
	/*---- Private helper methods ----*/
	
	private void listLooseReferences(String subDirName, Collection<Reference> result) throws IOException, DataFormatException {
		for (File item : new File(new File(directory, "refs"), subDirName.replace('/', File.separatorChar)).listFiles()) {
			if (item.isFile() && !item.getName().equals("HEAD"))
				result.add(parseReferenceFile(subDirName, item));
		}
	}
	
	
	// Returns an unordered collection of references (pairs of name, commit ID) from parsing the "packed-refs" file in the repository.
	// Note that the returned reference names are like "heads/master", and do not contain a "refs/" prefix.
	private Collection<Reference> parsePackedRefsFile() throws IOException, DataFormatException {
		Collection<Reference> result = new ArrayList<>();
		File packedRefFile = new File(directory, "packed-refs");
		if (!packedRefFile.isFile())
			return result;  // Empty but valid collection
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(packedRefFile), StandardCharsets.UTF_8))) {
			if (!checkPackedRefsFileHeaderLine(in.readLine()))
				throw new DataFormatException("Invalid packed-refs file");
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;
				String[] parts = line.split(" ", 2);
				if (parts.length == 1) {
					if (parts[0].startsWith("^"))
						continue;
					else
						throw new DataFormatException("Invalid packed-refs file");
				} else if (parts.length == 2) {
					if (!parts[1].startsWith("refs/"))
						throw new DataFormatException("Invalid packed-refs file");
					Reference ref = new Reference(parts[1].substring("refs/".length()), new CommitId(parts[0]));
					if (!ref.name.startsWith("tags/"))
						result.add(ref);
				} else
					throw new AssertionError();
			}
		}
		return result;
	}
	
	
	// Tests whether the given string is a valid header line for the packed-refs text file.
	private static boolean checkPackedRefsFileHeaderLine(String line) {
		return line.equals("# pack-refs with: peeled ") || line.equals("# pack-refs with: peeled fully-peeled ");
	}
	
	
	// Reads the file at the given location and returns the SHA-1 hash string that was in the file.
	// subDirName is usually something like "heads" or "remotes/origin" or "tags".
	private Reference parseReferenceFile(String subDirName, File file) throws IOException, DataFormatException {
		byte[] buf = new byte[41];
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			in.readFully(buf);
			if (buf[40] != '\n' || in.read() != -1)
				throw new DataFormatException("Invalid reference file");
		}
		return new Reference(subDirName + "/" + file.getName(), new CommitId(new String(buf, 0, 40, StandardCharsets.US_ASCII)));
	}
	
	
	// Returns the expected location of a loose object file with the given hash. This performs no I/O and always succeeds.
	private File getLooseObjectFile(ObjectId id) {
		File temp = new File(directory, "objects");
		temp = new File(temp, id.hexString.substring(0, 2));
		return new File(temp, id.hexString.substring(2));
	}
	
}
