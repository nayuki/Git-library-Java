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


/**
 * Represents a Git tree, which can be loosely thought of as a directory. Mutable structure.
 */
public final class TreeObject extends GitObject {
	
	/*---- Fields ----*/
	
	/**
	 * The list of children of this tree node. Each element must be not {@code null},
	 * and no two elements can have the same name.
	 */
	public List<TreeEntry> entries;
	
	
	
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
	 * @param srcRepo the repository to set for object IDs
	 * @throws NullPointerException if the array is {@code null}
	 */
	public TreeObject(byte[] data, WeakReference<Repository> srcRepo) {
		this();
		if (data == null)
			throw new NullPointerException();
		
		int index = 0;
		while (index < data.length) {
			int start = index;
			while (data[index] != ' ')
				index++;
			int mode = Integer.parseInt(new String(data, start, index - start, StandardCharsets.US_ASCII), 8);  // Parse number as octal
			index++;
			
			start = index;
			while (data[index] != 0)
				index++;
			String name = new String(data, start, index - start, StandardCharsets.UTF_8);
			index++;
			
			byte[] hash = Arrays.copyOfRange(data, index, index + ObjectId.NUM_BYTES);
			index += ObjectId.NUM_BYTES;
			entries.add(new TreeEntry(TreeEntry.Type.fromMode(mode), name, hash, srcRepo));
		}
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the list entry whose name matches the specified name, or {@code null} if none match.
	 * Note that the functionality of this method dictates that the list should have no duplicate names.
	 * @param name the name to query
	 * @return an entry with a matching name, or {@code null}
	 */
	public TreeEntry getEntry(String name) {
		if (name == null)
			throw new NullPointerException();
		for (TreeEntry entry : entries) {
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
			for (TreeEntry entry : entries) {
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
	
}
