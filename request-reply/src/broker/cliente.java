import org.zeromq.ZMQ;
import org.zeromq.ZContext;

class cliente { 
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            //ZeroMQ
            ZMQ.Socket socket = context.createSocket(ZMQ.REQ);
            socket.connect("tcp://broker:5555");
            
            System.out.println(" Cliente Java Iniciado");
            
            // Bot automático
            boolean loggedIn = false;
            String username = "bot_java_1";
            
            // Retry em caso de erro
            while (!loggedIn) {
                try {
                    Message loginMsg = new Message("login");
                    loginMsg.setUsername(username);
                    
                    byte[] msgBytes = MessagePackUtil.serialize(loginMsg);
                    
                    System.out.println("\nENVIANDO LOGIN!!");
                    System.out.println("Tipo: " + loginMsg.getType());
                    System.out.println("Username: " + loginMsg.getUsername());
                    System.out.println("Timestamp: " + loginMsg.getTimestamp());
                    System.out.println("Bytes enviados: " + msgBytes.length);
                    
                    // Enviar pelo ZeroMQ
                    socket.send(msgBytes, 0);
                    
                    // Aguardar UMA resposta
                    byte[] responseBytes = socket.recv(0);
                    Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
                  
                    System.out.println("\nRESPOSTA RECEBIDA!!");
                    System.out.println("Success: " + response.isSuccess());
                    System.out.println("Message: " + response.getMessage());
                    System.out.println("Timestamp: " + response.getTimestamp());
                    
                    if (response.isSuccess()) {
                        loggedIn = true;
                        System.out.println("\nLOGIN BEM-SUCEDIDO UHULLL\n");
                    } else {
                        System.out.println("\n ERRO NO LOGIN - Tentando novamente em 2s\n");
                        Thread.sleep(2000);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Erro: " + e.getMessage());
                    Thread.sleep(2000);
                }
            }
            
            System.out.println("Bot Logado com Sucesso");
    
            System.out.println("CRIANDO CANAL");
            
            String nomeCanal = "canal-java-bot"; 
            try {
                // mensagem de criação de canal
                Message createChannelMsg = new Message("create_channel");
                createChannelMsg.setUsername(username);  // Enviar username para validação
                createChannelMsg.setChannelName(nomeCanal);
                
                byte[] msgBytes = MessagePackUtil.serialize(createChannelMsg);
                
                System.out.println("\nENVIAR CRIAÇÃO DE CANAL");
                System.out.println("Tipo: " + createChannelMsg.getType());
                System.out.println("Username: " + createChannelMsg.getUsername());
                System.out.println("Nome do Canal: " + createChannelMsg.getChannelName());
                System.out.println("Timestamp: " + createChannelMsg.getTimestamp());
                System.out.println("Bytes enviados: " + msgBytes.length);
                
                socket.send(msgBytes, 0);
                
                byte[] responseBytes = socket.recv(0);
                Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
                
                System.out.println("\nRESPOSTA RECEBIDA");
                System.out.println("Success: " + response.isSuccess());
                System.out.println("Message: " + response.getMessage());
                System.out.println("Channel Name: " + response.getChannelName());
                System.out.println("Timestamp: " + response.getTimestamp());
                
                if (response.isSuccess()) {
                    System.out.println("\nCANAL CRIADO COM SUCESSO: " + response.getChannelName());
                } else {
                    System.out.println("\nERRO AO CRIAR CANAL: " + response.getMessage());
                }
                
            } catch (Exception e) {
                System.err.println("Erro ao criar canal: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("\nCliente Finalizado :D");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}