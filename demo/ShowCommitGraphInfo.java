/* 
 * Git library
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.zip.DataFormatException;
import io.nayuki.git.CommitGraph;
import io.nayuki.git.CommitId;
import io.nayuki.git.FileRepository;
import io.nayuki.git.Reference;
import io.nayuki.git.Repository;


public final class ShowCommitGraphInfo {
	
	public static void main(String[] args) throws IOException, DataFormatException {
		// Check command line arguments
		if (args.length < 1) {
			System.err.println("Usage: java ShowCommitGraphInfo GitDirectory [BranchNames...]");
			System.exit(1);
			return;
		}
		
		Set<String> refNames = new HashSet<>();
		if (args.length == 1)
			refNames.add("heads/master");
		else {
			for (int i = 1; i < args.length; i++)
				refNames.add("heads/" + args[i]);
		}
		
		// Scan the repository
		CommitGraph graph = new CommitGraph();
		try (Repository repo = new FileRepository(new File(args[0]))) {
			Set<CommitId> commitIds = new HashSet<>();
			for (String name : refNames) {
				Reference ref = repo.readReference(name);
				if (ref == null)
					throw new IllegalArgumentException("Reference not found: " + name);
				commitIds.add(ref.target);
			}
			graph.addHistory(repo, commitIds);
		}
		
		/* Collect statistics */
		
		int numFork = 0;
		for (CommitId id : graph.getChildrenKeys()) {
			if (graph.getChildren(id).size() > 1)
				numFork++;
		}
		
		int numRoot = 0;
		int numMerge = 0;
		for (CommitId id : graph.getParentsKeys()) {
			int n = graph.getParents(id).size();
			if (n == 0)
				numRoot++;
			else if (n > 1)
				numMerge++;
		}
		
		int maxChain = 0;
		Map<CommitId,Integer> chainLen = new HashMap<>();
		Queue<CommitId> queue = new ArrayDeque<>();
		Map<CommitId,Integer> pendingChildren = new HashMap<>();
		queue.addAll(graph.getLeaves());
		while (!queue.isEmpty()) {  // Topological sort
			CommitId id = queue.remove();
			if (chainLen.containsKey(id))
				throw new AssertionError();
			if (pendingChildren.containsKey(id))
				continue;
			
			int chain = 1;
			for (CommitId child : graph.getChildren(id))
				chain = Math.max(chainLen.get(child) + 1, chain);
			chainLen.put(id, chain);
			maxChain = Math.max(chain, maxChain);
			
			for (CommitId parent : graph.getParents(id)) {
				if (!pendingChildren.containsKey(parent))
					pendingChildren.put(parent, graph.getChildren(parent).size());
				pendingChildren.put(parent, pendingChildren.get(parent) - 1);
				if (pendingChildren.get(parent) == 0) {
					pendingChildren.remove(parent);
					queue.add(parent);
				}
			}
		}
		
		// Print results
		System.out.println("Number of root commits: " + numRoot);
		System.out.println("Number of fork commits: " + numFork);
		System.out.println("Number of merge commits: " + numMerge);
		System.out.println("Longest chain of commits: " + maxChain);
		System.out.println("Total number of commits: " + graph.getParentsKeys().size());
	}
	
}
