/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;


/**
 * Represents a Git commit. Contains author information, committer information,
 * a list of parent commit IDs, a tree ID, and a message. Mutable structure.
 */
public final class CommitObject extends GitObject {
	
	/*---- Fields ----*/
	
	public TreeId tree;
	public List<CommitId> parents;
	public String message;
	
	public String authorName;
	public String authorEmail;
	public int authorTime;      // Unix time in seconds
	public int authorTimezone;  // In minutes from UTC
	
	public String committerName;
	public String committerEmail;
	public int committerTime;      // Unix time in seconds
	public int committerTimezone;  // In minutes from UTC
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a blank commit object with an empty (but non-{@code null}) list of entries, and all other fields set to {@code null} or zero.
	 */
	public CommitObject() {
		parents = new ArrayList<>();
	}
	
	
	/**
	 * Constructs a commit object with the data initially set to the parsed interpretation of the specified bytes.
	 * Every object ID that the commit refers to will have its repository set to the specified repo argument.
	 * @param data the serialized commit data to read
	 * @param srcRepo the repository to set for object IDs
	 * @throws NullPointerException if the array is {@code null}
	 */
	public CommitObject(byte[] data, WeakReference<Repository> repo) throws DataFormatException {
		this();
		if (data == null)
			throw new NullPointerException();
		
		int index = 0;
		int start;
		String line;
		String[] parts;
		
		// Parse tree line
		for (start = index; data[index] != '\n'; index++);
		line = new String(data, start, index - start, StandardCharsets.UTF_8);
		parts = line.split(" ", 2);
		if (!parts[0].equals("tree"))
			throw new DataFormatException("Tree field expected");
		tree = new TreeId(parts[1], repo);
		index++;
		
		// Parse parent lines (0 or more)
		while (true) {
			for (start = index; data[index] != '\n'; index++);
			line = new String(data, start, index - start, StandardCharsets.UTF_8);
			parts = line.split(" ", 2);
			if (!parts[0].equals("parent"))
				break;
			parents.add(new CommitId(parts[1], repo));
			index++;
		}
		
		// Parse author line
		if (!parts[0].equals("author"))
			throw new DataFormatException("Author field expected");
		Matcher m = AUTHORSHIP_PATTERN.matcher(parts[1]);
		if (!m.matches())
			throw new DataFormatException("Invalid author data");
		authorName = m.group(1);
		authorEmail = m.group(2);
		authorTime = Integer.parseInt(m.group(3));
		authorTimezone = Integer.parseInt(m.group(4) + "1") * (Integer.parseInt(m.group(5)) * 60 + Integer.parseInt(m.group(6)));
		index++;
		
		// Parse committer line
		for (start = index; data[index] != '\n'; index++);
		line = new String(data, start, index - start, StandardCharsets.UTF_8);
		parts = line.split(" ", 2);
		if (!parts[0].equals("committer"))
			throw new DataFormatException("Committer field expected");
		m = AUTHORSHIP_PATTERN.matcher(parts[1]);
		if (!m.matches())
			throw new DataFormatException("Invalid committer data");
		committerName = m.group(1);
		committerEmail = m.group(2);
		committerTime = Integer.parseInt(m.group(3));
		committerTimezone = Integer.parseInt(m.group(4) + "1") * (Integer.parseInt(m.group(5)) * 60 + Integer.parseInt(m.group(6)));
		index++;
		
		// Grab message
		if (data[index] != '\n')
			throw new DataFormatException("Blank line expected");
		index++;
		message = new String(data, index, data.length - index, StandardCharsets.UTF_8);
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the raw byte serialization of the current state of this commit object, including a lightweight header.
	 * @return the raw byte serialization of this object
	 * @throws NullPointerException if any fields are {@code null}
	 */
	public byte[] toBytes() {
		if (tree == null || parents == null || message == null ||
				authorName == null || authorEmail == null || committerName == null || committerEmail == null)
			throw new NullPointerException();
		
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
	 * @return the hash ID of this commit object
	 */
	public CommitId getId() {
		return new CommitId(Sha1.getHash(toBytes()), null);
	}
	
	
	/**
	 * Returns a string representation of this commit object. The format is subject to change.
	 * @return a string representation of this commit object
	 */
	public String toString() {
		return String.format("CommitObject(tree=%s)", tree.hexString);
	}
	
	
	
	/*---- Static helper functions ----*/
	
	// For example: 0 -> "+0000"; 105 -> "+0145"; -240 -> "-0400".
	private static String formatTimezone(int minutes) {
		String sign = minutes >= 0 ? "+" : "-";
		minutes = Math.abs(minutes);
		return String.format("%s%02d%02d", sign, minutes / 60, minutes % 60);
	}
	
	
	// For example: John Smith <jsmith@example.com> 1234567890 +0000
	private static final Pattern AUTHORSHIP_PATTERN = Pattern.compile("(.*?) <([^>]*)> (\\d+) ([+-])(\\d{2})(\\d{2})");
	
}
