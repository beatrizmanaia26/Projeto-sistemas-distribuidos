import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.io.*;
import java.util.*;

class servidor {
    // Dados em memória
    private static Set<String> usuariosLogados = new HashSet<>();
    private static Map<String, Long> timestampsLogin = new HashMap<>();
    private static Set<String> canais = new HashSet<>();  
    
    // Arquivos próprios do servidor (salvos em volume montado) - persistencia
    private static final String USERS_FILE = "/app/data/users_data.ser";
    private static final String CHANNELS_FILE = "/app/data/channels_data.ser";  
    
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            // ZeroMQ
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.connect("tcp://broker:5556");
            
            System.out.println("Servidor Java Iniciado");
            
        
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
    

    // Salvar dados em disco
    private static void salvarDados() {
        try {
            // Salvar usuários
            ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(USERS_FILE)
            );
            oos.writeObject(timestampsLogin);
            oos.close();
            
            // Salvar canais
            oos = new ObjectOutputStream(
                new FileOutputStream(CHANNELS_FILE)
            );
            oos.writeObject(canais);
            oos.close();
            
            System.out.println("Dados persistidos em disco (usuários + canais)");
        } catch (Exception e) {
            System.err.println("Erro ao salvar: " + e.getMessage());
        }
    }
    
    // Carregar dados do disco
    @SuppressWarnings("unchecked")
    private static void carregarDados() {
        try {
        
            File file = new File(USERS_FILE);
            if (file.exists()) {
                ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(file)
                );
                timestampsLogin = (Map<String, Long>) ois.readObject();
                usuariosLogados.addAll(timestampsLogin.keySet());
                ois.close();
                System.out.println("Usuários carregados: " + usuariosLogados.size());
            }
            
            // Carregar canais
            File channelsFile = new File(CHANNELS_FILE);
            if (channelsFile.exists()) {
                ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(channelsFile)
                );
                canais = (Set<String>) ois.readObject();
                ois.close();
                System.out.println(" Canais carregados: " + canais.size());
            }
        } catch (Exception e) {
            System.err.println(" Erro ao carregar: " + e.getMessage());
        }
    }
}