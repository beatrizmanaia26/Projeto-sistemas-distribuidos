import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import java.io.*;
import java.util.*;

class servidor {//Existe pois precisa saber se login deu certo ou n 
    // Dados em memória
    private static Set<String> usuariosLogados = new HashSet<>();
    private static Map<String, Long> timestampsLogin = new HashMap<>();
    
    // Arquivo próprio do servidor (salvo em volume montado)
    private static final String USERS_FILE = "/app/data/users_data.ser";
    
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.connect("tcp://broker:5556");
            
            System.out.println("servidor Java Iniciado");
        
            carregarDados();
            
            while (true) {
                try {
                    // Receber por ZeroMQ
                    byte[] msgBytes = socket.recv(0);
                    
                    Message msg = MessagePackUtil.deserialize(msgBytes, Message.class);
                    
                    System.out.println("\n MENSAGEM RECEBIDA UHLLL");
                    System.out.println("Tipo: " + msg.getType());
                    System.out.println("Username: " + msg.getUsername());
                    System.out.println("Timestamp: " + msg.getTimestamp());
                    System.out.println("Bytes recebidos: " + msgBytes.length);
                    
                    Response response;
                    
                    if ("login".equals(msg.getType())) {
                        response = processarLogin(msg);
                    } else {
                        response = new Response(false, "Tipo desconhecido");
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
        
        // Persistir em disco
        salvarDados();
        
        System.out.println("Login registrado: " + username);
        return new Response(true, "Login realizado com sucesso para " + username);
    }
    
    // Salvar dados em disco
    private static void salvarDados() {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(USERS_FILE)
            );
            oos.writeObject(timestampsLogin);
            oos.close();
            System.out.println("Dados persistidos em disco: " + USERS_FILE);
        } catch (Exception e) {
            System.err.println(" Erro ao salvar: " + e.getMessage());
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
                System.out.println("Dados carregados: " + usuariosLogados.size() + " usuários");
            }
        } catch (Exception e) {
            System.err.println(" Erro ao carregar: " + e.getMessage());
        }
    }
}
