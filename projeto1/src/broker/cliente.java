import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.util.*;

class cliente {
    private static ZMQ.Socket reqSocket;
    private static ZMQ.Socket subSocket;
    private static String username;
    private static List<String> canaisDisponiveis = new ArrayList<>();
    private static Set<String> canaisInscritos = new HashSet<>();
    private static Random random = new Random();
    
    public static void main(String[] args) {
        username = args.length > 0 ? args[0] : "bot-java-" + System.currentTimeMillis();
        
        try (ZContext context = new ZContext()) {
            // Socket REQ para comunicação com servidor (Req-Rep)
            reqSocket = context.createSocket(ZMQ.REQ);
            reqSocket.connect("tcp://broker:5555");
            
            // Socket SUB para receber publicações do proxy (Pub-Sub)
            subSocket = context.createSocket(ZMQ.SUB);
            subSocket.connect("tcp://proxy:5558");
            
            System.out.println("Cliente Java Iniciado: " + username);
            
            // Aguardar serviços estarem prontos
            Thread.sleep(2000);
            
            // Bot automático
            boolean loggedIn = false;
            
            // FAZER LOGIN
            while (!loggedIn) {
                loggedIn = fazerLogin();
                if (!loggedIn) {
                    Thread.sleep(2000);
                }
            }
            
            System.out.println("[" + username + "] Login bem-sucedido!\n");
            
            listarCanais();
            
            // CRIAR CANAIS SE NECESSÁRIO (menos de 5)
            while (canaisDisponiveis.size() < 5) {
                String nomeCanal = "canal-" + username + "-" + System.currentTimeMillis();
                if (criarCanal(nomeCanal)) {
                    canaisDisponiveis.add(nomeCanal);
                    System.out.println("[" + username + "] Canal criado: " + nomeCanal);
                }
                Thread.sleep(100);
                listarCanais(); // Atualizar lista
            }
            
            // INSCREVER-SE EM CANAIS (mínimo 3)
            while (canaisInscritos.size() < 3 && canaisInscritos.size() < canaisDisponiveis.size()) {
                String canalAleatorio = canaisDisponiveis.get(random.nextInt(canaisDisponiveis.size()));
                if (!canaisInscritos.contains(canalAleatorio)) {
                    inscreverEmCanal(canalAleatorio);
                }
            }
            
            // INICIAR THREAD PARA RECEBER MENSAGENS
            Thread receiverThread = new Thread(() -> receberMensagens());
            receiverThread.setDaemon(true);
            receiverThread.start();
            
            System.out.println("[" + username + "] Iniciando loop de publicações...\n");
            
            // PUBLICAR MENSAGENS
            while (true) {
                if (!canaisDisponiveis.isEmpty()) {
                    // Escolher canal aleatório
                    String canal = canaisDisponiveis.get(random.nextInt(canaisDisponiveis.size()));
                    
                    for (int i = 1; i <= 10; i++) {
                        String conteudo = gerarMensagemAleatoria(i);
                        publicarMensagem(canal, conteudo);
                        Thread.sleep(1000); 
                    }
                }
                
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static boolean fazerLogin() {
        try {
            Message loginMsg = new Message("login");
            loginMsg.setUsername(username);
            
            byte[] msgBytes = MessagePackUtil.serialize(loginMsg);
            reqSocket.send(msgBytes, 0);
            
            byte[] responseBytes = reqSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            System.out.println("[" + username + "] Login: " + response.getMessage());
            return response.isSuccess();
            
        } catch (Exception e) {
            System.err.println("[" + username + "] Erro no login: " + e.getMessage());
            return false;
        }
    }
    
    private static void listarCanais() {
        try {
            Message listMsg = new Message("list_channels");
            listMsg.setUsername(username);
            
            byte[] msgBytes = MessagePackUtil.serialize(listMsg);
            reqSocket.send(msgBytes, 0);
            
            byte[] responseBytes = reqSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            if (response.isSuccess() && response.getChannels() != null) {
                canaisDisponiveis = new ArrayList<>(response.getChannels());
                System.out.println("[" + username + "] Canais disponíveis: " + canaisDisponiveis.size());
            }
        } catch (Exception e) {
            System.err.println("[" + username + "] Erro ao listar canais: " + e.getMessage());
        }
    }
    
    private static boolean criarCanal(String nomeCanal) {
        try {
            Message createMsg = new Message("create_channel");
            createMsg.setUsername(username);
            createMsg.setChannelName(nomeCanal);
            
            byte[] msgBytes = MessagePackUtil.serialize(createMsg);
            reqSocket.send(msgBytes, 0);
            
            byte[] responseBytes = reqSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            if (response.isSuccess()) {
                System.out.println("[" + username + "] Canal criado: " + nomeCanal);
            }
            return response.isSuccess();
        } catch (Exception e) {
            System.err.println("[" + username + "] Erro ao criar canal: " + e.getMessage());
            return false;
        }
    }
    
    private static void inscreverEmCanal(String canal) {
        try {
            subSocket.subscribe(canal.getBytes());
            canaisInscritos.add(canal);
            System.out.println("[" + username + "] Inscrito no canal: " + canal);
        } catch (Exception e) {
            System.err.println("[" + username + "] Erro ao se inscrever: " + e.getMessage());
        }
    }
    
    private static void publicarMensagem(String canal, String conteudo) {
        try {
            Message pubMsg = new Message("publish");
            pubMsg.setUsername(username);
            pubMsg.setChannelName(canal);
            pubMsg.setContent(conteudo);
            
            byte[] msgBytes = MessagePackUtil.serialize(pubMsg);
            reqSocket.send(msgBytes, 0);
            
            byte[] responseBytes = reqSocket.recv(0);
            Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
            
            if (response.isSuccess()) {
                System.out.println("[" + username + "] PUBLICADO em [" + canal + "]: " + conteudo);
            } else {
                System.err.println("[" + username + "] Erro ao publicar: " + response.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[" + username + "] Erro na publicação: " + e.getMessage());
        }
    }
    
    private static void receberMensagens() {
        System.out.println("[" + username + "] Thread de recebimento iniciada");
        while (true) {
            try {
                // Receber tópico (canal)
                String canal = subSocket.recvStr(0);
                
                // Receber mensagem
                byte[] msgBytes = subSocket.recv(0);
                Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                
                long receivedTimestamp = System.currentTimeMillis();
                
                System.out.println("\n[" + username + "] <<<< MENSAGEM RECEBIDA >>>>");
                System.out.println("  Canal: " + canal);
                System.out.println("  De: " + msg.getUsername());
                System.out.println("  Conteúdo: " + msg.getContent());
                System.out.println("  Timestamp envio: " + msg.getTimestamp());
                System.out.println("  Timestamp recebimento: " + receivedTimestamp);
                System.out.println("  Latência: " + (receivedTimestamp - msg.getTimestamp()) + "ms\n");
                
            } catch (Exception e) {
                System.err.println("[" + username + "] Erro ao receber: " + e.getMessage());
            }
        }
    }
    
    private static String gerarMensagemAleatoria(int numero) {
        String[] frases = {
            "Olá!",
            "Bem?",
            "Teste de mensagem",
            "Sistema funcionando",
            "Publicação automática",
            "Bot aqui",
            "Mensagem aleatória =D",
            "Teste de distribuição",
            "Hello World!",
            "Sistemas Distribuídos"
        };
        
        return frases[random.nextInt(frases.length)] + " #" + numero;
    }
}