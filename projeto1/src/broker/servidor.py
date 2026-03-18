import json
import os
import zmq
import zoneinfo
from datetime import datetime
import msgpack
from dataclasses import dataclass, asdict
import pickle

@dataclass
class Message:
    type: str
    username: str
    timestamp: int
    channel_name: str = ""
    @staticmethod
    def unpack(data: bytes) -> "Message":
        d = msgpack.unpackb(data)
        return Message(**d)

@dataclass
class Response:
    success: bool
    message: str
    timestamp: int = 0
    channel_name: str=""
    def __post_init__(self):
        if self.timestamp == 0:
            self.timestamp = int(datetime.now().timestamp() * 1000)
    
    def pack(self) -> bytes:
        return msgpack.packb(asdict(self))

# ============ PERSISTÊNCIA ============
DADOS_FILE = "/app/data/server_data.pkl"

def salvar_dados():
    os.makedirs("/app/data", exist_ok=True)
    with open(DADOS_FILE, "wb") as f:  # wb = write binary
        pickle.dump({
            "usuarios": usuarios,
            "tarefas": tarefas,
            "canais": canais,
            "logins": logins
        }, f)
    print("Dados salvos.", flush=True)

def carregar_dados():
    global usuarios, tarefas, canais, logins
    if os.path.exists(DADOS_FILE):
        with open(DADOS_FILE, "rb") as f:  # rb = read binary
            dados = pickle.load(f)
        usuarios = dados.get("usuarios", [])
        tarefas  = dados.get("tarefas", [])
        canais   = dados.get("canais", [])
        logins   = dados.get("logins", [])
        print(f"Dados carregados: {len(usuarios)} usuários, {len(canais)} canais", flush=True)

# ============ SETUP ============
context = zmq.Context()
socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")


usuarios = []
logins = []
canais = []
logado = False
tempo = ""

carregar_dados()  # carrega ao iniciar

# ============ LOOP PRINCIPAL ============
while True:
    msg = Message.unpack(socket.recv())
    fazer = msg.type
    resposta = ""

    if fazer == "login":
        if msg.username in usuarios:
            tempo = msg.timestamp
            horario = datetime.now(tz=fuso).timestamp()*1000
            resposta = Response(success=True, message=f"Login realizado às {horario}")
            login = (msg.username, horario)
            logins.append(login)
            logado = True
            salvar_dados()  # persiste após login
            print(resposta.message, flush=True)
        else:
            usuarios.append(msg.username)
            logado = True
            resposta = Response(success=True, message="Cadastro inexistente. Adicionando...")
            salvar_dados()  # persiste novo usuário
            print(resposta.message, flush=True)

    elif logado == True:
        if fazer == "criar":
            tarefas.append(msg.username + "|" + str(tempo))
            resposta = Response(success=True, message="Tarefa criada")
            salvar_dados()  # persiste nova tarefa
            print(resposta.message, flush=True)

        elif fazer == "remover":
            if msg.username in tarefas:
                tarefas.remove(msg.username)
                resposta = Response(success=True, message="Tarefa removida")
                salvar_dados()  # persiste remoção
            else:
                resposta = Response(success=False, message=f"Tarefa não encontrada :(")
            print(resposta.message, flush=True)

        elif fazer == "list_channels":
            
            resposta = Response(success=True, message=f"{canais}")
        
        elif fazer == "create_channel":
            canais.append(msg.channel_name)
            resposta = Response(success=True, message="Canal Criado!!! Yipeeeeee")
            salvar_dados()


        else:
            resposta = Response(success=False, message="Comando inválido")
            print(resposta.message, flush=True)
    else:
        resposta = Response(success=False, message="Usuário não logado!")
        print(resposta.message, flush=True)

    print(f"Mensagem recebida: {msg} às {datetime.now(tz=fuso).timestamp() * 1000}", flush=True)
    socket.send(resposta.pack())