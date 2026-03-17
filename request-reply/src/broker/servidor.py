
import json
import zmq
import zoneinfo
from datetime import datetime
import msgpack
from dataclasses import dataclass,asdict
@dataclass
class Message:
    type: str
    username: str
    timestamp: int
    
    @staticmethod
    def unpack(data: bytes) -> "Message":
        d = msgpack.unpackb(data)
        return Message(**d)

# Response — para ENVIAR de volta ao cliente
@dataclass
class Response:
    success: bool
    message: str
    timestamp: int = 0
    
    def __post_init__(self):
        if self.timestamp == 0:
            self.timestamp = int(datetime.now().timestamp() * 1000)
    
    def pack(self) -> bytes:
        return msgpack.packb(asdict(self))
context = zmq.Context()
socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")
tarefas = list() #lista de tarefas para o servidor processar, add remove pela lista
usuarios = list()
logins = list()
logado = False
while True:
    #message = socket.recv() #recebe mensagem do cliente (no REP sempre começa com recv)
    msg = Message.unpack(socket.recv()) #converte a string recebida em um dicionário
    fazer = msg.type; #pega o valor da chave "fazer" do dicionário
    #tempo = msg["timestamp"]
    resposta = ""
    # No padrão REQ/REP do ZeroMQ é OBRIGATÓRIO:
    # REP -> recv -> send -> recv -> send ...
    # Ou seja, SEMPRE depois de um recv precisamos dar um send.
    # Se não enviar resposta, o socket entra em estado inválido e dá erro.
    if fazer == "login":
        if msg.username in usuarios:
            tempo = msg.timestamp
            horario = datetime.now(tz=fuso).isoformat()
            resposta = f"Login realizado com sucesso às {horario}"
            resposta = Response(success = True, message = f"Login realizado às {horario}")
            login = (msg.username, horario)
            logins.append(login)
            
            logado = True
            print(resposta.message, flush=True)
            
        else:
            usuarios.append(msg.username)
            logado = True
            resposta = "Cadastro inexistente. Adicionado a lista de usuários..."
            resposta = Response(success = True, message = f"Cadastro inexistente. Adicionando...")
            
            print(resposta.message, flush=True)
            
    elif logado == True:
        if fazer == "criar":
            tarefas.append(msg.username + "|" + tempo) #adiciona a mensagem do cliente na lista de tarefas
            resposta = f"Tarefa '{msg.username}' criada."
            resposta = Response(success = True, message = f"Tarefa criada")
            print(resposta.message, flush=True)

        elif fazer == "remover":
            if msg.username in tarefas: #verifica se a mensagem do cliente está na lista de tarefas
                tarefas.remove(msg.username) #remove da lista
                resposta = f"Tarefa '{msg.username}' removida às {datetime.now(tz=fuso).isoformat()}."
                resposta = Response(success = True, message = f"Tarefa removida")
            else:
                # Mesmo se não encontrar a tarefa, PRECISA responder
                resposta = f"Tarefa '{msg['username']}' não encontrada às {datetime.now(tz=fuso).isoformat()} ."
                resposta = Response(success = True, message = f"Tarefa não encontrada :(")
           
            print(resposta.message, flush=True)

        elif fazer == "listar":
            print(f"Lista de tarefas: {tarefas}", flush=True)
            resposta = msgpack.packb(tarefas) #transforma lista em string json
            resposta = Response(success = True, message = f"{tarefas}")
             #sempre enviar resposta

        else:
            # Caso inesperado também precisa responder
            resposta = "Comando inválido."
            resposta = Response(success = False, message = f"Comando inválido")
            print(resposta.message, flush=True)
    else:
        resposta = "Usuário não logado. Faça login primeiro!"
        resposta = Response(success = False, message = f"Usuário não logado!")
        
        print(resposta.message, flush=True)
    print(f"Mensagem recebida: {msg} às {datetime.now(tz=fuso).timestamp() * 1000}", flush=True)
    #resp = msgpack.packb(resposta)
    socket.send(resposta.pack())