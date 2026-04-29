import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.io.*;
import java.util.*;

public class servidor {
    // contador para sincronizar eventos
    private static long logicalClock = 0;
    
    private static int serverRank = -1;
    private static String serverName = "servidor-java-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    
    // Contador de mensagens para heartbeat (a cada 10 mensagens)
    private static int messageCount = 0;
    
    // PARTE 4: Variáveis para eleição e sincronização
    private static String currentCoordinator = null;  // Nome do coordenador atual
    private static boolean isCoordinator = false;  // Se este servidor é o coordenador
    private static int syncMessageCount = 0;  // Contador para sincronização (a cada 15 mensagens)
    private static List<ServerRecord> knownServers = new ArrayList<>();  // Lista de servidores conhecidos
    private static boolean electionInProgress = false;  // Flag para evitar múltiplas eleições simultâneas
    private static volatile boolean syncResponseReceived = false;  // Flag para detectar resposta de sincronização
    private static final long SYNC_TIMEOUT = 3000;  // Timeout de 3 segundos para sincronização
    
    // Dados em memória
    private static Set<String> usuariosLogados = new HashSet<>();
    private static Map<String, Long> timestampsLogin = new HashMap<>();
    private static Set<String> canais = new HashSet<>();
    private static List<PublicationRecord> publicacoes = new ArrayList<>();
    
    // Arquivos próprios do servidor (salvos em volume montado) - persistencia
    private static final String USERS_FILE = "/app/data/users_data.ser";
    private static final String CHANNELS_FILE = "/app/data/channels_data.ser";
    //parte 2: publicação
    private static final String PUBLICATIONS_FILE = "/app/data/publications_data.ser";
    
    // Socket para publicar no proxy
    private static ZMQ.Socket publisherSocket;
    
    // PARTE 3: Socket para comunicar com coordenador
    private static ZMQ.Socket coordinatorSocket;
    
    // PARTE 4: Sockets para comunicação entre servidores
    private static ZMQ.Socket serverPubSocket;  // Para publicar anúncios de coordenador
    private static ZMQ.Socket serverSubSocket;  // Para receber anúncios de coordenador
    private static ZMQ.Socket serverRepSocket;  // Para responder requisições de eleição de outros servidores
    private static int serverPort;  // Porta do servidor para eleição
    
