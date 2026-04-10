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
    content: str = ""
    received_timestamp: int = 0
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
    channels: list = None
    publication_status: str = ""
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
            "canais": canais,
            "logins": logins,
            "publicacoes": publicacoes
        }, f)
    print("Dados salvos.", flush=True)

def carregar_dados():
    global usuarios, tarefas, canais, logins, publicacoes
    if os.path.exists(DADOS_FILE):
        try:
            if os.path.getsize(DADOS_FILE) > 0: # Verifica se o arquivo está vazio
                with open(DADOS_FILE, "rb") as f:
                    dados = pickle.load(f)
                usuarios = dados.get("usuarios", [])
                canais   = dados.get("canais", set())
                logins   = dados.get("logins", [])
                publicacoes = dados.get("publicacoes", [])
                print(f"Dados carregados: {len(usuarios)} usuários, {len(canais)} canais, {len(publicacoes)} publicacoes", flush=True)
            else:
                print("Arquivo de dados vazio. Iniciando do zero.", flush=True)
        except (EOFError, pickle.UnpicklingError):
            print("Erro ao ler arquivo de dados. Iniciando do zero.", flush=True)

# ============ SETUP ============
context = zmq.Context()
socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")


usuarios = []
logins = []
canais = []
logado = False
publicacoes = []
tempo = ""
pub_socket = context.socket(zmq.PUB)
# Conecta na porta de ENTRADA do Proxy (geralmente o XSUB)
pub_socket.connect("tcp://proxy:5557")

carregar_dados()  # carrega ao iniciar

# ============ LOOP PRINCIPAL ============
while True:
    msg = Message.unpack(socket.recv())
    fazer = msg.type
    resposta = ""
    print(msg.channel_name)
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
            usuarios.add(msg.username)
            logado = True
            resposta = Response(success=True, message="Cadastro inexistente. Adicionando...")
            salvar_dados()  # persiste novo usuário
            print(resposta.message, flush=True)

    elif logado == True:


        if fazer == "list_channels":
            
            resposta = Response(success=True, message=f"{canais}", channels=list(canais))
            print(list(canais))
            salvar_dados()
        
        elif fazer == "create_channel":
            if msg.channel_name in canais:
                resposta = Response(success=False, message="Canal já existe")
            else:
                canais.append(msg.channel_name)
                resposta = Response(success=True, message="Canal Criado!!! Yipeeeeee")
            salvar_dados()
        elif fazer == "publish":
            if msg.channel_name in canais:
            
                # 2. Monta a mensagem com o timestamp exigido no enunciado da Parte 2
                agora = int(datetime.now().timestamp() * 1000)
                conteudo_formatado = f"{msg.content} | Env: {msg.timestamp} | RecServ: {agora}"

                # 3. Publica no Proxy usando PUB
                pub_socket.send_multipart([
                    msg.channel_name.encode('utf-8'), 
                    conteudo_formatado.encode('utf-8')
                ])
                
                publicacoes.append(f"[{msg.channel_name}] {msg.username}: {msg.content}")
                salvar_dados()
                

                resposta = Response(success=True, message=f"Mensagem enviada ao canal {msg.channel_name}!")
                print(f"Publicado em {msg.channel_name}: {msg.content}", flush=True)
            else:
                resposta = Response(success=False, message="Canal não encontrado")
               
            salvar_dados()

        else:
            resposta = Response(success=False, message="Comando inválido")
            print(resposta.message, flush=True)
    else:
        resposta = Response(success=False, message="Usuário não logado!")
        print(resposta.message, flush=True)

    print(f"Mensagem recebida: {msg} às {datetime.now(tz=fuso).timestamp() * 1000}", flush=True)
    
    socket.send(resposta.pack())