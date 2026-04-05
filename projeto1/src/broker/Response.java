import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Response {
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private long timestamp;  //Timestamp obrigatório
    
    @JsonProperty("channel_name")
    private String channelName;
    
    @JsonProperty("channels")
    private List<String> channels;
    //parte 2: publicação
    @JsonProperty("publication_status")
    private String publicationStatus;  // Status da publicação


    public Response() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Response(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public long getTimestamp() { return timestamp; }
    
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public List<String> getChannels(){ return channels; }
    public void setChannels(List<String> channels) { this.channels = channels; }
    
    public String getPublicationStatus() { return publicationStatus; }
    public void setPublicationStatus(String publicationStatus) { this.publicationStatus = publicationStatus; }
}
