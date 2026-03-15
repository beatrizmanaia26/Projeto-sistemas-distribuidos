import com.fasterxml.jackson.annotation.JsonProperty;

public class Response {
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private long timestamp;  //Timestamp obrigatório
    
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
}
