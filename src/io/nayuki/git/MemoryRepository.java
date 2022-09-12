package io.nayuki.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * A Git repository that stores objects and references in memory. This class by itself provides useful
 * functionality, but is also extensible to allow subclasses to intercept calls, add new state, and add
 * actions. This implementation carefully makes every method freestanding so that no method in this class
 * calls another method in this class. This implementation is not thread-safe, but subclasses can be.
 */
public class MemoryRepository implements Repository {
	
	/*---- Fields ----*/
	
	/**
	 * The set of currently stored objects. No key or value is {@code null}.
	 * This is not {@code null} before {{@link #close()} is called, and {@code null} thereafter.
	 */
	protected SortedMap<ObjectId,byte[]> objects;
	
	/**
	 * The set of currently stored references. No key or value is {@code null}.
	 * This is not {@code null} before {{@link #close()} is called, and {@code null} thereafter.
	 */
	protected Map<String,CommitId> references;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs an empty memory-based Git repository, initially containing no objects and references.
	 */
	public MemoryRepository() {
		objects = new TreeMap<>();
		references = new HashMap<>();
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the unique object ID in this repository that matches the specified hexadecimal prefix.
	 * @param prefix the hexadecimal prefix, case insensitive, between 0 to 40 characters long (not {@code null})
	 * @return the ID of an object that exists in this repository and where the
	 * specified string is a prefix of that object's hexadecimal ID (not {@code null})
	 * @throws NullPointerException if the prefix is {@code null}
	 * @throws IllegalArgumentException if the prefix has non-hexadecimal characters or is over 40 chars long, or
	 * if there is no unique match - either zero or multiple objects have an ID with the specified hexadecimal prefix
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public ObjectId getIdByPrefix(String prefix) throws IOException {
		Objects.requireNonNull(prefix);
		if (prefix.length() > ObjectId.NUM_HEX_DIGITS)
			throw new IllegalArgumentException("Prefix too long");
		if (!prefix.matches("[0-9a-fA-F]*"))
			throw new IllegalArgumentException("Prefix contains non-hexadecimal characters");
		checkNotClosed();
		
		StringBuilder prefixLow = new StringBuilder(prefix);
		while (prefixLow.length() < ObjectId.NUM_HEX_DIGITS)
			prefixLow.append('0');
		CommitId prefixId = new CommitId(prefixLow.toString());
		
		ObjectId result = null;
		for (ObjectId id : objects.tailMap(prefixId).keySet()) {
			if (!id.hexString.startsWith(prefix))
				break;
			else if (result != null)
				throw new IllegalArgumentException("Multiple object IDs found");
			else
				result = id;
		}
		if (result == null)
			throw new IllegalArgumentException("No matching object ID found");
		return result;
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
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public Set<ObjectId> getIdsByPrefix(String prefix) throws IOException {
		Objects.requireNonNull(prefix);
		if (prefix.length() > ObjectId.NUM_HEX_DIGITS)
			throw new IllegalArgumentException("Prefix too long");
		if (!prefix.matches("[0-9a-fA-F]*"))
			throw new IllegalArgumentException("Prefix contains non-hexadecimal characters");
		checkNotClosed();
		
		StringBuilder prefixLow = new StringBuilder(prefix);
		while (prefixLow.length() < ObjectId.NUM_HEX_DIGITS)
			prefixLow.append('0');
		CommitId prefixId = new CommitId(prefixLow.toString());
		
		Set<ObjectId> result = new HashSet<>();
		for (ObjectId id : objects.tailMap(prefixId).keySet()) {
			if (id.hexString.startsWith(prefix))
				result.add(id);
			else
				break;
		}
		return result;
	}
	
	
	/**
	 * Tests whether this repository contains an object with the specified hash.
	 * @param id the hash of the object (not {@code null})
	 * @return {@code true} if the repo has at least one copy of the object, {@code false} if it has none
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public boolean containsObject(ObjectId id) throws IOException {
		Objects.requireNonNull(id);
		checkNotClosed();
		return objects.containsKey(id);
	}
	
	
	/**
	 * Reads the Git object with the specified hash from this repository, parses it, and returns it.
	 * @param id the hash of the object (not {@code null})
	 * @return the parsed object with the specified hash (not {@code null})
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalArgumentException if no object with the ID was found
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public GitObject readObject(ObjectId id) throws IOException {
		Objects.requireNonNull(id);
		checkNotClosed();
		try {
			// Get object bytes and extract header
			byte[] bytes = objects.get(id);
			if (bytes == null)
				throw new IllegalArgumentException("No object with the ID found");
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
			return switch (type) {
				case "blob"   -> new BlobObject  (bytes);
				case "tree"   -> new TreeObject  (bytes);
				case "commit" -> new CommitObject(bytes);
				case "tag"    -> new TagObject   (bytes);
				default -> throw new GitFormatException("Unknown object type: " + type);
			};
		} catch (IOException e) {  // Includes GitFormatException and EOFException
			throw new AssertionError(e);
		}
	}
	
	
	/**
	 * Writes the specified Git object to this repository if it doesn't already exist.
	 * @param obj the object to write (not {@code null})
	 * @throws NullPointerException if the object is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public void writeObject(GitObject obj) throws IOException {
		Objects.requireNonNull(obj);
		checkNotClosed();
		ObjectId id = obj.getId();
		if (objects.containsKey(id))
			return;
		objects.put(id, obj.toBytes());
	}
	
	
	/**
	 * Reads and returns a collection of all known references in this repository.
	 * @return a new collection of references based on this repo's data (not {@code null})
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public Collection<Reference> listReferences() throws IOException {
		checkNotClosed();
		Collection<Reference> result = new ArrayList<>();
		for (Map.Entry<String,CommitId> entry : references.entrySet())
			result.add(new Reference(entry.getKey(), entry.getValue()));
		return result;
	}
	
	
	/**
	 * Reads and returns a reference for the specified name, or {@code null} if not found.
	 * @param name the name to query (not {@code null})
	 * @return a new reference of the specified name or {@code null}
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public Reference readReference(String name) throws IOException {
		Objects.requireNonNull(name);
		checkNotClosed();
		CommitId target = references.get(name);
		if (target == null)
			return null;
		else
			return new Reference(name, target);
	}
	
	
	/**
	 * Writes the specified reference to the repository.
	 * @param ref the reference to write (not {@code null})
	 * @throws NullPointerException if the reference or target is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public void writeReference(Reference ref) throws IOException {
		Objects.requireNonNull(ref);
		checkNotClosed();
		references.put(ref.name, ref.target);
	}
	
	
	/**
	 * Deletes the reference with the specified name from this repository.
	 * If no reference with the name exists, then nothing happens.
	 * @param name the name to delete (not {@code null})
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public void deleteReference(String name) throws IOException {
		Objects.requireNonNull(name);
		checkNotClosed();
		references.remove(name);
	}
	
	
	/**
	 * Discards all stored data in this repository (objects and references) and invalidates this object.
	 * After closing, no other method can be called on this object. This has no effect if called more than once.
	 * This class only uses memory and no native resources, so it is not strictly necessary to close this object
	 * when code finishes using it. However it is good practice to close every repository, and subclasses may
	 * add resources that need explicit disposal.
	 * @throws IOException if an I/O exception occurred (not thrown by this class, but subclasses may)
	 */
	public void close() throws IOException {
		objects = null;
		references = null;
	}
	
	
	// Returns silently if this repo is still valid, otherwise throws an exception.
	private void checkNotClosed() {
		if (objects == null)
			throw new IllegalStateException("Repository already closed");
	}
	
}
