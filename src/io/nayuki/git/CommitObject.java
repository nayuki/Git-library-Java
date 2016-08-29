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


public final class CommitObject extends GitObject {
	
	public ObjectId tree;
	public List<ObjectId> parents;
	public String message;
	
	public String authorName;
	public String authorEmail;
	public int authorTime;      // Unix time in seconds
	public int authorTimezone;  // In minutes from UTC
	
	public String committerName;
	public String committerEmail;
	public int committerTime;      // Unix time in seconds
	public int committerTimezone;  // In minutes from UTC
	
	
	
	public CommitObject(byte[] data, WeakReference<Repository> repo) throws DataFormatException {
		parents = new ArrayList<>();
		
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
	
	
	
	public byte[] toBytes() {
		StringBuilder sb = new StringBuilder();
		sb.append("tree ").append(tree.hexString).append("\n");
		for (ObjectId parent : parents)
			sb.append("parent ").append(parent.hexString).append("\n");
		sb.append(String.format("author %s <%s> %d %s\n", authorName, authorEmail, authorTime, formatTimezone(authorTimezone)));
		sb.append(String.format("committer %s <%s> %d %s\n", committerName, committerEmail, committerTime, formatTimezone(committerTimezone)));
		sb.append("\n").append(message);
		return addHeader("commit", sb.toString().getBytes(StandardCharsets.UTF_8));
	}
	
	
	public String toString() {
		return String.format("CommitObject(tree=%s)", tree.hexString);
	}
	
	
	
	// For example: 105 -> "+0145"; -240 -> "-0400"
	private static String formatTimezone(int timezone) {
		String sign = timezone >= 0 ? "+" : "-";
		timezone = Math.abs(timezone);
		int hours = timezone / 60;
		int minutes = timezone % 60;
		return String.format("%s%02d%02d", sign, hours, minutes);
	}
	
	
	// For example: John Smith <jsmith@example.com> 1234567890 +0000
	private static final Pattern AUTHORSHIP_PATTERN = Pattern.compile("(.*?) <([^>]*)> (\\d+) ([+-])(\\d{2})(\\d{2})");
	
}
