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
                    
                    System.out.println("\nENVIANDO REQUISIÇÃO DE LOGIN!!");
                    System.out.println("Tipo: " + loginMsg.getType());
                    System.out.println("Username: " + loginMsg.getUsername());
                    System.out.println("Timestamp: " + loginMsg.getTimestamp());
                    System.out.println("Bytes enviados: " + msgBytes.length);
                    
                    // Enviar pelo ZeroMQ
                    socket.send(msgBytes, 0);
                    
                    // Aguardar resposta
                    byte[] responseBytes = socket.recv(0);
                    Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
                  
                    System.out.println("\nRESPOSTA DO SERVIDOR RECEBIDA!!");
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
    
            System.out.println("CRIANDO CANAIS");

            int canaisCriados = 0;
            int canaisComErro = 0;
            for (int i = 1; i <= 10; i++) {
                String nomeCanal = "canal-java-bot-" + i; 
                
                try {
                    // mensagem de criação de canal
                    Message createChannelMsg = new Message("create_channel");
                    createChannelMsg.setUsername(username);
                    createChannelMsg.setChannelName(nomeCanal);
                    
                    byte[] msgBytes = MessagePackUtil.serialize(createChannelMsg);
                    
                    System.out.println("Nome: " + createChannelMsg.getChannelName());
                    System.out.println("Criador: " + createChannelMsg.getUsername());
                    System.out.println("Tipo: " + createChannelMsg.getType());
                    System.out.println("Timestamp: " + createChannelMsg.getTimestamp());
                    System.out.println("Bytes enviados: " + msgBytes.length);
                    
                    socket.send(msgBytes, 0);
                    
                    byte[] responseBytes = socket.recv(0);
                    Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
                    
                    if (response.isSuccess()) {
                        canaisCriados++;
                        System.out.println("SUCESSO: " + response.getMessage());
                        System.out.println("Canal: " + response.getChannelName() + "\n");
                    } else {
                        canaisComErro++;
                        System.out.println("ERRO: " + response.getMessage() + "\n");
                    }
                
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    canaisComErro++;
                    System.err.println("Erro ao criar canal " + i + ": " + e.getMessage() + "\n");
                }
            }
           
            System.out.println("LISTANDO TODOS OS CANAIS\n");
            
            try {
                // mensagem de listagem
                Message listChannelsMsg = new Message("list_channels");
                listChannelsMsg.setUsername(username);
                
                byte[] msgBytes = MessagePackUtil.serialize(listChannelsMsg);
                
                System.out.println("ENVIANDO REQUISIÇÃO DE LISTAGEM");
                System.out.println("Tipo: " + listChannelsMsg.getType());
                System.out.println("Username: " + listChannelsMsg.getUsername());
                
                // Enviar requisição
                socket.send(msgBytes, 0);
                
                // Receber resposta
                byte[] responseBytes = socket.recv(0);
                Response response = MessagePackUtil.deserialize(responseBytes, Response.class);
                
                System.out.println("\nRESPOSTA RECEBIDA");
                System.out.println("Success: " + response.isSuccess());
                System.out.println("Message: " + response.getMessage());
                
                // Exibir lista de canais
                if (response.isSuccess() && response.getChannels() != null) {
                    System.out.println("\nCANAIS DISPONÍVEIS");
                    
                    int index = 1;
                    for (String canal : response.getChannels()) {
                        System.out.println(index + ". " + canal);
                        index++;
                    }
                    
                    System.out.println("\nTotal: " + response.getChannels().size() + " canais");
                } else if (!response.isSuccess()) {
                    System.out.println("ERRO: " + response.getMessage());
                }
                
            } catch (Exception e) {
                System.err.println("Erro ao listar canais: " + e.getMessage());
            }
            
            System.out.println("\nCliente Finalizado :D");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}