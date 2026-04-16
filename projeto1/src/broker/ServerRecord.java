import com.fasterxml.jackson.annotation.JsonProperty;

// Usado pelo Coordenador para manter lista de servidores disponíveis
public class ServerRecord {
    @JsonProperty("name")
    private String name;  // Nome do servidor 
    
    @JsonProperty("rank")
    private int rank;  // Rank único atribuído pelo coordenador 
    
    @JsonProperty("last_heartbeat")
    private long lastHeartbeat;  // Timestamp do último heartbeat 
    
    public ServerRecord() {}
    
    public ServerRecord(String name, int rank) {
        this.name = name;
        this.rank = rank;
        this.lastHeartbeat = System.currentTimeMillis();
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    
    public long getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    
    @Override
    public String toString() {
        return "Server{name='" + name + "', rank=" + rank + "}";
    }
}

