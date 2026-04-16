import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Response {
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("timestamp")
    private long timestamp;  //Timestamp obrigatório
    
    // PARTE 3: Relógio lógico - valor do contador enviado em cada resposta
    @JsonProperty("logical_clock")
    private long logicalClock;
    
    @JsonProperty("channel_name")
    private String channelName;
    
    @JsonProperty("channels")
    private List<String> channels;
    //parte 2: publicação
    @JsonProperty("publication_status")
    private String publicationStatus;  // Status da publicação
    
    // PARTE 3: Rank do servidor - identificador único atribuído pelo coordenador
    @JsonProperty("rank")
    private int rank;
    
    // PARTE 3: Lista de servidores disponíveis - retornada pelo coordenador (enunciado linha 22)
    @JsonProperty("server_list")
    private List<ServerRecord> serverList;
    
    // PARTE 3: Hora atual do coordenador - para sincronização do relógio físico
    @JsonProperty("current_time")
    private long currentTime;


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
    
    public long getLogicalClock() { return logicalClock; }
    public void setLogicalClock(long logicalClock) { this.logicalClock = logicalClock; }
    
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    
    public List<ServerRecord> getServerList() { return serverList; }
    public void setServerList(List<ServerRecord> serverList) { this.serverList = serverList; }
    
    public long getCurrentTime() { return currentTime; }
    public void setCurrentTime(long currentTime) { this.currentTime = currentTime; }
}
