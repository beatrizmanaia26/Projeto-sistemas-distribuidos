import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.util.*;
import java.util.concurrent.*;

// gerencia servidores (rank, lista, heartbeat, sincronização)
class Coordinator {
    private static long logicalClock = 0;
    
    // PARTE 3: Mapa de servidores: nome -> ServerRecord
    private static Map<String, ServerRecord> servers = new ConcurrentHashMap<>();
    
    // PARTE 3: Contador para atribuir ranks únicos
    private static int nextRank = 1;
    
    // PARTE 3: Timeout para considerar servidor inativo (30 segundos)
    private static final long HEARTBEAT_TIMEOUT = 30000;
    
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            // Socket REP para receber requisições dos servidores
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.bind("tcp://*:5559");
            
            System.out.println("COORDENADOR INICIADO");
            System.out.println("Porta: 5559");
            System.out.println("Aguardando servidores...\n");
            
            // Thread para limpar servidores inativos
            startCleanupThread();
            
            while (true) {
                try {
                    // Receber mensagem do servidor
                    byte[] msgBytes = socket.recv(0);
                    Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                    
                    // PARTE 3: AO RECEBER - atualizar relógio lógico
                    long receivedClock = msg.getLogicalClock();
                    long oldClock = logicalClock;
                    
                    if (receivedClock > logicalClock) {
                        logicalClock = receivedClock + 1;
                        System.out.println("[COORDENADOR][RELÓGIO] ATUALIZADO! Recebido=" + receivedClock + 
                                         " > local=" + oldClock + ". Novo=" + logicalClock);
                    } else {
                        logicalClock++;
                        System.out.println("[COORDENADOR][RELÓGIO] MANTIDO! Recebido=" + receivedClock + 
                                         " <= local=" + oldClock + ". Novo=" + logicalClock);
                    }
                    
                    System.out.println("\n[COORDENADOR] Requisição: " + msg.getType() + " de " + msg.getUsername());
                    
                    Response response;
                    
                    // Processar baseado no tipo
                    if ("get_rank".equals(msg.getType())) {
                        // PARTE 3: Atribuir rank (enunciado linha 14)
                        response = processGetRank(msg);
                    } else if ("get_server_list".equals(msg.getType())) {
                        // PARTE 3: Retornar lista de servidores (enunciado linha 22)
                        response = processGetServerList(msg);
                    } else if ("heartbeat".equals(msg.getType())) {
                        // PARTE 3: Processar heartbeat (enunciado linha 27 e 33)
                        response = processHeartbeat(msg);
                    } else {
                        response = new Response(false, "Tipo desconhecido");
                    }
                    
                    // PARTE 3: ANTES DE ENVIAR - incrementar relógio
                    logicalClock++;
                    response.setLogicalClock(logicalClock);
                    System.out.println("[COORDENADOR][RELÓGIO] Incrementado antes de enviar: " + logicalClock);
                    
                    // Enviar resposta
                    byte[] responseBytes = MessagePackUtil.serialize(response);
                    socket.send(responseBytes, 0);
                    
                } catch (Exception e) {
                    System.err.println("[COORDENADOR] Erro: " + e.getMessage());
                    Response errorResponse = new Response(false, "Erro no coordenador");
                    logicalClock++;
                    errorResponse.setLogicalClock(logicalClock);
                    byte[] errorBytes = MessagePackUtil.serialize(errorResponse);
                    socket.send(errorBytes, 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // PARTE 3: Processar requisição de rank (enunciado linha 14)
    private static Response processGetRank(Message msg) {
        String serverName = msg.getUsername();
        
        if (serverName == null || serverName.trim().isEmpty()) {
            return new Response(false, "Nome do servidor inválido");
        }
        
        ServerRecord server;
        
        // PARTE 3: Verificar se servidor já tem rank (enunciado linha 19 - sem repetições)
        if (servers.containsKey(serverName)) {
            server = servers.get(serverName);
            System.out.println("[COORDENADOR] Servidor já cadastrado: " + serverName + " (rank=" + server.getRank() + ")");
        } else {
            // PARTE 3: Atribuir novo rank
            server = new ServerRecord(serverName, nextRank++);
            servers.put(serverName, server);
            System.out.println("[COORDENADOR] NOVO servidor cadastrado: " + serverName + " (rank=" + server.getRank() + ")");
        }
        
        Response response = new Response(true, "Rank atribuído");
        response.setRank(server.getRank());
        return response;
    }
    
    // PARTE 3: Processar requisição de lista (enunciado linha 22)
    private static Response processGetServerList(Message msg) {
        List<ServerRecord> serverList = new ArrayList<>(servers.values());
        
        System.out.println("[COORDENADOR] Retornando lista com " + serverList.size() + " servidores");
        for (ServerRecord s : serverList) {
            System.out.println("  - " + s.getName() + " (rank=" + s.getRank() + ")");
        }
        
        Response response = new Response(true, "Lista de servidores");
        response.setServerList(serverList);
        return response;
    }
    
    // PARTE 4: Processar heartbeat (sem retornar hora - eleição cuida da sincronização)
    private static Response processHeartbeat(Message msg) {
        String serverName = msg.getUsername();
        
        if (serverName == null || !servers.containsKey(serverName)) {
            return new Response(false, "Servidor não cadastrado");
        }
        
        // Atualizar timestamp do último heartbeat
        ServerRecord server = servers.get(serverName);
        server.setLastHeartbeat(System.currentTimeMillis());
        
        System.out.println("[COORDENADOR] Heartbeat de: " + serverName + " (rank=" + server.getRank() + ")");
        
        // PARTE 4: Não retorna mais currentTime - sincronização é feita via eleição
        Response response = new Response(true, "Heartbeat OK");
        return response;
    }
    
    // Thread para remover servidores inativos 
    private static void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000); 
                    
                    long currentTime = System.currentTimeMillis();
                    List<String> inactiveServers = new ArrayList<>();
                    
                    // Identificar servidores inativos
                    for (Map.Entry<String, ServerRecord> entry : servers.entrySet()) {
                        ServerRecord server = entry.getValue();
                        long timeSinceLastHeartbeat = currentTime - server.getLastHeartbeat();
                        
                        if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT) {
                            inactiveServers.add(entry.getKey());
                        }
                    }
                    
                    // Remover servidores inativos
                    for (String serverName : inactiveServers) {
                        ServerRecord removed = servers.remove(serverName);
                        System.out.println("[COORDENADOR] Servidor REMOVIDO por inatividade: " + 
                                         serverName + " (rank=" + removed.getRank() + ")");
                    }
                    
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
}
