package nayugit;


public final class BlobObject extends GitObject {
	
	private byte[] data;
	
	
	
	public BlobObject(ObjectId id, byte[] data) {
		super(id);
		this.data = data.clone();
	}
	
	
	
	public byte[] getData() {
		return data.clone();
	}
	
	
	public String toString() {
		return String.format("BlobObject(id=%s, length=%d)", id.hexString, data.length);
	}
	
}
