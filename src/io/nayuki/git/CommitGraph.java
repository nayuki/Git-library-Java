/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


/**
 * A graph commits, which tracks parent and child relationships. Mutable and not thread-safe.
 * @see CommitId
 * @see CommitObject
 * @see Repository
 */
public final class CommitGraph {
	
	/*---- Fields ----*/
	
	private Map<CommitId,Set<CommitId>> idToParents;
	private Map<CommitId,Set<CommitId>> idToChildren;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a new, initially empty commit graph.
	 */
	public CommitGraph() {
		idToParents  = new HashMap<>();
		idToChildren = new HashMap<>();
	}
	
	
	
	/*---- Methods ----*/
	
	/* Methods to add commits */
	
	/**
	 * Adds the specified commit object to the graph's database.
	 * @param obj the commit object to add (not {@code null})
	 * @throws NullPointerException if the commit object is {@code null}
	 * @throws IllegalStateException if the object has invalid
	 * field values that prevent it from being serialized
	 */
	public void addCommit(CommitObject obj) {
		if (obj == null)
			throw new NullPointerException();
		addCommit(obj.getId(), obj);
	}
	
	
	/**
	 * Reads the commit with the specified ID from the specified repository and
	 * adds it to this graph's database. For efficiency, this returns
	 * the commit object read if the caller needs to do further processing.
	 * @param repo the repository to read from (not {@code null})
	 * @param id the commit ID to query and add (not {@code null})
	 * @return a new commit object that was read from the repository
	 * @throws NullPointerException if the repository or commit ID is {@code null}
	 * @throws IllegalArgumentException if the commit ID was not found in the repository
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public CommitObject addCommit(Repository repo, CommitId id) throws IOException {
		if (repo == null || id == null)
			throw new NullPointerException();
		CommitObject obj = id.read(repo);
		if (obj == null)
			throw new IllegalArgumentException("Commit object with the given ID not found in repository");
		addCommit(id, obj);
		return obj;
	}
	
	
	// Adds the given commit ID and object (which must match each other) to this graph's database.
	private void addCommit(CommitId id, CommitObject obj) {
		if (idToParents.containsKey(id))
			return;
		idToParents.put(id, new HashSet<>(obj.parents));
		if (!idToChildren.containsKey(id))
			idToChildren.put(id, new HashSet<CommitId>());
		for (CommitId parent : obj.parents) {
			if (!idToChildren.containsKey(parent))
				idToChildren.put(parent, new HashSet<CommitId>());
			idToChildren.get(parent).add(id);
		}
	}
	
	
	/**
	 * Reads the commit objects with the specified IDs and their entire past history
	 * from the repository, and adds all of this data to this graph's database.
	 * @param repo the repository to read from (not {@code null})
	 * @param startIds zero or more commit IDs whose histories
	 * to query (array not {@code null} and no element {@code null})
	 * @throws NullPointerException if the repository or any commit ID is {@code null}
	 * @throws IllegalArgumentException if a commit ID in the
	 * specified list or in the history was not found in the repository
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public void addHistory(Repository repo, CommitId... startIds) throws IOException {
		if (repo == null || startIds == null)
			throw new NullPointerException();
		addHistory(repo, Arrays.asList(startIds));
	}
	
	
	/**
	 * Reads the commit objects with the specified IDs and their entire past history
	 * from the repository, and adds all of this data to this graph's database.
	 * @param repo the repository to read from (not {@code null})
	 * @param startIds zero or more commit IDs whose histories
	 * to query (collection not {@code null} and no element {@code null})
	 * @throws NullPointerException if the repository or any commit ID is {@code null}
	 * @throws IllegalArgumentException if a commit ID in the
	 * specified list or in the history was not found in the repository
	 * @throws IOException if an I/O exception occurred or malformed data was encountered
	 */
	public void addHistory(Repository repo, Collection<CommitId> startIds) throws IOException {
		if (repo == null || startIds == null)
			throw new NullPointerException();
		Queue<CommitId> queue = new ArrayDeque<>();
		Set<CommitId> visited = new HashSet<>();
		queue.addAll(startIds);
		while (!queue.isEmpty()) {
			CommitId id = queue.remove();
			if (!visited.add(id))
				continue;
			CommitObject obj = id.read(repo);
			if (obj == null)
				throw new IllegalArgumentException("Commit object with the given ID not found in repository");
			addCommit(id, obj);
			queue.addAll(obj.parents);
		}
	}
	
	
	/* Methods to query the graph */
	
