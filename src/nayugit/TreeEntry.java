package nayugit;


public class TreeEntry {
	
	public final int mode;
	public final String name;
	public final ObjectId id;
	
	
	
	public TreeEntry(int mode, String name, ObjectId id) {
		if (name == null || id == null)
			throw new NullPointerException();
		this.mode = mode;
		this.name = name;
		this.id = id;
	}
	
	
	
	public String toString() {
		return String.format("TreeEntry(mode=%s, name=\"%s\", id=%s)", Integer.toString(mode, 8), name, id.hexString);
	}
	
}
