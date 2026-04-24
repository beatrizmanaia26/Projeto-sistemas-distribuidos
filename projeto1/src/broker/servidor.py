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
    logical_clock: int = 0
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
    logical_clock: int = 0
    rank: int = 0
    current_time: int = 0
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
#==============COORDENADOR============================================
def clock_before_send() -> int:
    #Incrementa o relógio antes de enviar e retorna o novo valor.
    global logical_clock
    logical_clock += 1
    print(f"[RELÓGIO] Incrementado antes de enviar: {logical_clock}", flush=True)
    return logical_clock
 
def clock_on_receive(received: int) -> None:
    #Atualiza o relógio ao receber uma mensagem.
    global logical_clock
    old = logical_clock
    if received > logical_clock:
        logical_clock = received + 1
        print(f"[RELÓGIO] ATUALIZADO! Recebido={received} > local={old}. Novo={logical_clock}", flush=True)
    else:
        logical_clock += 1
        print(f"[RELÓGIO] MANTIDO! Recebido={received} <= local={old}. Novo={logical_clock}", flush=True)

def _coord_send_recv(msg_dict: dict) -> dict:
    """Envia uma mensagem ao coordenador e retorna a resposta desserializada."""
    data = msgpack.packb(msg_dict)
    coord_socket.send(data)
    raw = coord_socket.recv()
    return msgpack.unpackb(raw, raw=False)

def request_rank():
    #Pede o rank ao coordenador na inicialização (enunciado linha 14).
    global logical_clock
    global server_rank
    global server_name
    lc = clock_before_send()
    msg = {
        "type": "get_rank",
        "username": server_name,
        "timestamp": int(datetime.now().timestamp() * 1000),
        "logical_clock": lc,
    }
    print(f"[COORDENADOR] Pedindo rank como '{server_name}'...", flush=True)
    resp = _coord_send_recv(msg)
    clock_on_receive(resp.get("logical_clock", 0))
 
    if resp.get("success"):
        server_rank = resp.get("rank", -1)
        print(f"[COORDENADOR] Rank recebido: {server_rank}", flush=True)
    else:
        print(f"[COORDENADOR] Erro ao pedir rank: {resp.get('message')}", flush=True)
 
 
def send_heartbeat():
    #Envia heartbeat ao coordenador e sincroniza o relógio físico.
    global server_name
    lc = clock_before_send()
    msg = {
        "type": "heartbeat",
        "username": server_name,
        "timestamp": int(datetime.now().timestamp() * 1000),
        "logical_clock": lc,
    }
    print(f"\n[HEARTBEAT] Enviando heartbeat...", flush=True)
    resp = _coord_send_recv(msg)
    clock_on_receive(resp.get("logical_clock", 0))
 
    if resp.get("success"):
        coord_time = resp.get("current_time", 0)
        local_time = int(datetime.now().timestamp() * 1000)
        print(f"[HEARTBEAT] OK! Hora do coordenador: {coord_time}", flush=True)
        print(f"[HEARTBEAT] Hora local:               {local_time}", flush=True)
        print(f"[HEARTBEAT] Diferença:                {local_time - coord_time} ms\n", flush=True)
    else:
        print(f"[HEARTBEAT] Erro: {resp.get('message')}", flush=True)
# ============ SETUP ============
context = zmq.Context()
socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")
coord_socket = context.socket(zmq.REQ)
coord_socket.connect("tcp://coordinatorPython:5560")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")
logical_clock = 0
server_rank = 0
server_name = "servidor-python"
contadorMensagens = 0
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
request_rank() #pedindo rank para o coordenador
# ============ LOOP PRINCIPAL ============
while True:
    msg = Message.unpack(socket.recv())
    contadorMensagens+=1
    fazer = msg.type
    resposta = ""
    print(msg.channel_name)
    if contadorMensagens == 10:
        send_heartbeat()
        contadorMensagens = 0
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