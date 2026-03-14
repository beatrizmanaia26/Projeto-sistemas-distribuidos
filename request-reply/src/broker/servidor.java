import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

class servidor {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.connect("tcp://broker:5556");
            
            System.out.println("Servidor Java conectado ao broker");
            
            List<String> tarefas = new ArrayList<>();
            
            while (true) {
                // Recebe mensagem do cliente
                String message = new String(socket.recv(0), ZMQ.CHARSET);
                JSONObject msg = new JSONObject(message);
                String fazer = msg.getString("fazer");
                
                String resposta;
                
                // No padrão REQ/REP do ZeroMQ é OBRIGATÓRIO:
                // REP -> recv -> send -> recv -> send ...
                // Ou seja, SEMPRE depois de um recv precisamos dar um send.
                
                if (fazer.equals("criar")) {
                    String tarefa = msg.getString("msg");
                    tarefas.add(tarefa);
                    resposta = "Tarefa '" + tarefa + "' criada.";
                    socket.send(resposta.getBytes(ZMQ.CHARSET), 0);
                    System.out.println(resposta);
                    
                } else if (fazer.equals("remover")) {
                    String tarefa = msg.getString("msg");
                    if (tarefas.contains(tarefa)) {
                        tarefas.remove(tarefa);
                        resposta = "Tarefa '" + tarefa + "' removida.";
                    } else {
                        resposta = "Tarefa '" + tarefa + "' não encontrada.";
                    }
                    socket.send(resposta.getBytes(ZMQ.CHARSET), 0);
                    System.out.println(resposta);
                    
                } else if (fazer.equals("listar")) {
                    System.out.println("Lista de tarefas: " + tarefas);
                    JSONArray jsonArray = new JSONArray(tarefas);
                    resposta = jsonArray.toString();
                    socket.send(resposta.getBytes(ZMQ.CHARSET), 0);
                    
                } else {
                    resposta = "Comando inválido.";
                    socket.send(resposta.getBytes(ZMQ.CHARSET), 0);
                }
                
                System.out.println("Mensagem recebida: " + message);
                System.out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
