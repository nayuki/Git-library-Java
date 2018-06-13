/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;


/**
 * Represents a Git repository - which could be on disk or in memory or over the network,
 * mutable or immutable, thread-safe or unsafe. A repository doesn't need to support all
 * the defined methods (e.g. throwing {@link UnsupportedOperationException} upon writing).
 * @see FileRepository
 * @see ObjectId
 * @see GitObject
 * @see Reference
 */
public interface Repository extends AutoCloseable {
	
	/*---- Methods for object IDs ----*/
	
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
	public ObjectId getIdByPrefix(String prefix) throws IOException;
	
	
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
	public Set<ObjectId> getIdsByPrefix(String prefix) throws IOException;
	
	
	/*---- Methods for Git objects ----*/
	
	/**
	 * Tests whether this repository contains an object with the specified hash.
	 * @param id the hash of the object (not {@code null})
	 * @return {@code true} if the repo has at least one copy of the object, {@code false} if it has none
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public boolean containsObject(ObjectId id) throws IOException;
	
	
	/**
	 * Reads the Git object with the specified hash from this repository, parses it, and returns it.
	 * @param id the hash of the object (not {@code null})
	 * @return the parsed object with the specified hash (not {@code null})
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalArgumentException if no object with the ID was found
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public GitObject readObject(ObjectId id) throws IOException;
	
	
	/**
	 * Writes the specified Git object to this repository if it doesn't already exist.
	 * @param obj the object to write (not {@code null})
	 * @throws NullPointerException if the object is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while writing the object
	 */
	public void writeObject(GitObject obj) throws IOException;
	
	
	/*---- Methods for references ----*/
	
	/**
	 * Reads and returns a collection of all known references in this repository.
	 * @return a new collection of references based on this repo's data (not {@code null})
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public Collection<Reference> listReferences() throws IOException;
	
	
	/**
	 * Reads and returns a reference for the specified name, or {@code null} if not found.
	 * @param name the name to query (not {@code null})
	 * @return a new reference of the specified name or {@code null}
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public Reference readReference(String name) throws IOException;
	
	
	/**
	 * Writes the specified reference to the repository.
	 * @param ref the reference to write (not {@code null})
	 * @throws NullPointerException if the reference or target is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while writing references
	 */
	public void writeReference(Reference ref) throws IOException;
	
	
	/**
	 * Deletes the reference with the specified name from this repository.
	 * If no reference with the name exists, then nothing happens.
	 * @param name the name to delete (not {@code null})
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while changing references
	 */
	public void deleteReference(String name) throws IOException;
	
	
	/*---- Miscellaneous methods ----*/
	
	/**
	 * Disposes any resources associated with this repository object and invalidates this object.
	 * This method must be called when finished using a repository. This has no effect if called more than once.
	 * <p>The method may close file streams and removed cached data. It is illegal
	 * to use fields or call methods on this repository object after closing.</p>
	 * @throws IOException if an I/O exception occurred
	 */
	public void close() throws IOException;
	
}
