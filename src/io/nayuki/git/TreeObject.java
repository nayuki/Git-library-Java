/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;


/**
 * Represents a Git tree, which can be loosely thought of as a directory. Mutable structure.
 * @see TreeId
 * @see BlobObject
 * @see CommitObject
 */
public final class TreeObject extends GitObject {
	
	/*---- Fields ----*/
	
	/**
	 * The list of children of this tree node. Each element must be not {@code null},
	 * and no two elements can have the same name.
	 */
	public List<Entry> entries;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a tree object with an empty (but non-{@code null}) list of entries.
	 */
	public TreeObject() {
		entries = new ArrayList<>();
	}
	
	
	/**
	 * Constructs a tree object with the data initially set to the parsed interpretation of the specified bytes.
	 * Every object ID that the tree refers to will have its repository set to the specified repo argument.
	 * @param data the serialized tree data to read
	 * @param repo the repository to set for object IDs (can be {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws DataFormatException if malformed data was encountered during reading
	 */
	public TreeObject(byte[] data, WeakReference<Repository> repo) throws DataFormatException {
		this();
		if (data == null)
			throw new NullPointerException();
		
		int index = 0;
		while (index < data.length) {
			// Scan for the next space
			int start = index;
			while (true) {
				if (index >= data.length)
					throw new DataFormatException("Unexpected end of tree data");
				else if (data[index] == ' ')
					break;
				else
					index++;
			}
			
			// Resolve the mode value
			String modeStr = new String(data, start, index - start, StandardCharsets.US_ASCII);
			int modeInt;
			try {
				modeInt = Integer.parseInt(modeStr, 8);  // Parse number as octal
			} catch (NumberFormatException e) {
				throw new DataFormatException("Invalid mode value");
			}
			Entry.Type mode;
			try {
				mode = Entry.Type.fromMode(modeInt);
			} catch (IllegalArgumentException e) {
				throw new DataFormatException("Unrecognized mode value");
			}
			index++;
			
			// Scan for the next NUL and decode the item name
			start = index;
			while (true) {
				if (index >= data.length)
					throw new DataFormatException("Unexpected end of tree data");
				else if (data[index] == '\0')
					break;
				else
					index++;
			}
			String name = new String(data, start, index - start, StandardCharsets.UTF_8);
			index++;
			
			// Grab the hash bytes and create new entry
			if (data.length - index < ObjectId.NUM_BYTES)
				throw new DataFormatException("Unexpected end of tree data");
			byte[] hash = Arrays.copyOfRange(data, index, index + ObjectId.NUM_BYTES);
			index += ObjectId.NUM_BYTES;
			entries.add(new Entry(mode, name, hash, repo));
		}
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the list entry whose name matches the specified name, or {@code null} if none match.
	 * Note that the functionality of this method dictates that the list should have no duplicate names.
	 * @param name the name to query
	 * @return an entry with a matching name, or {@code null}
	 */
	public Entry getEntry(String name) {
		if (name == null)
			throw new NullPointerException();
		for (Entry entry : entries) {
			if (entry.name.equals(name))
				return entry;
		}
		return null;
	}
	
	
	/**
	 * Returns the raw byte serialization of the current state of this tree object, including a lightweight header.
	 * @return the raw byte serialization of this object
	 * @throws NullPointerException if the list of entries is {@code null}
	 */
	public byte[] toBytes() {
		if (entries == null)
			throw new NullPointerException();
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			for (Entry entry : entries) {
				out.write(String.format("%o %s\0", entry.type.mode, entry.name).getBytes(StandardCharsets.UTF_8));  // Format number as octal
				out.write(entry.id.getBytes());
			}
			return addHeader("tree", out.toByteArray());
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}
	
	
	/**
	 * Returns the hash ID of the current state of this tree object.
	 * @return the hash ID of this tree object
	 */
	public TreeId getId() {
		return new TreeId(Sha1.getHash(toBytes()), null);
	}
	
	
	/**
	 * Returns a string representation of this tree object. The format is subject to change.
	 * @return a string representation of this tree object
	 */
	public String toString() {
		return String.format("TreeObject(entries=%d)", entries.size());
	}
	
	
	
	/*---- Nested classes ----*/
	
	public final static class Entry {
		
		public final Type type;
		public final String name;
		public final ObjectId id;
		
		
		
		public Entry(Type type, String name, byte[] hash, WeakReference<Repository> repo) {
			if (name == null || hash == null)
				throw new NullPointerException();
			if (name.indexOf('\0') != -1)
				throw new IllegalArgumentException("Name cannot contain NUL character");
			
			this.type = type;
			this.name = name;
			if (type == Type.NORMAL_FILE || type == Type.EXECUTABLE_FILE)
				id = new BlobId(hash, repo);
			else if (type == Type.DIRECTORY)
				id = new TreeId(hash, repo);
			else
				throw new IllegalArgumentException();
		}
		
		
		
		public String toString() {
			return String.format("TreeEntry(mode=%s, name=\"%s\", id=%s)", Integer.toString(type.mode, 8), name, id.hexString);
		}
		
		
		
		public enum Type {
			
			// Numbers are in octal
			DIRECTORY      (0040000),
			NORMAL_FILE    (0100644),
			EXECUTABLE_FILE(0100755),
			SYMBOLIC_LINK  (0120000);
			
			
			public final int mode;
			
			
			private Type(int mode) {
				this.mode = mode;
			}
			
			
			public static Type fromMode(int mode) {
				for (Type type : VALUES) {
					if (mode == type.mode)
						return type;
				}
				throw new IllegalArgumentException("Unknown mode: octal " + Integer.toString(mode, 8));
			}
			
			
			private static final Type[] VALUES = values();
			
		}
		
	}
	
}
