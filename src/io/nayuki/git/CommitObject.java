/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Represents a Git commit. Contains author information, committer information,
 * a list of parent commit IDs, a tree ID, and a message. Mutable structure.
 * @see CommitId
 * @see TreeObject
 * @see BlobObject
 */
public final class CommitObject extends GitObject {
	
	/*---- Fields ----*/
	
	// Note: All fields must be non-null when getBytes() or getId() is called!
	
	
	/* Miscellaneous fields */
	
	/**
	 * The hash of the root directory of files in this commit.
	 */
	public TreeId tree;
	
	/**
	 * The list of zero or more parents of this commit, which must not contain duplicates.
	 */
	public List<CommitId> parents;
	
	/**
	 * The message for this commit, which can be multi-line.
	 */
	public String message;
	
	
	/* Author fields */
	
	/**
	 * The name of the author of this commit, for example "Alice Margatroid".
	 */
	public String authorName;
	
	/**
	 * The email address of the author of this commit, for example "bob@smith.com".
	 */
	public String authorEmail;
	
	/**
	 * The time this commit was authored, in Unix epoch seconds.
	 * Note that this value is always given in UTC and does not depend on the time zone field.
	 */
	public long authorTime;
	
	/**
	 * The time zone of the author when this commit was created, in minutes ahead of UTC. For example 180 represents UTC+3 hours.
	 */
	public int authorTimezone;
	
	
	/* Committer fields */
	
	/**
	 * The name of the committer of this commit. Normally this is the same as the author, but if the author's patch (changeset)
	 * was applied by a different person, then the committer is the one who applied the patch and created the Git commit.
	 */
	public String committerName;
	
	/**
	 * The email address of the committer of this commit. Normally this is the same as the author's email,
	 * but otherwise it follows the same rules as the committer name.
	 */
	public String committerEmail;
	
	/**
	 * The time this Git commit object was created, in Unix epoch seconds. Normally this is the same as the author's time,
	 * but if the author's patch was transplanted to a different location and/or tweaked after the initial creation,
	 * then this timestamp reflects the time that this particular application of the patch was created.
	 * Note that this value is always given in UTC and does not depend on the time zone field.
	 */
	public long committerTime;
	
