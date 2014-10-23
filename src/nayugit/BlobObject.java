package nayugit;


public final class BlobObject extends GitObject {
	
	public byte[] data;
	
	
	
	public BlobObject(byte[] data) {
		this.data = data.clone();
	}
	
	
	
	public byte[] toBytes() {
		return addHeader("blob", data);
	}
	
	
	public String toString() {
		return String.format("BlobObject(length=%d)", data.length);
	}
	
}
