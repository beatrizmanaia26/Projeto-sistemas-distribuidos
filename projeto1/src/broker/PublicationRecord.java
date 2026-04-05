import java.io.Serializable;

public class PublicationRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String username;
    private String channelName;
    private String content;
    private long timestamp;
    private long publicationTimestamp;
    
    public PublicationRecord() {}
    
    public PublicationRecord(String username, String channelName, String content, long timestamp) {
        this.username = username;
        this.channelName = channelName;
        this.content = content;
        this.timestamp = timestamp;
        this.publicationTimestamp = System.currentTimeMillis();
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public long getPublicationTimestamp() { return publicationTimestamp; }
    public void setPublicationTimestamp(long publicationTimestamp) { this.publicationTimestamp = publicationTimestamp; }
    
    @Override
    public String toString() {
        return String.format("[%s] %s em %s: %s (enviado: %d, publicado: %d)",
            channelName, username, channelName, content, timestamp, publicationTimestamp);
    }
}