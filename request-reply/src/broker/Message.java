import com.fasterxml.jackson.annotation.JsonProperty;

public class Message {
    @JsonProperty("type")
    private String type;  // "login"
    
    @JsonProperty("timestamp")
    private long timestamp;  // Timestamp obrigatório
    
    @JsonProperty("username")
    private String username;
    
    public Message() {
        this.timestamp = System.currentTimeMillis();  
    }
    
    public Message(String type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
