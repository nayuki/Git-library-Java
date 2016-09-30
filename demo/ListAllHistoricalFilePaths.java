/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.DataFormatException;
import io.nayuki.git.CommitId;
import io.nayuki.git.CommitObject;
import io.nayuki.git.FileRepository;
import io.nayuki.git.Reference;
import io.nayuki.git.Repository;
import io.nayuki.git.TreeId;
import io.nayuki.git.TreeObject;


public final class ListAllHistoricalFilePaths {
	
	public static void main(String[] args) throws IOException, DataFormatException {
		// Check command line arguments
		if (args.length < 1 || args.length > 2) {
			System.err.println("Usage: java ListAllHistoricalFilePaths GitDirectory [BranchName]");
			System.exit(1);
			return;
		}
		
		Set<String> filePaths = new TreeSet<>();
		
		// Parse command line arguments
		try (Repository repo = new FileRepository(new File(args[0]))) {
			String branch = "master";
			if (args.length == 2)
				branch = args[1];
			Reference ref = repo.readReference("heads/" + branch);
			
			// Scan the graph of commits and collect file paths
			Queue<CommitId> queue = new ArrayDeque<>();
			queue.add(ref.target);
			while (!queue.isEmpty()) {  // Breadth-first search
				CommitObject commit = queue.remove().read(repo);
				scanFilePaths(repo, commit.tree.read(repo), "/", filePaths);
				queue.addAll(commit.parents);
			}
		}
		
		// Print results
		for (String s : filePaths)
			System.out.println(s);
	}
	
	
	private static void scanFilePaths(Repository repo, TreeObject tree, String prefix, Set<String> result)
			throws IOException, DataFormatException {
		
		for (TreeObject.Entry entry : tree.entries) {
			if (entry.type == TreeObject.Entry.Type.DIRECTORY)
				scanFilePaths(repo, ((TreeId)entry.id).read(repo), prefix + entry.name + "/", result);
			else if (entry.type == TreeObject.Entry.Type.NORMAL_FILE
					|| entry.type == TreeObject.Entry.Type.EXECUTABLE_FILE
					|| entry.type == TreeObject.Entry.Type.SYMBOLIC_LINK)
				result.add(prefix + entry.name);
		}
	}
	
}