	/**
	 * Returns the set of keys for the ID-to-parents map. The returned set is a
	 * read-only view, but its contents change when the underlying graph database is changed.
	 * @return the set of keys for the ID-to-parents map
	 */
	public Set<CommitId> getParentsKeys() {
		return Collections.unmodifiableSet(idToParents.keySet());
	}
	
	
	/**
	 * Returns the set of keys for the ID-to-children map. The returned set is a
	 * read-only view, but its contents change when the underlying graph database is changed.
	 * @return the set of keys for the ID-to-children map
	 */
	public Set<CommitId> getChildrenKeys() {
		return Collections.unmodifiableSet(idToChildren.keySet());
	}
	
	
	/**
	 * Returns the set of parents for the specified commit ID, or {@code null} if the ID has not been added.
	 * The returned set is read-only and doesn't change because each commit ID has a fixed set of parents.
	 * @param id the commit ID to query (not {@code null})
	 * @return the set of parents or {@code null}
	 */
	public Set<CommitId> getParents(CommitId id) {
		if (id == null)
			throw new NullPointerException();
		Set<CommitId> temp = idToParents.get(id);
		if (temp != null)
			temp = Collections.unmodifiableSet(temp);
		return temp;
	}
	
	
	/**
	 * Returns the set of currently known children for the specified commit ID. The returned set
	 * is a read-only view, but its contents change when the underlying graph database is changed.
	 * @param id the commit ID to query (not {@code null})
	 * @return the set of children (not {@code null})
	 */
	public Set<CommitId> getChildren(CommitId id) {
		if (id == null)
			throw new NullPointerException();
		Set<CommitId> temp = idToChildren.get(id);
		if (temp != null)
			return Collections.unmodifiableSet(temp);
		else
			return Collections.emptySet();
	}
	
	
	/**
	 * Returns the set of commit IDs associated with commit objects that are known to have zero parents.
	 * The returned information is based on the set of all commits that have been added to this graph's
	 * database, and does not query over some implicit "global" set of objects.
	 * <p>The result set can only grow or stay the same size as more commits are added to the database.
	 * The minimum result set has size 0.</p>
	 * @return a new set of commit IDs with zero parents (not {@code null})
	 */
	public Set<CommitId> getRoots() {
		Set<CommitId> result = new HashSet<>();
		for (Map.Entry<CommitId,Set<CommitId>> entry : idToParents.entrySet()) {
			if (entry.getValue().isEmpty())
				result.add(entry.getKey());
		}
		return result;
	}
	
	
	/**
	 * Returns the set of commit IDs that are not the parent of any currently known commit.
	 * The returned information is based on the set of all commits that have been added to
	 * this graph's database, and does not query over some implicit "global" set of objects.
	 * <p>The result set can grow and shrink in size as more commits are added to the database.
	 * When the database contains no commits, the result set has size 0.
	 * Otherwise, the result set always has size at least 1.</p>
	 * @return a new set of commit IDs with currently zero children (not {@code null})
	 */
	public Set<CommitId> getLeaves() {
		Set<CommitId> result = new HashSet<>();
		for (Map.Entry<CommitId,Set<CommitId>> entry : idToChildren.entrySet()) {
			if (entry.getValue().isEmpty())
				result.add(entry.getKey());
		}
		return result;
	}
	
	
	/**
	 * Returns the set of commit IDs whose commit objects have parents that have not been added
	 * to the database. The returned information is based on the set of all commits that have been
	 * added to this graph's database, and does not query over some implicit "global" set of objects.
	 * <p>The result set can grow or shrink in size as more commits are added to the database.
	 * The minimum result set has size 0. Note that whenever the size of the unexplored set
	 * decreases by 1, the size of the root set increases by 1.</p> 
	 * @return a new set of commit IDs with currently unexplored parents (not {@code null})
	 */
	public Set<CommitId> getUnexplored() {
		Set<CommitId> result = new HashSet<>(idToChildren.keySet());
		result.removeAll(idToParents.keySet());
		return result;
	}
	
}
