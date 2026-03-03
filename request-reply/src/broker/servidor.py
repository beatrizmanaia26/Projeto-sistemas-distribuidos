import json
import zmq

context = zmq.Context()
socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")

tarefas = list() #lista de tarefas para o servidor processar, add remove pela lista

while True:
    message = socket.recv_string() #recebe mensagem do cliente (no REP sempre começa com recv)
    msg = json.loads(message) #converte a string recebida em um dicionário
    fazer = msg["fazer"] #pega o valor da chave "fazer" do dicionário

    # No padrão REQ/REP do ZeroMQ é OBRIGATÓRIO:
    # REP -> recv -> send -> recv -> send ...
    # Ou seja, SEMPRE depois de um recv precisamos dar um send.
    # Se não enviar resposta, o socket entra em estado inválido e dá erro.

    if fazer == "criar":
        tarefas.append(msg["msg"]) #adiciona a mensagem do cliente na lista de tarefas
        resposta = f"Tarefa '{msg['msg']}' criada."
        socket.send_string(resposta) #envia resposta obrigatória
        print(resposta, flush=True)

    elif fazer == "remover":
        if msg["msg"] in tarefas: #verifica se a mensagem do cliente está na lista de tarefas
            tarefas.remove(msg["msg"]) #remove da lista
            resposta = f"Tarefa '{msg['msg']}' removida."
        else:
            # Mesmo se não encontrar a tarefa, PRECISA responder
            resposta = f"Tarefa '{msg['msg']}' não encontrada."

        socket.send_string(resposta) #sempre enviar resposta
        print(resposta, flush=True)

    elif fazer == "listar":
        print(f"Lista de tarefas: {tarefas}", flush=True)
        resposta = json.dumps(tarefas) #transforma lista em string json
        socket.send_string(resposta) #sempre enviar resposta

    else:
        # Caso inesperado também precisa responder
        resposta = "Comando inválido."
        socket.send_string(resposta)

    print(f"Mensagem recebida: {message}", flush=True)