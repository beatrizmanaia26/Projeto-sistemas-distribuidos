import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.json.JSONObject;

class cliente {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(ZMQ.REQ);
            socket.connect("tcp://broker:5555");
            
            System.out.println("Cliente Java conectado ao broker");
            
            int i = 0;
            while (true) {
                // Mensagem 1: criar
                JSONObject msg1 = new JSONObject();
                msg1.put("fazer", "criar");
                msg1.put("msg", "eba");
                
                System.out.print("Mensagem " + i + ": ");
                System.out.flush();
                socket.send(msg1.toString().getBytes(ZMQ.CHARSET), 0);
                String resposta1 = new String(socket.recv(0), ZMQ.CHARSET);
                System.out.println(resposta1);
                
                Thread.sleep(500);
                
                // Mensagem 2: remover
                JSONObject msg2 = new JSONObject();
                msg2.put("fazer", "remover");
                msg2.put("msg", "eba2");
                
                System.out.print("Mensagem " + i + ": ");
                System.out.flush();
                socket.send(msg2.toString().getBytes(ZMQ.CHARSET), 0);
                String resposta2 = new String(socket.recv(0), ZMQ.CHARSET);
                System.out.println(resposta2);
                
                Thread.sleep(500);
                
                // Mensagem 3: criar
                JSONObject msg3 = new JSONObject();
                msg3.put("fazer", "criar");
                msg3.put("msg", "eba3");
                
                System.out.print("Mensagem " + i + ": ");
                System.out.flush();
                socket.send(msg3.toString().getBytes(ZMQ.CHARSET), 0);
                String resposta3 = new String(socket.recv(0), ZMQ.CHARSET);
                System.out.println(resposta3);
                
                Thread.sleep(500);
                
                // Mensagem 4: listar
                JSONObject msg4 = new JSONObject();
                msg4.put("fazer", "listar");
                msg4.put("msg", "eba4");
                
                System.out.print("Mensagem " + i + ": ");
                System.out.flush();
                socket.send(msg4.toString().getBytes(ZMQ.CHARSET), 0);
                String resposta4 = new String(socket.recv(0), ZMQ.CHARSET);
                System.out.println(resposta4);
                
                Thread.sleep(500);
                i++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
