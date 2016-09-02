/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

package io.nayuki.git;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A name that refers to a commit ID. Partially mutable structure.
 */
public final class Reference {
	
	/*---- Fields ----*/
	
	/** The name of the reference, such as "master". Immutable and not {@code null}. */
	public final String name;
	
	/** The commit hash that this reference points to. Can be {@code null}. */
	public CommitId target;
	
	
	
	/*---- Constructors ----*/
	
	/**
	 * Constructs a reference with the specified name and a {@code null} hash.
	 * @param name the name of the reference
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalArgumentException if the reference name is invalid
	 */
	public Reference(String name) {
		this(name, null);
	}
	
	
	/**
	 * Constructs a reference with the specified name and specified hash.
	 * <p>Examples of valid reference names:</p>
	 * <ul>
	 *   <li>heads/master</li>
	 *   <li>heads/development</li>
	 *   <li>remotes/origin/mybranch</li>
	 *   <li>remotes/server/master</li>
	 *   <li>tags/version1</li>
	 *   <li>tags/HelloWorld</li>
	 * </ul>
	 * <p>Examples of invalid reference names:</p>
	 * <ul>
	 *   <li>heads//</li>
	 *   <li>heads/..</li>
	 *   <li>heads/HEAD</li>
	 *   <li>heads/alpha/beta</li>
	 *   <li>remotes/foobox/HEAD</li>
	 *   <li>remotes/what</li>
	 *   <li>tags/subdir/onetwo</li>
	 * </ul>
	 * @param name the name of the reference
	 * @param target the hash of the target
	 * @throws NullPointerException if the name is {@code null}
	 * @throws IllegalArgumentException if the reference name is invalid
	 */
	public Reference(String name, CommitId target) {
		checkName(name);
		this.name = name;
		this.target = target;
	}
	
	
	
	/*---- Methods ----*/
	
	/**
	 * Returns a string representation of this reference. The format is subject to change.
	 * @return a string representation of this reference
	 */
	public String toString() {
		return String.format("Reference(name=%s, id=%s)",
			name, target != null ? target.hexString : "null");
	}
	
	
	
	/*---- Static members ----*/
	
	// Returns silently or throws an exception.
	static void checkName(String name) {
		if (name == null)
			throw new NullPointerException();
		Matcher m = NAME_PATTERN.matcher(name);
		if (!m.matches())
			throw new IllegalArgumentException("Invalid reference name: " + name);
		
		String[] parts = name.split("/", -1);
		if (parts[0].equals("remotes") && (parts[1].equals(".") || parts[1].equals(".."))
				|| parts[parts.length - 1].equals("HEAD"))
			throw new IllegalArgumentException("Invalid reference name");
	}
	
	
	private static final Pattern NAME_PATTERN = Pattern.compile("(heads|remotes/[^/]+|tags)/[A-Za-z0-9_-]+");
	
}
