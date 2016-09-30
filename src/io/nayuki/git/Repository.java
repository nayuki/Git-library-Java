/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;
import java.util.Collection;
import java.util.zip.DataFormatException;


/**
 * Represents a Git repository - which could be on disk or in memory or over the network, and mutable or immutable.
 * @see FileRepository
 * @see ObjectId
 * @see GitObject
 * @see Reference
 */
public interface Repository extends AutoCloseable {
	
	/*---- Methods for Git objects ----*/
	
	/**
	 * Tests whether this repository contains an object with the specified hash.
	 * @param id the hash of the object (not {@code null})
	 * @return {@code true} if the repo has at least one copy of the object, {@code false} if it has none
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while testing for the object
	 * @throws DataFormatException if malformed data was encountered while testing for the object
	 */
	public boolean containsObject(ObjectId id) throws IOException, DataFormatException;
	
	
	/**
	 * Reads the Git object with the specified hash from this repository,
	 * parses it, and returns it - or {@code null} if the object was not found.
	 * @param id the hash of the object (not {@code null})
	 * @return the parsed object with the specified hash, or {@code null} if not found in the repo
	 * @throws NullPointerException if the ID is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while reading the object
	 * @throws DataFormatException if malformed data was encountered while reading the object
	 */
	public GitObject readObject(ObjectId id) throws IOException, DataFormatException;
	
	
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
	 * @throws IOException if an I/O exception occurred while reading references
	 * @throws DataFormatException if malformed data was encountered while reading references
	 */
	public Collection<Reference> listReferences() throws IOException, DataFormatException;
	
	
	/**
	 * Reads and returns a reference for the specified name, or {@code null} if not found.
	 * @param name the name to query (not {@code null})
	 * @return a new reference of the specified name or {@code null}
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while reading references
	 * @throws DataFormatException if malformed data was encountered while reading references
	 */
	public Reference readReference(String name) throws IOException, DataFormatException;
	
	
	/**
	 * Writes the specified reference to the repository.
	 * @param ref the reference to write (not {@code null})
	 * @throws NullPointerException if the reference or target is {@code null}
	 * @throws IllegalStateException if this repository is already closed
	 * @throws IOException if an I/O exception occurred while writing references
	 */
	public void writeReference(Reference ref) throws IOException;
	
	
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
