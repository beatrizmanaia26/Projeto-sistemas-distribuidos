import com.fasterxml.jackson.annotation.JsonProperty;

public class Message {
    @JsonProperty("type")
    private String type;  // "login", "create_channel", "publish", etc
    
    @JsonProperty("timestamp")
    private long timestamp;  // Timestamp obrigatório
    
    @JsonProperty("logical_clock")
    private long logicalClock;
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("channel_name")
    private String channelName;
    //parte 2: publicação
    @JsonProperty("content")
    private String content;  // Conteúdo da mensagem para publicação
    
    @JsonProperty("received_timestamp")
    private long receivedTimestamp;  // Timestamp de recebimento (para subscriber)
    
    // PARTE 4: Campos para eleição e sincronização
    @JsonProperty("election_id")
    private int electionId;  // ID da eleição (rank do servidor que iniciou)
    
    @JsonProperty("coordinator_name")
    private String coordinatorName;  // Nome do coordenador eleito
    
    @JsonProperty("clock_offset")
    private long clockOffset;  // Diferença de relógio para sincronização Berkeley
    
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
    
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public long getReceivedTimestamp() { return receivedTimestamp; }
    public void setReceivedTimestamp(long receivedTimestamp) { this.receivedTimestamp = receivedTimestamp; }
    
    public long getLogicalClock() { return logicalClock; }
    public void setLogicalClock(long logicalClock) { this.logicalClock = logicalClock; }
    
    // PARTE 4: Getters e Setters para eleição e sincronização
    public int getElectionId() { return electionId; }
    public void setElectionId(int electionId) { this.electionId = electionId; }
    
    public String getCoordinatorName() { return coordinatorName; }
    public void setCoordinatorName(String coordinatorName) { this.coordinatorName = coordinatorName; }
    
    public long getClockOffset() { return clockOffset; }
    public void setClockOffset(long clockOffset) { this.clockOffset = clockOffset; }
}