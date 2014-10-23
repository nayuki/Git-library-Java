package nayugit;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public final class TreeObject extends GitObject {
	
	public List<TreeEntry> entries;
	
	
	
	public TreeObject(byte[] data) throws UnsupportedEncodingException {
		entries = new ArrayList<TreeEntry>();
		int index = 0;
		while (index < data.length) {
			int start = index;
			while (data[index] != ' ')
				index++;
			int mode = Integer.parseInt(new String(data, start, index - start, "US-ASCII"), 8);  // Parse number as octal
			
			index++;
			start = index;
			while (data[index] != 0)
				index++;
			String name = new String(data, start, index - start, "UTF-8");
			
			index++;
			ObjectId fileId = new ObjectId(Arrays.copyOfRange(data, index, index + 20));
			index += 20;
			entries.add(new TreeEntry(TreeEntry.Type.fromMode(mode), name, fileId));
		}
	}
	
	
	
	public TreeEntry getEntry(String name) {
		for (TreeEntry entry : entries) {
			if (entry.name.equals(name))
				return entry;
		}
		return null;
	}
	
	
	public String toString() {
		return String.format("TreeObject(entries=%d)", entries.size());
	}
	
}
