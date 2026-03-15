
import json
import zmq
import zoneinfo
from datetime import datetime
import msgpack
context = zmq.Context()
socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")
tarefas = list() #lista de tarefas para o servidor processar, add remove pela lista
usuarios = list()
logins = list()
logado = False
while True:
    message = socket.recv() #recebe mensagem do cliente (no REP sempre começa com recv)
    msg = msgpack.unpackb(message) #converte a string recebida em um dicionário
    fazer = msg["fazer"] #pega o valor da chave "fazer" do dicionário
    #tempo = msg["timestamp"]
    resposta = ""
    # No padrão REQ/REP do ZeroMQ é OBRIGATÓRIO:
    # REP -> recv -> send -> recv -> send ...
    # Ou seja, SEMPRE depois de um recv precisamos dar um send.
    # Se não enviar resposta, o socket entra em estado inválido e dá erro.
    if fazer == "logar":
        if msg["msg"] in usuarios:
            tempo = msg["timestamp"]
            horario = datetime.now(tz=fuso).isoformat()
            resposta = f"Login realizado com sucesso às {horario}"
            login = (msg["msg"], horario)
            logins.append(login)
            #socket.send_string(resposta)
            logado = True
            print(resposta, flush=True)
            
        else:
            usuarios.append(msg["msg"])
            resposta = "Cadastro inexistente. Adicionado a lista de usuários..."
            #socket.send_string(resposta)
            print(resposta, flush=True)
            
    elif logado == True:
        if fazer == "criar":
            tarefas.append(msg["msg"] + "|" + tempo) #adiciona a mensagem do cliente na lista de tarefas
            resposta = f"Tarefa '{msg['msg']}' criada."
           # socket.send_string(resposta) #envia resposta obrigatória
            print(resposta, flush=True)

        elif fazer == "remover":
            if msg["msg"] in tarefas: #verifica se a mensagem do cliente está na lista de tarefas
                tarefas.remove(msg["msg"]) #remove da lista
                resposta = f"Tarefa '{msg['msg']}' removida às {datetime.now(tz=fuso).isoformat()}."
            else:
                # Mesmo se não encontrar a tarefa, PRECISA responder
                resposta = f"Tarefa '{msg['msg']}' não encontrada às {datetime.now(tz=fuso).isoformat()} ."

           # socket.send_string(resposta) #sempre enviar resposta
            print(resposta, flush=True)

        elif fazer == "listar":
            print(f"Lista de tarefas: {tarefas}", flush=True)
            resposta = msgpack.packb(tarefas) #transforma lista em string json
            #socket.send_string(resposta) #sempre enviar resposta

        else:
            # Caso inesperado também precisa responder
            resposta = "Comando inválido."
            #socket.send_string(resposta)
    else:
        resposta = "Usuário não logado. Faça login primeiro!"
        # socket.send_string(resposta)
        print(resposta, flush = True)
    print(f"Mensagem recebida: {msg} às {datetime.now(tz=fuso)}", flush=True)
    resp = msgpack.packb(resposta)
    socket.send(resp)