    public static void main(String[] args) {
        // Porta para eleição (passada como argumento)
        if (args.length > 0) {
            serverPort = Integer.parseInt(args[0]);
        } else {
            serverPort = 6001; // Porta padrão
        }
        
        try (ZContext context = new ZContext()) {
            // ZeroMQ REP socket para requisições
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.connect("tcp://broker:5556");
            
            // ZeroMQ PUB socket para publicações no proxy
            publisherSocket = context.createSocket(ZMQ.PUB);
            publisherSocket.connect("tcp://proxy:5557");
            
            // PARTE 3: Socket REQ para comunicar com coordenador
            coordinatorSocket = context.createSocket(ZMQ.REQ);
            coordinatorSocket.connect("tcp://coordinator:5559");
            
            // PARTE 4: Socket PUB para publicar no tópico "servers"
            serverPubSocket = context.createSocket(ZMQ.PUB);
            serverPubSocket.connect("tcp://proxy:5557");
            
            // PARTE 4: Socket SUB para receber do tópico "servers"
            serverSubSocket = context.createSocket(ZMQ.SUB);
            serverSubSocket.connect("tcp://proxy:5558");
            serverSubSocket.subscribe("servers".getBytes());
            
            // PARTE 4: Socket REP para receber requisições de eleição de outros servidores
            serverRepSocket = context.createSocket(ZMQ.REP);
            serverRepSocket.bind("tcp://*:" + serverPort);
            
            System.out.println("Servidor Java Iniciado: " + serverName);
            System.out.println("Conectado ao broker (REQ-REP) e ao proxy (PUB-SUB)");
            System.out.println("Inscrito no tópico 'servers' para eleição");
            System.out.println("Socket REP para eleição na porta: " + serverPort);
            
            // Aguardar para o proxy estar pronto E para a inscrição ser efetivada
            Thread.sleep(2000);
            
            // PARTE 3: Pedir rank ao coordenador
            requestRank();
            
            // PARTE 4: Obter lista de servidores
            getServerList();
            
            // PARTE 4: Iniciar thread para receber mensagens de eleição/coordenação
            Thread serverMessageThread = new Thread(() -> receiveServerMessages());
            serverMessageThread.setDaemon(true);
            serverMessageThread.start();
        
            carregarDados();
            
            // PARTE 4: Criar Poller para receber de clientes, eleições E anúncios PUB/SUB
            ZMQ.Poller poller = context.createPoller(3);
            poller.register(socket, ZMQ.Poller.POLLIN);           // índice 0: Socket para clientes
            poller.register(serverRepSocket, ZMQ.Poller.POLLIN);  // índice 1: Socket para eleições
            poller.register(serverSubSocket, ZMQ.Poller.POLLIN);  // índice 2: Socket PUB/SUB para anúncios
            
            while (true) {
                try {
                    // Poll com timeout de 100ms
                    int events = poller.poll(100);
                    
                    if (events > 0) {
                        // PRIORIDADE 1: Verificar anúncios de coordenador via PUB/SUB
                        if (poller.pollin(2)) {
                            String topic = serverSubSocket.recvStr();
                            byte[] msgBytes = serverSubSocket.recv(0);
                            Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                            
                            if ("coordinator".equals(msg.getType())) {
                                handleCoordinatorMessage(msg);
                            } else if ("election".equals(msg.getType())) {
                                handleElectionMessage(msg);
                            } else if ("election_ok".equals(msg.getType())) {
                                handleElectionOkMessage(msg);
                            } else if ("sync_request".equals(msg.getType())) {
                                handleSyncRequest(msg);
                            } else if ("sync_response".equals(msg.getType())) {
                                handleSyncResponse(msg);
                            } else if ("sync_update".equals(msg.getType())) {
                                handleSyncUpdate(msg);
                            }
                        }
                        
                        // PRIORIDADE 2: Verificar se há requisição de eleição de outro servidor
                        if (poller.pollin(1)) {
                            handleElectionRequest(serverRepSocket);
                        }
                        
                        // PRIORIDADE 3: Verificar se há mensagem de cliente
                        if (poller.pollin(0)) {
                            // Receber por ZeroMQ
                            byte[] msgBytes = socket.recv(0);
                            Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                    
                            // PARTE 3: AO RECEBER - comparar contador e atualizar se necessário
                            long receivedClock = msg.getLogicalClock();
                            long oldClock = logicalClock;
                            
                            if (receivedClock > logicalClock) {
                                // Contador recebido é MAIOR - atualizar
                                logicalClock = receivedClock + 1;
                                System.out.println("[RELÓGIO] ATUALIZADO! Recebido=" + receivedClock +
                                                 " > local=" + oldClock + ". Novo=" + logicalClock);
                            } else {
                                // Contador local é MAIOR/IGUAL - manter
                                logicalClock++;
                                System.out.println("[RELÓGIO] MANTIDO! Recebido=" + receivedClock +
                                                 " <= local=" + oldClock + ". Novo=" + logicalClock);
                            }
                            
                            System.out.println("\n MENSAGEM RECEBIDA UHLL");
                            System.out.println("Tipo: " + msg.getType());
                            System.out.println("Username: " + msg.getUsername());
                            System.out.println("Channel Name: " + msg.getChannelName());
                            System.out.println("Timestamp: " + msg.getTimestamp());
                            System.out.println("Relógio Recebido: " + receivedClock);
                            System.out.println("Bytes recebidos: " + msgBytes.length);
                            
                            Response response;
                            
                            // Processar baseado no tipo
                            if ("login".equals(msg.getType())) {
                                response = processarLogin(msg);
                            } else if ("create_channel".equals(msg.getType())) {
                                response = processarCriarCanal(msg);
                            } else if ("list_channels".equals(msg.getType())) {
                                response = processarListarCanais(msg);
                            } else if ("publish".equals(msg.getType())) {
                                response = processarPublicacao(msg);
                            } else {
                                response = new Response(false, "Tipo desconhecido: " + msg.getType());
                            }
                            
                            // PARTE 3: ANTES DE ENVIAR - incrementar contador
                            logicalClock++;
                            response.setLogicalClock(logicalClock);
                            System.out.println("[RELÓGIO] Incrementado ANTES de enviar: " + logicalClock);
                         
                            System.out.println("\nENVIANDO RESPOSTA !!!!");
                            System.out.println("Success: " + response.isSuccess());
                            System.out.println("Message: " + response.getMessage());
                            System.out.println("Timestamp: " + response.getTimestamp());
                            System.out.println("Relógio Enviado: " + logicalClock);
                            
                           
                            byte[] responseBytes = MessagePackUtil.serialize(response);
                            socket.send(responseBytes, 0);
                            
                            // PARTE 3: Incrementar contador e enviar heartbeat a cada 10 mensagens
                            messageCount++;
                            if (messageCount >= 10) {
                                System.out.println("\n[HEARTBEAT] 10 mensagens processadas, enviando heartbeat...");
                                sendHeartbeat();
                                messageCount = 0;
                            }
                            
                            // PARTE 4: Sincronizar relógio a cada 15 mensagens (enunciado4 linha 3)
                            syncMessageCount++;
                            if (syncMessageCount >= 15) {
                                System.out.println("\n[SYNC] 15 mensagens processadas, sincronizando relógio...");
                                
                                // CRÍTICO: Se já estou em eleição, não fazer nada
                                if (electionInProgress) {
                                    System.out.println("[SYNC] Eleição em andamento, pulando sincronização");
                                    syncMessageCount = 0;
                                } else if (isCoordinator) {
                                    // Se sou coordenador, inicio sincronização Berkeley
                                    System.out.println("[SYNC] Sou coordenador, executando Berkeley...");
                                    synchronizeClocks();
                                    syncMessageCount = 0;
                                } else {
                                    // Tentar sincronizar com coordenador
                                    if (currentCoordinator != null) {
                                        System.out.println("[SYNC] Tentando sincronizar com coordenador: " + currentCoordinator);
                                        boolean coordAvailable = requestSyncFromCoordinator();
                                        if (!coordAvailable) {
                                            // Coordenador não disponível, iniciar eleição
                                            System.out.println("[SYNC] Coordenador não disponível! Iniciando eleição...");
                                            currentCoordinator = null;
                                            isCoordinator = false;
                                            startElection();
                                        }
                                        syncMessageCount = 0;
                                    } else {
                                        // Sem coordenador, iniciar eleição com delay aleatório
                                        System.out.println("[SYNC] Sem coordenador definido, iniciando eleição...");
                                        // Adicionar delay aleatório para evitar eleições simultâneas
                                        new Thread(() -> {
                                            try {
                                                long delay = (long)(Math.random() * 2000); // 0-2 segundos
                                                System.out.println("[SYNC] Aguardando " + delay + "ms antes de iniciar eleição...");
                                                Thread.sleep(delay);
                                                startElection();
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                        }).start();
                                        syncMessageCount = 0;
                                    }
                                }
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    System.err.println("Erro: " + e.getMessage());
                    Response errorResponse = new Response(false, "Erro no servidor");
                    // PARTE 3: Incrementar antes de enviar erro
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
    

    private static Response processarLogin(Message msg) {
        String username = msg.getUsername();
        
    
        if (username == null || username.trim().isEmpty()) {
            return new Response(false, "Nome de usuário inválido");
        }
        
        // Registrar
        usuariosLogados.add(username);
        timestampsLogin.put(username, msg.getTimestamp());
        
      
        salvarDados();
        
        System.out.println("Login registrado: " + username);
        return new Response(true, "Login realizado com sucesso para " + username);
    }
    
    private static Response processarCriarCanal(Message msg) {
        String username = msg.getUsername();
        String channelName = msg.getChannelName();
        
        // Usuário deve estar logado
        if (username == null || !usuariosLogados.contains(username)) {
            System.out.println("Tentativa de criar canal sem estar logado");
            return new Response(false, "Você precisa fazer login antes de criar um canal");
        }
        
        if (channelName == null || channelName.trim().isEmpty()) {
            System.out.println("Tentativa de criar canal com nome inválido");
            return new Response(false, "Nome do canal não pode ser vazio");
        }
        
        if (canais.contains(channelName)) {
            System.out.println("Canal já existe: " + channelName);
            return new Response(false, "Canal '" + channelName + "' já existe");
        }
        
        // Criar canal
        canais.add(channelName);
        
        salvarDados();
        
        System.out.println("Canal criado com sucesso: " + channelName + " por " + username);
        Response response = new Response(true, "Canal '" + channelName + "' criado com sucesso");
        response.setChannelName(channelName);
        return response;
    }
    
    private static Response processarListarCanais(Message msg) {
        String username = msg.getUsername();
        
        if (username == null || !usuariosLogados.contains(username)) {
            System.out.println("Tentativa de listar canais sem estar logado");
            return new Response(false, "Você precisa fazer login antes de listar canais");
        }
        
        // Converter Set para List para enviar na resposta
        List<String> listaCanais = new ArrayList<>(canais);
        
        System.out.println("Listando " + listaCanais.size() + " canais para " + username);
        
        Response response = new Response(true, "Total de canais: " + listaCanais.size());
        response.setChannels(listaCanais);
        return response;
    }
    
    private static Response processarPublicacao(Message msg) {
        String username = msg.getUsername();
        String channelName = msg.getChannelName();
        String content = msg.getContent();
        
        // Validações
        if (username == null || !usuariosLogados.contains(username)) {
            System.out.println("Tentativa de publicar sem estar logado");
            return new Response(false, "Você precisa fazer login antes de publicar");
        }
        
        if (channelName == null || channelName.trim().isEmpty()) {
            System.out.println("Tentativa de publicar sem especificar canal");
            return new Response(false, "Nome do canal não pode ser vazio");
        }
        
        if (!canais.contains(channelName)) {
            System.out.println("Tentativa de publicar em canal inexistente: " + channelName);
            return new Response(false, "Canal '" + channelName + "' não existe");
        }
        
        if (content == null || content.trim().isEmpty()) {
            System.out.println("Tentativa de publicar mensagem vazia");
            return new Response(false, "Conteúdo da mensagem não pode ser vazio");
        }
        
        try {
            // Criar mensagem para publicação
            Message pubMsg = new Message("publication");
            pubMsg.setUsername(username);
            pubMsg.setChannelName(channelName);
            pubMsg.setContent(content);
            pubMsg.setTimestamp(msg.getTimestamp());
            
            // Serializar e publicar no proxy usando o canal como tópico
            byte[] pubBytes = MessagePackUtil.serialize(pubMsg);
            
            // Enviar tópico (canal) + mensagem
            publisherSocket.sendMore(channelName);
            publisherSocket.send(pubBytes, 0);
            
            // Registrar publicação
            PublicationRecord record = new PublicationRecord(username, channelName, content, msg.getTimestamp());
            publicacoes.add(record);
            
            // Persistir
            salvarDados();
            
            System.out.println("Publicação realizada: [" + channelName + "] " + username + ": " + content);
            
            Response response = new Response(true, "Mensagem publicada com sucesso no canal '" + channelName + "'");
            response.setChannelName(channelName);
            response.setPublicationStatus("published");
            return response;
            
        } catch (Exception e) {
            System.err.println("Erro ao publicar mensagem: " + e.getMessage());
            return new Response(false, "Erro ao publicar mensagem: " + e.getMessage());
        }
    }
    

    // Salvar dados em disco
    private static void salvarDados() {
        try {
            // Salvar usuários
            ObjectOutputStream usersOutputStream = new ObjectOutputStream(
                new FileOutputStream(USERS_FILE)
            );
            usersOutputStream.writeObject(timestampsLogin);
            usersOutputStream.close();
            
            // Salvar canais
            ObjectOutputStream channelsOutputStream = new ObjectOutputStream(
                new FileOutputStream(CHANNELS_FILE)
            );
            channelsOutputStream.writeObject(canais);
            channelsOutputStream.close();
            
            // Salvar publicações
            ObjectOutputStream publicationsOutputStream = new ObjectOutputStream(
                new FileOutputStream(PUBLICATIONS_FILE)
            );
            publicationsOutputStream.writeObject(publicacoes);
            publicationsOutputStream.close();
            
            System.out.println("Dados persistidos em disco (usuários + canais + publicações)");
        } catch (Exception e) {
            System.err.println("Erro ao salvar: " + e.getMessage());
        }
    }
    
    // Carregar dados do disco
    @SuppressWarnings("unchecked")
    private static void carregarDados() {
        try {
            // Carregar usuários
            File usersFile = new File(USERS_FILE);
            if (usersFile.exists()) {
                ObjectInputStream usersInputStream = new ObjectInputStream(
                    new FileInputStream(usersFile)
                );
                timestampsLogin = (Map<String, Long>) usersInputStream.readObject();
                usuariosLogados.addAll(timestampsLogin.keySet());
                usersInputStream.close();
                System.out.println("Usuários carregados: " + usuariosLogados.size());
            }
            
            // Carregar canais
            File channelsFile = new File(CHANNELS_FILE);
            if (channelsFile.exists()) {
                ObjectInputStream channelsInputStream = new ObjectInputStream(
                    new FileInputStream(channelsFile)
                );
                canais = (Set<String>) channelsInputStream.readObject();
                channelsInputStream.close();
                System.out.println("Canais carregados: " + canais.size());
            }
            
            // Carregar publicações
            File publicationsFile = new File(PUBLICATIONS_FILE);
            if (publicationsFile.exists()) {
                ObjectInputStream publicationsInputStream = new ObjectInputStream(
                    new FileInputStream(publicationsFile)
                );
                publicacoes = (List<PublicationRecord>) publicationsInputStream.readObject();
                publicationsInputStream.close();
                System.out.println("Publicações carregadas: " + publicacoes.size());
            }
        } catch (Exception e) {
            System.err.println("Erro ao carregar: " + e.getMessage());
        }
    }

    // Pedir rank ao coordenador na inicialização (enunciado linha 14)
    private static void requestRank() {
        try {
            Message rankMsg = new Message("get_rank");
            rankMsg.setUsername(serverName);
            
            // ANTES DE ENVIAR incrementar relógio
            logicalClock++;
            rankMsg.setLogicalClock(logicalClock);
            System.out.println("SERVIDOR RELÓGIO Incrementado antes de pedir rank: " + logicalClock);
            
            byte[] msgBytes = MessagePackUtil.serialize(rankMsg);
            coordinatorSocket.send(msgBytes, 0);
            
            byte[] responseBytes = coordinatorSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            // AO RECEBER - atualizar relógio
            long receivedClock = response.getLogicalClock();
            long oldClock = logicalClock;
            
            if (receivedClock > logicalClock) {
                logicalClock = receivedClock + 1;
                System.out.println("[SERVIDOR][RELÓGIO] ATUALIZADO! Recebido=" + receivedClock + 
                                 " > local=" + oldClock + ". Novo=" + logicalClock);
            } else {
                logicalClock++;
                System.out.println("[SERVIDOR][RELÓGIO] MANTIDO! Recebido=" + receivedClock + 
                                 " <= local=" + oldClock + ". Novo=" + logicalClock);
            }
            
            if (response.isSuccess()) {
                serverRank = response.getRank();
                System.out.println("[SERVIDOR] Rank recebido do coordenador: " + serverRank);
            } else {
                System.err.println("[SERVIDOR] Erro ao pedir rank: " + response.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[SERVIDOR] Erro ao pedir rank: " + e.getMessage());
        }
    }
    
    // Enviar heartbeat ao coordenador (enunciado linha 27 e 33)
    private static void sendHeartbeat() {
        try {
            Message heartbeatMsg = new Message("heartbeat");
            heartbeatMsg.setUsername(serverName);
            
            //ANTES DE ENVIAR - incrementar relógio
            logicalClock++;
            heartbeatMsg.setLogicalClock(logicalClock);
            System.out.println("[HEARTBEAT][RELÓGIO] Incrementado antes de enviar: " + logicalClock);
            
            byte[] msgBytes = MessagePackUtil.serialize(heartbeatMsg);
            coordinatorSocket.send(msgBytes, 0);
            
            byte[] responseBytes = coordinatorSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            // AO RECEBER - atualizar relógio
            long receivedClock = response.getLogicalClock();
            long oldClock = logicalClock;
            
            if (receivedClock > logicalClock) {
                logicalClock = receivedClock + 1;
                System.out.println("[HEARTBEAT][RELÓGIO] ATUALIZADO! Recebido=" + receivedClock + 
                                 " > local=" + oldClock + ". Novo=" + logicalClock);
            } else {
                logicalClock++;
                System.out.println("[HEARTBEAT][RELÓGIO] MANTIDO! Recebido=" + receivedClock + 
                                 " <= local=" + oldClock + ". Novo=" + logicalClock);
            }
            
            if (response.isSuccess()) {
                System.out.println("[HEARTBEAT] Enviado com sucesso!");
            } else {
                System.err.println("[HEARTBEAT] Erro: " + response.getMessage());
                
                // Se servidor não está cadastrado, re-registrar
                if (response.getMessage() != null && response.getMessage().contains("não cadastrado")) {
                    System.out.println("[HEARTBEAT] Re-registrando servidor no coordenador...");
                    requestRank();
                    getServerList();
                }
            }
        } catch (Exception e) {
            System.err.println("[HEARTBEAT] Erro ao enviar: " + e.getMessage());
        }
    }
    
    // PARTE 4: Obter lista de servidores do coordenador
    private static void getServerList() {
        try {
            Message listMsg = new Message("get_server_list");
            listMsg.setUsername(serverName);
            
            logicalClock++;
            listMsg.setLogicalClock(logicalClock);
            
            byte[] msgBytes = MessagePackUtil.serialize(listMsg);
            coordinatorSocket.send(msgBytes, 0);
            
            byte[] responseBytes = coordinatorSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            long receivedClock = response.getLogicalClock();
            if (receivedClock > logicalClock) {
                logicalClock = receivedClock + 1;
            } else {
                logicalClock++;
            }
            
            if (response.isSuccess() && response.getServerList() != null) {
                knownServers = new ArrayList<>(response.getServerList());
                System.out.println("[SERVIDOR] Lista de servidores recebida: " + knownServers.size() + " servidores");
                for (ServerRecord s : knownServers) {
                    System.out.println("  - " + s.getName() + " (rank=" + s.getRank() + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVIDOR] Erro ao obter lista: " + e.getMessage());
        }
    }
    
    // PARTE 4: Thread para receber mensagens de eleição/coordenação
    private static void receiveServerMessages() {
        System.out.println("[SERVIDOR] Thread de mensagens entre servidores iniciada");
        while (true) {
            try {
                // Receber tópico
                String topic = serverSubSocket.recvStr(0);
                
                // Receber mensagem
                byte[] msgBytes = serverSubSocket.recv(0);
                Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                
                // Atualizar relógio lógico
                long receivedClock = msg.getLogicalClock();
                if (receivedClock > logicalClock) {
                    logicalClock = receivedClock + 1;
                } else {
                    logicalClock++;
                }
                
                System.out.println("\n[SERVIDOR] Mensagem recebida no tópico 'servers'");
                System.out.println("  Tipo: " + msg.getType());
                System.out.println("  De: " + msg.getUsername());
                
                // Processar baseado no tipo
                if ("election".equals(msg.getType())) {
                    handleElectionMessage(msg);
                } else if ("election_ok".equals(msg.getType())) {
                    handleElectionOkMessage(msg);
                } else if ("coordinator".equals(msg.getType())) {
                    handleCoordinatorMessage(msg);
                } else if ("sync_request".equals(msg.getType())) {
                    handleSyncRequest(msg);
                } else if ("sync_response".equals(msg.getType())) {
                    handleSyncResponse(msg);
                } else if ("sync_update".equals(msg.getType())) {
                    handleSyncUpdate(msg);
                }
                
            } catch (Exception e) {
                System.err.println("[SERVIDOR] Erro ao receber mensagem: " + e.getMessage());
            }
        }
    }
    
    // PARTE 4: Iniciar eleição (algoritmo Bully) - USANDO REQ/REP
    private static void startElection() {
        if (electionInProgress) {
            System.out.println("[ELEIÇÃO] Eleição já em andamento, ignorando...");
            return;
        }
        
        electionInProgress = true;
        System.out.println("\n[ELEIÇÃO] Iniciando eleição Bully...");
        System.out.println("[ELEIÇÃO] Meu rank: " + serverRank);
        
        // CRÍTICO: Atualizar lista de servidores antes de iniciar eleição
        System.out.println("[ELEIÇÃO] Atualizando lista de servidores do coordenador...");
        try {
            getServerList();
            System.out.println("[ELEIÇÃO] Lista atualizada com sucesso!");
        } catch (Exception e) {
            System.err.println("[ELEIÇÃO] ERRO ao atualizar lista: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("[ELEIÇÃO] Servidores conhecidos: " + knownServers.size());
        for (ServerRecord s : knownServers) {
            System.out.println("[ELEIÇÃO]   - " + s.getName() + " (rank=" + s.getRank() + ")");
        }
        
        // Enviar mensagem de eleição para servidores com rank maior usando REQ/REP
        boolean sentToHigher = false;
        boolean receivedOK = false;
        
        try (ZContext electionContext = new ZContext()) {
            for (ServerRecord server : knownServers) {
                if (server.getRank() > serverRank) {
                    sentToHigher = true;
                    System.out.println("[ELEIÇÃO] Enviando ELECTION para " + server.getName() + " (rank=" + server.getRank() + ")");
                    
                    // Criar socket REQ para cada servidor
                    ZMQ.Socket reqSocket = electionContext.createSocket(ZMQ.REQ);
                    
                    // Conectar ao servidor usando o hostname do docker-compose
                    String serverHost = "servidorJava" + server.getRank(); // servidorJava1, servidorJava2, servidorJava3
                    int serverPort = 6000 + server.getRank(); // 6001, 6002, 6003
                    String address = "tcp://" + serverHost + ":" + serverPort;
                    
                    System.out.println("[ELEIÇÃO] Conectando a: " + address);
                    reqSocket.connect(address);
                    reqSocket.setReceiveTimeOut(2000); // Timeout de 2 segundos
                    
                    // Criar mensagem de eleição
                    Message electionMsg = new Message("election");
                    electionMsg.setUsername(serverName);
                    electionMsg.setElectionId(serverRank);
                    
                    logicalClock++;
                    electionMsg.setLogicalClock(logicalClock);
                    
                    // Enviar requisição
                    byte[] msgBytes = MessagePackUtil.serialize(electionMsg);
                    reqSocket.send(msgBytes, 0);
                    
                    // Aguardar resposta OK
                    byte[] responseBytes = reqSocket.recv(0);
                    if (responseBytes != null) {
                        Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
                        if ("OK".equals(response.getMessage())) {
                            System.out.println("[ELEIÇÃO] Recebido OK de " + server.getName());
                            receivedOK = true;
                        }
                    } else {
                        System.out.println("[ELEIÇÃO] Timeout ao aguardar resposta de " + server.getName());
                    }
                    
                    reqSocket.close();
                }
            }
        } catch (Exception e) {
            System.err.println("[ELEIÇÃO] Erro ao enviar mensagens: " + e.getMessage());
        }
        
        if (sentToHigher) {
            if (receivedOK) {
                // Alguém com rank maior respondeu, aguardar anúncio de coordenador
                System.out.println("[ELEIÇÃO] Servidor(es) com rank maior responderam, aguardando anúncio...");
                
               for (int i = 0; i < 50; i++) { 
                    try {
                        Thread.sleep(100); 
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    
                    // Verificar se coordenador foi definido ou eleição cancelada
                    if (!electionInProgress || currentCoordinator != null) {
                        System.out.println("[ELEIÇÃO] Coordenador já foi definido: " + currentCoordinator);
                        electionInProgress = false;
                        return;
                    }
                }
                
                // Se após 5 segundos ainda não há coordenador, me torno coordenador
                System.out.println("[ELEIÇÃO] Nenhum anúncio recebido após 5s, me tornando coordenador");
                becomeCoordinator();
            } else {
                // Ninguém respondeu, me torno coordenador
                System.out.println("[ELEIÇÃO] Nenhuma resposta recebida, me tornando coordenador");
                becomeCoordinator();
            }
        } else {
            // Nenhum servidor com rank maior, me torno coordenador
            System.out.println("[ELEIÇÃO] Nenhum servidor com rank maior, me tornando coordenador");
            becomeCoordinator();
        }
    }
    
    // PARTE 4: Processar requisição de eleição via REP socket
    private static void handleElectionRequest(ZMQ.Socket repSocket) {
        try {
            // Receber mensagem de eleição
            byte[] msgBytes = repSocket.recv(0);
            Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
            
            int senderRank = msg.getElectionId();
            String senderName = msg.getUsername();
            
            System.out.println("[ELEIÇÃO] Recebida requisição ELECTION de " + senderName + " (rank=" + senderRank + ")");
            
            // Atualizar relógio lógico
            long receivedClock = msg.getLogicalClock();
            if (receivedClock > logicalClock) {
                logicalClock = receivedClock + 1;
            } else {
                logicalClock++;
            }
            
            // Se o rank do remetente é menor que o meu, respondo OK
            if (senderRank < serverRank) {
                System.out.println("[ELEIÇÃO] Meu rank (" + serverRank + ") é maior, respondendo OK");
                
                // Responder OK
                Response okResponse = new Response(true, "OK");
                okResponse.setLogicalClock(logicalClock);
                byte[] responseBytes = MessagePackUtil.serialize(okResponse);
                repSocket.send(responseBytes, 0);
                
                // Iniciar eleição APENAS se não estiver em progresso
                // Se já estou em eleição, apenas continuo (não reinicio)
                if (!electionInProgress) {
                    System.out.println("[ELEIÇÃO] Não estava em eleição, iniciando agora");
                    new Thread(() -> {
                        try {
                            Thread.sleep(100); // Pequeno delay para garantir que a resposta foi enviada
                            startElection();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                } else {
                    System.out.println("[ELEIÇÃO] Já estou em eleição, apenas respondi OK");
                }
            } else {
                // Rank do remetente é maior ou igual, apenas respondo
                System.out.println("[ELEIÇÃO] Rank do remetente >= meu rank, apenas respondendo");
                Response response = new Response(true, "ACK");
                response.setLogicalClock(logicalClock);
                byte[] responseBytes = MessagePackUtil.serialize(response);
                repSocket.send(responseBytes, 0);
            }
            
        } catch (Exception e) {
            System.err.println("[ELEIÇÃO] Erro ao processar requisição: " + e.getMessage());
        }
    }
    
    // PARTE 4: Processar mensagem de eleição (mantido para compatibilidade com PUB/SUB)
    private static void handleElectionMessage(Message msg) {
        int senderRank = msg.getElectionId();
        String senderName = msg.getUsername();
        System.out.println("[ELEIÇÃO] Mensagem de eleição recebida de " + senderName + " (rank=" + senderRank + ")");
        
        if (senderRank < serverRank) {
            // Meu rank é maior, respondo para cancelar a eleição dele
            System.out.println("[ELEIÇÃO] Meu rank (" + serverRank + ") é maior, respondendo OK");
            
            // Enviar resposta de eleição (OK)
            try {
                Message responseMsg = new Message("election_ok");
                responseMsg.setUsername(serverName);
                responseMsg.setElectionId(serverRank);
                
                logicalClock++;
                responseMsg.setLogicalClock(logicalClock);
                
                byte[] msgBytes = MessagePackUtil.serialize(responseMsg);
                serverPubSocket.sendMore("servers");
                serverPubSocket.send(msgBytes, 0);
                
                System.out.println("[ELEIÇÃO] Resposta OK enviada para " + senderName);
            } catch (Exception e) {
                System.err.println("[ELEIÇÃO] Erro ao enviar resposta: " + e.getMessage());
            }
            
            // Iniciar minha própria eleição APENAS se não estou em uma eleição
            if (!electionInProgress) {
                System.out.println("[ELEIÇÃO] Iniciando minha própria eleição");
                startElection();
            } else {
                System.out.println("[ELEIÇÃO] Já estou em eleição, não inicio outra");
            }
        }
    }
    
    // PARTE 4: Me tornar coordenador
    private static synchronized void becomeCoordinator() {
        // Aguardar um pouco para garantir que mensagens de outros servidores sejam processadas
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Verificar se outro servidor já se declarou coordenador
        if (currentCoordinator != null && !currentCoordinator.equals(serverName)) {
            System.out.println("[ELEIÇÃO] Outro servidor já é coordenador: " + currentCoordinator);
            electionInProgress = false;
            return;
        }
        
        // Verificar se ainda estou em eleição (pode ter sido cancelada)
        if (!electionInProgress) {
            System.out.println("[ELEIÇÃO] Eleição foi cancelada, não me torno coordenador");
            return;
        }
        
        try {
            for (ServerRecord server : knownServers) {
                if (server.getRank() > serverRank) {
                    System.out.println("[ELEIÇÃO] Servidor com rank maior existe: " + server.getName() + " (rank=" + server.getRank() + "), não me torno coordenador");
                    electionInProgress = false;
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[ELEIÇÃO] Erro ao verificar ranks: " + e.getMessage());
        }
        
        isCoordinator = true;
        currentCoordinator = serverName;
        electionInProgress = false;
        
        System.out.println("\n[COORDENADOR] *** EU SOU O NOVO COORDENADOR ***");
        System.out.println("[COORDENADOR] Nome: " + serverName);
        System.out.println("[COORDENADOR] Rank: " + serverRank);
        
        // Anunciar para todos os servidores
        announceCoordinator();
    }
    
    // PARTE 4: Processar resposta OK de eleição
    private static void handleElectionOkMessage(Message msg) {
        int senderRank = msg.getElectionId();
        String senderName = msg.getUsername();
        System.out.println("[ELEIÇÃO] Resposta OK recebida de " + senderName + " (rank=" + senderRank + ")");
        
        // Um servidor de rank maior respondeu, então cancelo minha eleição
        if (senderRank > serverRank) {
            System.out.println("[ELEIÇÃO] Servidor de rank maior respondeu, cancelando minha eleição");
            electionInProgress = false;
        }
    }
    
    // PARTE 4: Anunciar que sou o coordenador
    private static void announceCoordinator() {
        try {
            // Aguardar um pouco para garantir que todos os subscribers estão prontos
            Thread.sleep(500);
            
            Message coordMsg = new Message("coordinator");
            coordMsg.setUsername(serverName);
            coordMsg.setCoordinatorName(serverName);
            
            logicalClock++;
            coordMsg.setLogicalClock(logicalClock);
            
            byte[] msgBytes = MessagePackUtil.serialize(coordMsg);
            serverPubSocket.sendMore("servers");
            serverPubSocket.send(msgBytes, 0);
            
            System.out.println("[COORDENADOR] Anúncio enviado para todos os servidores");
            
            // Enviar múltiplas vezes para garantir entrega
            Thread.sleep(100);
            serverPubSocket.sendMore("servers");
            serverPubSocket.send(msgBytes, 0);
            
            Thread.sleep(100);
            serverPubSocket.sendMore("servers");
            serverPubSocket.send(msgBytes, 0);
            
        } catch (Exception e) {
            System.err.println("[COORDENADOR] Erro ao anunciar: " + e.getMessage());
        }
    }
    
    // PARTE 4: Processar mensagem de coordenador
    private static void handleCoordinatorMessage(Message msg) {
        String newCoordinator = msg.getCoordinatorName();
        System.out.println("[ELEIÇÃO] Novo coordenador anunciado: " + newCoordinator);
        
        currentCoordinator = newCoordinator;
        isCoordinator = serverName.equals(newCoordinator);
        electionInProgress = false;
        
        if (isCoordinator) {
            System.out.println("[ELEIÇÃO] Confirmado: EU sou o coordenador");
        } else {
            System.out.println("[ELEIÇÃO] Coordenador definido: " + newCoordinator);
        }
    }
    
    // PARTE 4: Sincronizar relógios (algoritmo Berkeley) - apenas coordenador
    private static void synchronizeClocks() {
        if (!isCoordinator) {
            return;
        }
        
        System.out.println("\n[BERKELEY] Iniciando sincronização de relógios...");
        System.out.println("[BERKELEY] Meu relógio: " + System.currentTimeMillis());
        
        try {
            // Solicitar relógios de todos os servidores
            Message syncReq = new Message("sync_request");
            syncReq.setUsername(serverName);
            syncReq.setTimestamp(System.currentTimeMillis());
            
            logicalClock++;
            syncReq.setLogicalClock(logicalClock);
            
            byte[] msgBytes = MessagePackUtil.serialize(syncReq);
            serverPubSocket.sendMore("servers");
            serverPubSocket.send(msgBytes, 0);
            
            System.out.println("[BERKELEY] Requisição de sincronização enviada");
            
            // Aguardar respostas (2 segundos)
            Thread.sleep(2000);
            
            // Calcular offset médio e enviar ajustes
            System.out.println("[BERKELEY] Sincronização concluída");
            
        } catch (Exception e) {
            System.err.println("[BERKELEY] Erro: " + e.getMessage());
        }
    }
    
    // PARTE 4: Processar requisição de sincronização
    private static void handleSyncRequest(Message msg) {
        String requester = msg.getUsername();
        
        if (isCoordinator) {
            // Coordenador recebe requisição de outro servidor
            System.out.println("[BERKELEY] Requisição de sincronização recebida de " + requester);
            
            try {
                // Responder com meu relógio (sou o coordenador)
                Message syncResp = new Message("sync_response");
                syncResp.setUsername(serverName);
                syncResp.setTimestamp(System.currentTimeMillis());
                
                logicalClock++;
                syncResp.setLogicalClock(logicalClock);
                
                byte[] msgBytes = MessagePackUtil.serialize(syncResp);
                serverPubSocket.sendMore("servers");
                serverPubSocket.send(msgBytes, 0);
                
                System.out.println("[BERKELEY] Resposta enviada ao servidor " + requester);
                
            } catch (Exception e) {
                System.err.println("[BERKELEY] Erro ao responder: " + e.getMessage());
            }
            return;
        }
        
        // Se não sou coordenador, ignoro requisições de outros servidores
        System.out.println("[BERKELEY] Requisição ignorada (não sou coordenador)");
    }
    
    // PARTE 4: Solicitar sincronização do coordenador e detectar se está disponível
    private static boolean requestSyncFromCoordinator() {
        try {
            // Resetar flag de resposta
            syncResponseReceived = false;
            
            // Enviar requisição de sincronização
            Message syncReq = new Message("sync_request");
            syncReq.setUsername(serverName);
            syncReq.setTimestamp(System.currentTimeMillis());
            
            logicalClock++;
            syncReq.setLogicalClock(logicalClock);
            
            byte[] msgBytes = MessagePackUtil.serialize(syncReq);
            serverPubSocket.sendMore("servers");
            serverPubSocket.send(msgBytes, 0);
            
            System.out.println("[BERKELEY] Requisição de sincronização enviada ao coordenador");
            
            // Aguardar resposta com timeout
            long startTime = System.currentTimeMillis();
            while (!syncResponseReceived && (System.currentTimeMillis() - startTime) < SYNC_TIMEOUT) {
                Thread.sleep(100);
            }
            
            if (syncResponseReceived) {
                System.out.println("[BERKELEY] Coordenador respondeu - disponível");
                return true;
            } else {
                System.out.println("[BERKELEY] Timeout - coordenador não respondeu");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("[BERKELEY] Erro ao solicitar sincronização: " + e.getMessage());
            return false;
        }
    }
    
    // PARTE 4: Processar resposta de sincronização
    private static void handleSyncResponse(Message msg) {
        String sender = msg.getUsername();
        
        // Se não sou coordenador e recebi resposta do coordenador
        if (!isCoordinator && sender.equals(currentCoordinator)) {
            syncResponseReceived = true;
            System.out.println("[BERKELEY]  Resposta recebida do coordenador " + sender);
            System.out.println("[BERKELEY] Relógio do coordenador: " + msg.getTimestamp());
            System.out.println("[BERKELEY] Meu relógio: " + System.currentTimeMillis());
            long diff = msg.getTimestamp() - System.currentTimeMillis();
            System.out.println("[BERKELEY] Diferença: " + diff + "ms");
            return;
        }
        
        // Se sou coordenador, recebo respostas de outros servidores
        if (isCoordinator) {
            System.out.println("[BERKELEY] Resposta de sincronização recebida de " + sender);
            System.out.println("[BERKELEY] Relógio do servidor: " + msg.getTimestamp());
        }
    }
    
    // PARTE 4: Processar atualização de sincronização
    private static void handleSyncUpdate(Message msg) {
        if (isCoordinator) {
            return; // Coordenador não ajusta seu próprio relógio
        }
        
        long offset = msg.getClockOffset();
        System.out.println("[BERKELEY] Atualização de relógio recebida");
        System.out.println("[BERKELEY] Offset a aplicar: " + offset + "ms");
        
      
        System.out.println("[BERKELEY] Relógio ajustado (simulado)");
    }
}