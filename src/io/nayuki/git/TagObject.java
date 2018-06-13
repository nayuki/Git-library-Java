/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/git-library-java
 */

package io.nayuki.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;


/**
 * Represents a Git tag. Contains a target object ID and type (usually a commit),
 * tagger information, and a message. Mutable structure.
 * @see TagId
 * @see CommitId
 */
public final class TagObject extends GitObject {
	
	/*---- Fields ----*/
	
	// Note: All fields must be non-null when getBytes() or getId() is called!
	
	
	/* Miscellaneous fields */
	
	/**
	 * The hash of the target object being tagged. This is usually, but not always a {@link CommitId}.
	 */
	public ObjectId target;
	
	/**
	 * The type of the target object. Should be "commit", "tree", "blob", or "tag".
	 */
	public String targetType;
	
	/**
	 * The name of this tag. This plays a similar role to a branch name.
	 */
	public String tagName;
	
	/**
	 * The message for this tag, which can be multi-line.
	 */
	public String message;
	
	
	/* Tagger fields */
	
	/**
	 * The name of the creator of this tag, for example "Alice Margatroid".
	 */
	public String taggerName;
	
	/**
	 * The email address of the creator of this tag, for example "bob@smith.com".
	 */
	public String taggerEmail;
	
	/**
	 * The time this tag was created, in Unix epoch seconds.
	 * Note that this value is always given in UTC and does not depend on the time zone field.
	 */
	public int taggerTime;
	
	/**
	 * The time zone of the tagger when this tag was created,
	 * in minutes ahead of UTC. For example 180 represents UTC+3 hours.
	 */
	public int taggerTimezone;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a blank tag object with all fields set to {@code null} or zero.
	 */
	public TagObject() {}
	
	
	/**
	 * Constructs a tag object with the data initially set to the parsed interpretation of the specified bytes.
	 * @param data the serialized tag data to read (not {@code null})
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IOException if malformed data was encountered during reading
	 */
	public TagObject(byte[] data) throws IOException {
		this();
		Objects.requireNonNull(data);
		
		try {
			CommitObject.LineParser parser = new CommitObject.LineParser(data);
			
			// Parse object line
			String[] parts = parser.nextLineAsPair();
			if (!parts[0].equals("object"))
				throw new GitFormatException("Object field expected");
			target = new RawId(parts[1]);
			
			// Parse type line
			parts = parser.nextLineAsPair();
			if (!parts[0].equals("type"))
				throw new GitFormatException("Type field expected");
			targetType = parts[1];
			
			// Parse tag line
			parts = parser.nextLineAsPair();
			if (!parts[0].equals("tag"))
				throw new GitFormatException("Tag field expected");
			tagName = parts[1];
			
			// Parse tagger line
			parts = parser.nextLineAsPair();
			if (!parts[0].equals("tagger"))
				throw new GitFormatException("Author field expected");
			Matcher m = CommitObject.AUTHORSHIP_PATTERN.matcher(parts[1]);
			if (!m.matches())
				throw new GitFormatException("Invalid author data");
			taggerName = m.group(1);
			taggerEmail = m.group(2);
			taggerTime = Integer.parseInt(m.group(3));
			taggerTimezone = Integer.parseInt(m.group(4) + "1") * (Integer.parseInt(m.group(5)) * 60 + Integer.parseInt(m.group(6)));
			
			// Grab message
			if (!parser.nextLine().equals(""))
				throw new GitFormatException("Blank line expected");
			message = parser.getRemainder();
			
		} catch (IllegalStateException e) {
			throw new GitFormatException("Unexpected end of tag data");
		}
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns the raw byte serialization of the current state of this tag object, including a lightweight header.
	 * @return the raw byte serialization of this object (not {@code null})
	 * @throws IllegalStateException if any field is {@code null}
	 */
	public byte[] toBytes() {
		checkState();
		StringBuilder sb = new StringBuilder();
		sb.append("object ").append(target.hexString).append("\n");
		sb.append("type ").append(targetType).append("\n");
		sb.append("tag ").append(tagName).append("\n");
		sb.append(String.format("tagger %s <%s> %d %s\n",
			taggerName, taggerEmail, taggerTime, CommitObject.formatTimezone(taggerTimezone)));
		sb.append("\n").append(message);
		return addHeader("tag", sb.toString().getBytes(StandardCharsets.UTF_8));
	}
	
	
	/**
	 * Returns the hash ID of the current state of this tag object.
	 * @return the hash ID of this tag object (not {@code null})
	 * @throws IllegalStateException if this object has invalid field values
	 * that prevent it from being serialized (see {@link #toBytes()})
	 */
	public TagId getId() {
		return new TagId(getSha1Hash(toBytes()));
	}
	
	
	/**
	 * Returns a string representation of this tag object. The format is subject to change.
	 * @return a string representation of this tag object
	 */
	public String toString() {
		return String.format("TagObject(target=%s, type=%s)", target.hexString, targetType);
	}
	
	
	private void checkState() {
		if (target == null)
			throw new IllegalStateException("Target is null");
		if (targetType == null)
			throw new IllegalStateException("Target type is null");
		if (tagName == null)
			throw new IllegalStateException("Tag name is null");
		if (taggerName == null)
			throw new IllegalStateException("Tagger name is null");
		if (taggerEmail == null)
			throw new IllegalStateException("Tagger email is null");
	}
	
}
