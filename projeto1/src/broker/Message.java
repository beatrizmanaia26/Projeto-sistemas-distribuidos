import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Message {
    @JsonProperty("type")
    private String type;  // "login", "create_channel", "publish", "sync_data", "sync_ack", etc
    
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
    
    // PARTE 5: Campos para replicação de dados (Consistência Eventual)
    @JsonProperty("server_name")
    private String serverName;  
    
    @JsonProperty("server_rank")
    private int serverRank;  // Rank do servidor para resolução de conflitos
    
    @JsonProperty("publications")
    private List<PublicationRecord> publications;  // publicações para sincronizar
    
    @JsonProperty("channels")
    private Set<String> channels;  //canais para sincronizar
    
    @JsonProperty("users")
    private Map<String, Long> users; 
    
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
    
    // para replicação
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    
    public int getServerRank() { return serverRank; }
    public void setServerRank(int serverRank) { this.serverRank = serverRank; }
    
    public List<PublicationRecord> getPublications() { return publications; }
    public void setPublications(List<PublicationRecord> publications) { this.publications = publications; }
    
    public Set<String> getChannels() { return channels; }
    public void setChannels(Set<String> channels) { this.channels = channels; }
    
    public Map<String, Long> getUsers() { return users; }
    public void setUsers(Map<String, Long> users) { this.users = users; }
}