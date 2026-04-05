import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.io.*;
import java.util.*;

class servidor {
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
    
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            // ZeroMQ REP socket para requisições
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.connect("tcp://broker:5556");
            
            // ZeroMQ PUB socket para publicações no proxy
            publisherSocket = context.createSocket(ZMQ.PUB);
            publisherSocket.connect("tcp://proxy:5557");
            
            System.out.println("Servidor Java Iniciado");
            System.out.println("Conectado ao broker (REQ-REP) e ao proxy (PUB-SUB)");
            
            // Aguardar um pouco para o proxy estar pronto
            Thread.sleep(1000);
        
            carregarDados();
            
            while (true) {
                try {
                    // Receber por ZeroMQ
                    byte[] msgBytes = socket.recv(0);
                    
                  
                    Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                    
                    
                    System.out.println("\n MENSAGEM RECEBIDA UHLL");
                    System.out.println("Tipo: " + msg.getType());
                    System.out.println("Username: " + msg.getUsername());
                    System.out.println("Channel Name: " + msg.getChannelName());
                    System.out.println("Timestamp: " + msg.getTimestamp());
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
                    
                
                    System.out.println("\nENVIANDO RESPOSTA !!!!");
                    System.out.println("Success: " + response.isSuccess());
                    System.out.println("Message: " + response.getMessage());
                    System.out.println("Timestamp: " + response.getTimestamp());
                    
                  
                    byte[] responseBytes = MessagePackUtil.serialize(response);
                    
            
                    socket.send(responseBytes, 0);
                    
                } catch (Exception e) {
                    System.err.println("Erro: " + e.getMessage());
                    Response errorResponse = new Response(false, "Erro no servidor");
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
}