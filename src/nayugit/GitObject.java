package nayugit;


public abstract class GitObject {
	
	public final ObjectId id;
	
	
	
	public GitObject(ObjectId id) {
		if (id == null)
			throw new NullPointerException();
		this.id = id;
	}
	
}
