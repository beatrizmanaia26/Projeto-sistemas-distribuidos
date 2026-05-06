import java.io.Serializable;
import java.util.UUID;

public class PublicationRecord implements Serializable {
    private static final long serialVersionUID = 2L;  // Incrementado para nova versão
    
    private String id;//único para deduplicação

    private String username;
    private String channelName;
    private String content;
    private long timestamp;
    private long publicationTimestamp;
    
    private long logicalClock; // Relógio lógico para ordenação e resolução de conflitos
    
    private int serverRank;// Rank do servidor que criou a publicação (para desempate)
    
    public PublicationRecord() {
        this.id = UUID.randomUUID().toString();
    }
    
    public PublicationRecord(String username, String channelName, String content, long timestamp) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.channelName = channelName;
        this.content = content;
        this.timestamp = timestamp;
        this.publicationTimestamp = System.currentTimeMillis();
    }
    
    public PublicationRecord(String id, String username, String channelName, String content,
                           long timestamp, long publicationTimestamp, long logicalClock, int serverRank) {
        this.id = id;
        this.username = username;
        this.channelName = channelName;
        this.content = content;
        this.timestamp = timestamp;
        this.publicationTimestamp = publicationTimestamp;
        this.logicalClock = logicalClock;
        this.serverRank = serverRank;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
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
    
    public long getLogicalClock() { return logicalClock; }
    public void setLogicalClock(long logicalClock) { this.logicalClock = logicalClock; }
    
    public int getServerRank() { return serverRank; }
    public void setServerRank(int serverRank) { this.serverRank = serverRank; }
    
    @Override
    public String toString() {
        return String.format("[%s] %s em %s: %s (enviado: %d, publicado: %d, clock: %d, id: %s)",
            channelName, username, channelName, content, timestamp, publicationTimestamp, logicalClock, id.substring(0, 8));
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublicationRecord that = (PublicationRecord) o;
        return id != null && id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}