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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;


/**
 * A repository based on files and directories in the file system.
 * @see ObjectId
 * @see GitObject
 * @see Reference
 */
public final class FileRepository implements Repository {
	
	/*---- Fields ----*/
	
	// Initially not null, but becomes null after close() is called.
	private File directory;
	private File objectsDir;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a repository object based on the specified directory path.
	 * <p>The directory must exist and already be initialized as a Git repository.
	 * The directory must be either a bare repository or the repository subdirectory (".git") of a particular working tree;
	 * in particular a working tree directory will not be searched to find where the repository is actually located.</p>
	 * @param dir the repository directory (not {@code null})
	 * @throws NullPointerException if the directory is null
	 * @throws IllegalArgumentException if the directory does not contain a valid existing Git repository
	 * @throws IOException if an I/O exception occurred while reading the repository
	 */
	public FileRepository(File dir) throws IOException {
		Objects.requireNonNull(dir);
		if (!dir.isDirectory())
			throw new IllegalArgumentException("Repository directory does not exist");
		objectsDir = new File(dir, "objects");
		if (!new File(dir, "config").isFile() || !objectsDir.isDirectory())
			throw new IllegalArgumentException("Invalid repository format");
		directory = dir;
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the directory associated with this repository object,
	 * or {@code null} if and only if the repository has been closed.
	 * @return the repository's directory or {@code null}
	 */
	public File getDirectory() {
		return directory;
	}
	
	
	/**
	 * Disposes any resources associated with this repository object and invalidates this object.
	 * This method must be called when finished using a repository. This has no effect if called more than once.
	 * <p>The method may close file streams and removed cached data. It is illegal
	 * to use fields or call methods on this repository object after closing.</p>
	 * @throws IOException if an I/O exception occurred
	 */
	public void close() throws IOException {
		directory = null;
		objectsDir = null;
	}
	
	
	/**
	 * Returns the unique object ID in this repository that matches the specified hexadecimal prefix.
	 * @param prefix the hexadecimal prefix, case insensitive, between 0 to 40 characters long (not {@code null})
	 * @return the ID of an object that exists in this repository and where the
	 * specified string is a prefix of that object's hexadecimal ID (not {@code null})
	 * @throws NullPointerException if the prefix is {@code null}
	 * @throws IllegalArgumentException if the prefix has non-hexadecimal characters or is over 40 chars long, or
	 * if there is no unique match - either zero or multiple objects have an ID with the specified hexadecimal prefix
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public ObjectId getIdByPrefix(String prefix) throws IOException {
		Set<ObjectId> temp = getIdsByPrefix(prefix);
		switch (temp.size()) {
			case 0 :  throw new IllegalArgumentException("No matching object ID found");
			case 1 :  return temp.iterator().next();
			default:  throw new IllegalArgumentException("Multiple object IDs found");
		}
	}
	
	
	/**
	 * Returns the set of all object IDs in this repository that match the specified hexadecimal prefix.
	 * Note that {@code getIdsByPrefix("")} will list all object IDs in this repository,
	 * since the empty string is a prefix of every string.
	 * @param prefix the hexadecimal prefix, case insensitive, between 0 to 40 characters long (not {@code null})
	 * @return a new set of object IDs matching the prefix, of size at least 0 (not {@code null})
	 * @throws NullPointerException if the prefix is {@code null}
	 * @throws IllegalArgumentException if the prefix has non-hexadecimal characters or is over 40 chars long
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public Set<ObjectId> getIdsByPrefix(String prefix) throws IOException {
		Objects.requireNonNull(prefix);
		if (prefix.length() > ObjectId.NUM_HEX_DIGITS)
			throw new IllegalArgumentException("Prefix too long");
		if (!prefix.matches("[0-9a-fA-F]*"))
			throw new IllegalArgumentException("Prefix contains non-hexadecimal characters");
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		
		prefix = prefix.toLowerCase();
		Set<ObjectId> result = new HashSet<>();
		
		// Check loose objects
		if (prefix.length() < 2) {
			for (File item : objectsDir.listFiles()) {
				String itemName = item.getName();
				if (item.isDirectory() && itemName.length() == 2 && itemName.toLowerCase().startsWith(prefix)) {
					for (File subitem : item.listFiles()) {
						String subitemName = subitem.getName();
						if (subitem.isFile() && subitemName.matches("[0-9a-fA-F]{38}"))
							result.add(new RawId(itemName + subitemName));
					}
				}
			}
		} else {
			String itemName = prefix.substring(0, 2);
			File item = new File(objectsDir, itemName);
			if (item.isDirectory()) {
				String subprefix = prefix.substring(2);
				for (File subitem : item.listFiles()) {
					String subitemName = subitem.getName();
					if (subitem.isFile() && subitemName.matches("[0-9a-fA-F]{38}") && subitemName.toLowerCase().startsWith(subprefix))
						result.add(new RawId(itemName + subitemName));
				}
			}
		}
		
		// Check pack files
		for (PackfileReader pfr : listPackfiles())
			pfr.getIdsByPrefix(prefix, result);
		return result;
	}
	
	
	/**
	 * Tests whether this repository contains an object with the specified hash.
	 * @param id the hash of the object (not {@code null})
	 * @return {@code true} if the repo has at least one copy of the object, {@code false} if it has none
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public boolean containsObject(ObjectId id) throws IOException {
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
	
	
	// Reads the object in the repository with the given hash, checks the hash, and returns the byte array.
	// This does not check whether the object has a valid header or data format.
	private byte[] readRawObject(ObjectId id) throws IOException {
		// Try to read the object data bytes from loose file or pack files
		byte[] result = null;
		File looseFile = getLooseObjectFile(id);
		if (looseFile.isFile()) {  // Read from loose object store
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			try (OutputStream out = new InflaterOutputStream(bout)) {
				Files.copy(looseFile.toPath(), out);
			}
			result = bout.toByteArray();
			
		} else {  // Scan pack files
			for (PackfileReader pfr : listPackfiles()) {
				result = pfr.readRawObject(id);
				if (result != null)
					break;
			}
		}
		
		// Check the bytes and return result
		if (result != null && !Arrays.equals(GitObject.getSha1Hash(result), id.getBytes()))
			throw new GitFormatException("Hash of data mismatches object ID");
		return result;
	}
	
	
	/**
	 * Reads the Git object with the specified hash from this repository, parses it, and returns it.
	 * @param id the hash of the object (not {@code null})
	 * @return the parsed object with the specified hash (not {@code null})
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalArgumentException if no object with the ID was found
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public GitObject readObject(ObjectId id) throws IOException {
		Objects.requireNonNull(id);
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		
		if (getLooseObjectFile(id).isFile()) {
			// Read object bytes and extract header
			byte[] bytes = readRawObject(id);
			int index = 0;
			while (index < bytes.length && bytes[index] != 0)
				index++;
			if (index >= bytes.length)
				throw new GitFormatException("Invalid object header");
			String header = new String(bytes, 0, index, StandardCharsets.US_ASCII);
			bytes = Arrays.copyOfRange(bytes, index + 1, bytes.length);
			
			// Parse header
			String[] parts = header.split(" ", -1);
			if (parts.length != 2)
				throw new GitFormatException("Invalid object header");
			String type = parts[0];
			int length = Integer.parseInt(parts[1]);
			if (length < 0)
				throw new GitFormatException("Negative data length");
			if (!Integer.toString(length).equals(parts[1]))  // Check for non-canonical number representations like -0, 007, etc.
				throw new GitFormatException("Invalid data length string");
			if (length != bytes.length)
				throw new GitFormatException("Data length mismatch");
			
			// Parse bytes into object
			switch (type) {
				case "blob"  :  return new BlobObject  (bytes);
				case "tree"  :  return new TreeObject  (bytes);
				case "commit":  return new CommitObject(bytes);
				case "tag"   :  return new TagObject   (bytes);
				default:  throw new GitFormatException("Unknown object type: " + type);
			}
			
		} else {
			for (PackfileReader pfr : listPackfiles()) {
				GitObject result = pfr.readObject(id);
				if (result != null)
					return result;
			}
			throw new IllegalArgumentException("No object with the ID found");
		}
	}
	
	
	/**
	 * Writes the specified Git object to this repository if it doesn't already exist.
	 * @param obj the object to write (not {@code null})
	 * @throws NullPointerException if the object is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while writing the object
	 */
	public void writeObject(GitObject obj) throws IOException {
		Objects.requireNonNull(obj);
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		writeRawObject(obj.toBytes());
	}
	
	
	// Writes the given raw byte array (which should normally include headers)
	// to this repository's on-disk storage as a loose object file.
	// This does not check whether the object has a valid header or data format.
	private void writeRawObject(byte[] b) throws IOException {
		// Handle the file path and the 2-digit directory
		File file = getLooseObjectFile(new RawId(GitObject.getSha1Hash(b)));
		if (file.isFile())
			return;  // Object already stored; do nothing
		File dir = file.getParentFile();
		if (!dir.exists())
			dir.mkdirs();
		
		// Write the file or delete if unsuccessful
		boolean success = false;
		try (OutputStream out = new DeflaterOutputStream(new FileOutputStream(file))) {
			out.write(b);
			success = true;
		}
		if (!success)
			file.delete();
	}
	
	
	// Scans the "objects/pack" directory and returns a collection of pack file reader objects.
	private Collection<PackfileReader> listPackfiles() {
		Collection<PackfileReader> result = new ArrayList<>();
		File dir = new File(objectsDir, "pack");
		final String IDX_EXT = ".idx";
		for (File item : dir.listFiles()) {  // Look for index files
			String name = item.getName();
			if (item.isFile() && name.startsWith("pack-") && name.endsWith(IDX_EXT)) {
				File packfile = new File(dir, name.substring(0, name.length() - IDX_EXT.length()) + ".pack");
				if (packfile.isFile())
					result.add(new PackfileReader(item, packfile));
			}
		}
		return result;
	}
	
	
	/**
	 * Reads and returns a collection of all known references in this repository.
	 * @return a new collection of references based on this repo's data (not {@code null})
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public Collection<Reference> listReferences() throws IOException {
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
	
	
	/**
	 * Reads and returns a reference for the specified name, or {@code null} if not found.
	 * @param name the name to query (not {@code null})
	 * @return a new reference of the specified name or {@code null}
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public Reference readReference(String name) throws IOException {
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
	
	
	/**
	 * Writes the specified reference to the repository.
	 * @param ref the reference to write (not {@code null})
	 * @throws NullPointerException if the reference or target is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while writing references
	 */
	public void writeReference(Reference ref) throws IOException {
		Objects.requireNonNull(ref);
		Objects.requireNonNull(ref.target);
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
	
	
	/**
	 * Deletes the reference with the specified name from this repository.
	 * If no reference with the name exists, then nothing happens.
	 * @param name the name to delete (not {@code null})
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while changing references
	 */
	public void deleteReference(String name) throws IOException {
		Objects.requireNonNull(name);
		if (directory == null)
			throw new IllegalStateException("Repository already closed");
		throw new UnsupportedOperationException("Not implemented");
	}
	
	
	
	/*---- Private helper methods ----*/
	
	// Scans all loose reference files in the given subdirectory name and adds them to the given collection of results.
	private void listLooseReferences(String subDirName, Collection<Reference> result) throws IOException {
		for (File item : new File(new File(directory, "refs"), subDirName.replace('/', File.separatorChar)).listFiles()) {
			if (item.isFile() && !item.getName().equals("HEAD"))
				result.add(parseReferenceFile(subDirName, item));
		}
	}
	
	
	// Returns an unordered collection of references (pairs of name, commit ID) from parsing the "packed-refs" file in the repository.
	// Note that the returned reference names are like "heads/master", and do not contain a "refs/" prefix.
	private Collection<Reference> parsePackedRefsFile() throws IOException {
		Collection<Reference> result = new ArrayList<>();
		File packedRefFile = new File(directory, "packed-refs");
		if (!packedRefFile.isFile())
			return result;  // Empty but valid collection
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(packedRefFile), StandardCharsets.UTF_8))) {
			if (!checkPackedRefsFileHeaderLine(in.readLine()))
				throw new GitFormatException("Invalid packed-refs file");
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;
				String[] parts = line.split(" ", 2);
				if (parts.length == 1) {
					if (parts[0].startsWith("^"))
						continue;
					else
						throw new GitFormatException("Invalid packed-refs file");
				} else if (parts.length == 2) {
					if (!parts[1].startsWith("refs/"))
						throw new GitFormatException("Invalid packed-refs file");
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
	// The file must contain exactly 40 hexadecimal digits followed by a newline (total 41 bytes).
	private Reference parseReferenceFile(String subDirName, File file) throws IOException {
		byte[] buf = new byte[ObjectId.NUM_HEX_DIGITS + 1];
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			in.readFully(buf);
			if (buf[buf.length - 1] != '\n' || in.read() != -1)
				throw new GitFormatException("Invalid reference file");
		}
		return new Reference(subDirName + "/" + file.getName(), new CommitId(new String(buf, 0, buf.length - 1, StandardCharsets.US_ASCII)));
	}
	
	
	// Returns the expected location of a loose object file with the given hash. This performs no I/O and always succeeds.
	// For example, a repo at "user/project.git" has a loose object of hash 12345xyz at "user/project.git/objects/12/345xyz".
	private File getLooseObjectFile(ObjectId id) {
		File temp = new File(objectsDir, id.hexString.substring(0, 2));
		return new File(temp, id.hexString.substring(2));
	}
	
}