	/**
	 * The time zone of the committer when this commit was created, in minutes ahead of UTC. For example -105 represents UTC-1:45.
	 */
	public int committerTimezone;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a blank commit object with an empty (but non-{@code null}) list of entries, and all other fields set to {@code null} or zero.
	 */
	public CommitObject() {
		parents = new ArrayList<>();
	}
	
	
	/**
	 * Constructs a commit object with the data initially set to the parsed interpretation of the specified bytes.
	 * @param data the serialized commit data to read (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IOException if malformed data was encountered during reading
	 */
	public CommitObject(byte[] data) throws IOException {
		this();
		Objects.requireNonNull(data);
		
		try {
			LineParser parser = new LineParser(data);
			
			// Parse tree line
			String[] parts = parser.nextLineAsPair();
			if (!parts[0].equals("tree"))
				throw new GitFormatException("Tree field expected");
			tree = new TreeId(parts[1]);
			
			// Parse parent lines (zero or more)
			while (true) {
				parts = parser.nextLineAsPair();
				if (!parts[0].equals("parent"))
					break;
				parents.add(new CommitId(parts[1]));
			}
			
			// Parse author line
			if (!parts[0].equals("author"))
				throw new GitFormatException("Author field expected");
			Matcher m = AUTHORSHIP_PATTERN.matcher(parts[1]);
			if (!m.matches())
				throw new GitFormatException("Invalid author data");
			authorName = m.group(1);
			authorEmail = m.group(2);
			authorTime = Long.parseLong(m.group(3));
			authorTimezone = Integer.parseInt(m.group(4) + "1") * (Integer.parseInt(m.group(5)) * 60 + Integer.parseInt(m.group(6)));
			
			// Parse committer line
			parts = parser.nextLineAsPair();
			if (!parts[0].equals("committer"))
				throw new GitFormatException("Committer field expected");
			m = AUTHORSHIP_PATTERN.matcher(parts[1]);
			if (!m.matches())
				throw new GitFormatException("Invalid committer data");
			committerName = m.group(1);
			committerEmail = m.group(2);
			committerTime = Long.parseLong(m.group(3));
			committerTimezone = Integer.parseInt(m.group(4) + "1") * (Integer.parseInt(m.group(5)) * 60 + Integer.parseInt(m.group(6)));
			
			// Grab message
			if (!parser.nextLine().equals(""))
				throw new GitFormatException("Blank line expected");
			message = parser.getRemainder();
			
		} catch (IllegalStateException e) {
			throw new EOFException("Unexpected end of commit data");
		}
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the raw byte serialization of the current state of this commit object, including a lightweight header.
	 * @return the raw byte serialization of this object (not {@code null})
	 * @throws IllegalStateException if any field is {@code null}, the list of parents has {@code null} elements,
	 * the list of parents contains duplicates, or some field contains invalid characters
	 */
	public byte[] toBytes() {
		checkState();
		StringBuilder sb = new StringBuilder();
		sb.append("tree ").append(tree.hexString).append("\n");
		for (ObjectId parent : parents)
			sb.append("parent ").append(parent.hexString).append("\n");
		sb.append(String.format("author %s <%s> %d %s\n", authorName, authorEmail, authorTime, formatTimezone(authorTimezone)));
		sb.append(String.format("committer %s <%s> %d %s\n", committerName, committerEmail, committerTime, formatTimezone(committerTimezone)));
		sb.append("\n").append(message);
		return addHeader("commit", sb.toString().getBytes(StandardCharsets.UTF_8));
	}
	
	
	/**
	 * Returns the hash ID of the current state of this commit object.
	 * @return the hash ID of this commit object (not {@code null})
	 * @throws IllegalStateException if this object has invalid field values
	 * that prevent it from being serialized (see {@link #toBytes()})
	 */
	public CommitId getId() {
		return new CommitId(getSha1Hash(toBytes()));
	}
	
	
	/**
	 * Returns a string representation of this commit object. The format is subject to change.
	 * @return a string representation of this commit object
	 */
	public String toString() {
		return String.format("CommitObject(tree=%s)", tree.hexString);
	}
	
	
	private void checkState() {
		if (tree == null)
			throw new IllegalStateException("Tree hash is null");
		if (parents == null)
			throw new IllegalStateException("List of parents is null");
		for (CommitId id : parents) {
			if (id == null)
				throw new IllegalStateException("List element is null");
		}
		if (new HashSet<>(parents).size() != parents.size())
			throw new IllegalStateException("List of parents contains duplicates");
		if (message == null)
			throw new IllegalStateException("Commit message is null");
		
		if (authorName == null)
			throw new IllegalStateException("Author name is null");
		if (authorName.indexOf('\n') != -1)
			throw new IllegalStateException("Author name contains illegal characters");
		if (authorEmail == null)
			throw new IllegalStateException("Author email is null");
		if (authorEmail.indexOf('\n') != -1)
			throw new IllegalStateException("Author email contains illegal characters");
		if (committerName == null)
			throw new IllegalStateException("Committer name is null");
		if (committerName.indexOf('\n') != -1)
			throw new IllegalStateException("Committer name contains illegal characters");
		if (committerEmail == null)
			throw new IllegalStateException("Committer email is null");
		if (committerEmail.indexOf('\n') != -1)
			throw new IllegalStateException("Committer email contains illegal characters");
	}
	
	
	
	/*---- Static helper functions ----*/
	
	// For example: 0 -> "+0000"; 105 -> "+0145"; -240 -> "-0400".
	static String formatTimezone(int minutes) {
		String sign = minutes >= 0 ? "+" : "-";
		minutes = Math.abs(minutes);
		return String.format("%s%02d%02d", sign, minutes / 60, minutes % 60);
	}
	
	
	// For example: John Smith <jsmith@example.com> 1234567890 +0000
	static final Pattern AUTHORSHIP_PATTERN = Pattern.compile("(.*?) <([^>]*)> (\\d+) ([+-])(\\d{2})(\\d{2})");
	
	
	
	/*---- Helper class for parser constructor ----*/
	
	static final class LineParser {
		
		private String text;
		private int startIndex;
		
		
		public LineParser(byte[] data) {
			text = new String(data, StandardCharsets.UTF_8);
			startIndex = 0;
		}
		
		
		public String nextLine() {
			int end = text.indexOf('\n', startIndex);
			if (end == -1)
				throw new IllegalStateException("Next line does not exist");
			String result = text.substring(startIndex, end);
			startIndex = end + 1;
			return result;
		}
		
		
		public String[] nextLineAsPair() {
			String temp = nextLine();
			String[] result = temp.split(" ", 2);
			if (result.length != 2)
				throw new IllegalStateException("Next line does not have 2 parts");
			return result;
		}
		
		
		public String getRemainder() {
			return text.substring(startIndex);
		}
		
	}
	
}
