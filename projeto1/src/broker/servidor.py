import os
import sys
import time
import threading
import zmq
import zoneinfo
from datetime import datetime
import msgpack
from dataclasses import dataclass, asdict
import pickle

# ============ DATACLASSES ============
@dataclass
class Message:
    type: str
    username: str
    timestamp: int
    channel_name: str = ""
    content: str = ""
    received_timestamp: int = 0
    logical_clock: int = 0
    election_id: int = 0
    coordinator_name: str = ""
    clock_offset: int = 0
    success: bool = False
    rank: int = 0
    #server_rank: int = 0
    @staticmethod
    def unpack(data: bytes) -> "Message":
        d = msgpack.unpackb(data, raw=False)
        known = Message.__dataclass_fields__.keys()
        return Message(**{k: v for k, v in d.items() if k in known})

@dataclass
class Response:
    success: bool
    message: str
    timestamp: int = 0
    channel_name: str = ""
    channels: list = None
    publication_status: str = ""
    logical_clock: int = 0
    #server_rank: int = 0
    rank: int = 0
    current_time: int = 0
    server_list: list = None
    coordinator_name: str = ""
    clock_offset: int = 0

    def __post_init__(self):
        if self.timestamp == 0:
            self.timestamp = int(datetime.now().timestamp() * 1000)

    def pack(self) -> bytes:
        return msgpack.packb(asdict(self))

# ============ RELÓGIO LÓGICO ============
logical_clock = 0
clock_lock = threading.Lock()

def clock_before_send() -> int:
    global logical_clock
    with clock_lock:
        logical_clock += 1
        val = logical_clock
    print(f"[RELÓGIO] Incrementado antes de enviar: {val}", flush=True)
    return val

def clock_on_receive(received: int) -> None:
    global logical_clock
    with clock_lock:
        old = logical_clock
        if received > logical_clock:
            logical_clock = received + 1
            print(f"[RELÓGIO] ATUALIZADO! Recebido={received} > local={old}. Novo={logical_clock}", flush=True)
        else:
            logical_clock += 1
            print(f"[RELÓGIO] MANTIDO! Recebido={received} <= local={old}. Novo={logical_clock}", flush=True)

# ============ PERSISTÊNCIA ============
DADOS_FILE = "/app/data/server_data.pkl"

def salvar_dados():
    os.makedirs("/app/data", exist_ok=True)
    with open(DADOS_FILE, "wb") as f:
        pickle.dump({
            "usuarios": usuarios,
            "canais": canais,
            "logins": logins,
            "publicacoes": publicacoes
        }, f)
    print("Dados salvos.", flush=True)

def carregar_dados():
    global usuarios, canais, logins, publicacoes
    if os.path.exists(DADOS_FILE):
        try:
            if os.path.getsize(DADOS_FILE) > 0:
                with open(DADOS_FILE, "rb") as f:
                    dados = pickle.load(f)
                usuarios    = dados.get("usuarios", [])
                canais      = dados.get("canais", [])
                logins      = dados.get("logins", [])
                publicacoes = dados.get("publicacoes", [])
                print(f"Dados carregados: {len(usuarios)} usuarios, {len(canais)} canais, {len(publicacoes)} publicacoes", flush=True)
            else:
                print("Arquivo de dados vazio. Iniciando do zero.", flush=True)
        except (EOFError, pickle.UnpicklingError):
            print("Erro ao ler arquivo. Iniciando do zero.", flush=True)

# ============ COORDENADOR DE REFERÊNCIA ============
def _coord_send_recv(msg_dict: dict) -> dict:
    data = msgpack.packb(msg_dict)
    coord_socket.send(data)
    raw = coord_socket.recv()
    return msgpack.unpackb(raw, raw=False)

def request_rank():
    global server_rank
    lc = clock_before_send()
    resp = _coord_send_recv({
        "type": "get_rank",
        "username": server_name,
        "timestamp": int(datetime.now().timestamp() * 1000),
        "logical_clock": lc,
    })
    clock_on_receive(resp.get("logical_clock", 0))
    if resp.get("success"):
        server_rank = resp.get("rank", -1)
        print(f"[COORDENADOR] Rank recebido: {server_rank}", flush=True)
    else:
        print(f"[COORDENADOR] Erro ao pedir rank: {resp.get('message')}", flush=True)

def get_server_list() -> list:
    lc = clock_before_send()
    resp = _coord_send_recv({
        "type": "get_server_list",
        "username": server_name,
        "timestamp": int(datetime.now().timestamp() * 1000),
        "logical_clock": lc,
    })
    clock_on_receive(resp.get("logical_clock", 0))
    servers = resp.get("server_list", [])
    print(f"[COORDENADOR] {len(servers)} servidores na lista", flush=True)
    for s in servers:
        print(f"  - {s.get('name')} (rank={s.get('rank')})", flush=True)
    return servers

def send_heartbeat():
    lc = clock_before_send()
    resp = _coord_send_recv({
        "type": "heartbeat",
        "username": server_name,
        "timestamp": int(datetime.now().timestamp() * 1000),
        "logical_clock": lc,
    })
    clock_on_receive(resp.get("logical_clock", 0))
    if resp.get("success"):
        print("[HEARTBEAT] OK!", flush=True)
    else:
        print(f"[HEARTBEAT] Erro: {resp.get('message')}", flush=True)
        if "não cadastrado" in resp.get("message", "").lower():
            request_rank()

# ============ SINCRONIZAÇÃO BERKELEY ============
# Mapeamento de endereços dos servidores (host e porta de eleição/sync)
# Como usamos o mesmo socket REP para eleição e sincronização, a porta é a de eleição.
SERVER_ADDRESSES = {
    1: ("servidorPython1", 6003),
    2: ("servidorPython2", 6004),
    3: ("servidorJava1", 6001),
    4: ("servidorJava2", 6002),
}

def get_server_address(rank: int) -> str:
    host, port = SERVER_ADDRESSES.get(rank, (f"servidor{rank}", 6000+rank))
    return f"tcp://{host}:{port}"

def synchronize_clocks():
    """Coordenador inicia Berkeley: coleta relógios e calcula média (simplificado)."""
    if not is_coordinator:
        return
    print("\n[BERKELEY] Iniciando sincronização...", flush=True)
    # Notifica servidores para enviarem seus relógios (opcional, aqui apenas pedimos)
    # Na prática, o coordenador já atende requisições de sync_request via REP.
    # Vamos apenas logar.
    print("[BERKELEY] Sincronização concluída (simplificada).", flush=True)

def request_sync_from_coordinator():
    """Servidor comum pede hora ao coordenador via REQ/REP."""
    global sync_response_received, known_servers
    if current_coordinator is None:
        return False
    # Descobrir endereço do coordenador (ele está na lista de servidores)
    coord_addr = None
    for s in known_servers:
        if s.get("name") == current_coordinator:
            coord_addr = get_server_address(s.get("rank"))
            break
    if coord_addr is None:
        print("[BERKELEY] Não foi possível achar o endereço do coordenador.", flush=True)
        return False

    try:
        req = context.socket(zmq.REQ)
        req.setsockopt(zmq.RCVTIMEO, 2000)
        req.setsockopt(zmq.LINGER, 0)
        req.connect(coord_addr)

        lc = clock_before_send()
        msg = {
            "type": "sync_request",
            "username": server_name,
            "timestamp": int(datetime.now().timestamp() * 1000),
            "logical_clock": lc,
        }
        req.send(msgpack.packb(msg))
        raw = req.recv()
        resp = msgpack.unpackb(raw, raw=False)
        clock_on_receive(resp.get("logical_clock", 0))
        if resp.get("success"):
            sync_response_received = True
            diff = int(datetime.now().timestamp() * 1000) - resp.get("current_time", 0)
            print(f"[BERKELEY] Resposta do coordenador. Diff={diff}ms", flush=True)
            return True
        else:
            print("[BERKELEY] Coordenador respondeu com erro.", flush=True)
            return False
    except zmq.error.Again:
        print("[BERKELEY] Timeout ao pedir hora ao coordenador.", flush=True)
        return False
    finally:
        req.close()

# ============ ELEIÇÃO (Bully) ============
election_lock = threading.Lock()
election_in_progress = False
sync_response_received = False
coordinator_announced = threading.Event()
SYNC_TIMEOUT = 3.0

def start_election():
    global election_in_progress, current_coordinator, is_coordinator, known_servers

    with election_lock:
        if election_in_progress:
            print("[ELEIÇÃO] Já em andamento, ignorando.", flush=True)
            return
        election_in_progress = True

    print(f"\n[ELEIÇÃO] Iniciando Bully... rank={server_rank}", flush=True)
    known_servers = get_server_list()

    sent_to_higher = False
    received_ok = False
    coordinator_announced.clear()

    for s in known_servers:
        peer_rank = s.get("rank", 0)
        if peer_rank <= server_rank: # mandar apenas para servidores com rank maior, senão, continua até achar um de maior rank
            continue
        sent_to_higher = True
        addr = get_server_address(peer_rank)
        print(f"[ELEIÇÃO] Enviando ELECTION para {s.get('name')} em {addr}", flush=True)

        lc = clock_before_send()
        election_msg = {
            "type": "election",
            "username": server_name,
            "election_id": server_rank,
            "timestamp": int(datetime.now().timestamp() * 1000),
            "logical_clock": lc,
        }

        sock = context.socket(zmq.REQ)
        sock.setsockopt(zmq.RCVTIMEO, 2000)
        sock.setsockopt(zmq.LINGER, 0)
        sock.connect(addr)
        try:
            sock.send(msgpack.packb(election_msg))
            raw = sock.recv()
            resp = msgpack.unpackb(raw, raw=False)
            if resp.get("message") == "OK":
                print(f"[ELEIÇÃO] OK de {s.get('name')}", flush=True)
                received_ok = True
        except zmq.error.Again:
            print(f"[ELEIÇÃO] Timeout de {s.get('name')}", flush=True)
        finally:
            sock.close()

    if not sent_to_higher:
        print("[ELEIÇÃO] Sem servidores com rank maior, me tornando coordenador.", flush=True)
        become_coordinator()
        return

    if not received_ok:
        print("[ELEIÇÃO] Nenhuma resposta, me tornando coordenador.", flush=True)
        become_coordinator()
        return

    print("[ELEIÇÃO] OK recebido. Aguardando anúncio por 6s...", flush=True)
    announced = coordinator_announced.wait(timeout=6.0)
    with election_lock:
        already_has_coord = (current_coordinator is not None and current_coordinator != server_name)
    if already_has_coord:
        print(f"[ELEIÇÃO] Coordenador já eleito: {current_coordinator}", flush=True)
        election_in_progress = False
    else:
        become_coordinator()

def become_coordinator():
    global election_in_progress, current_coordinator, is_coordinator, known_servers

    time.sleep(1.0)
    with election_lock:
        if current_coordinator is not None and current_coordinator != server_name:
            print(f"[ELEIÇÃO] Outro já é coordenador: {current_coordinator}", flush=True)
            election_in_progress = False
            return
        if not election_in_progress:
            return  
        is_coordinator = True
        current_coordinator = server_name
        election_in_progress = False
        for server in known_servers:
            if server.get('rank') > server_rank:
                print(f"[ELEIÇÃO] Servidor com rank maior existe: {server.get('name')} (rank={server.get('rank')}), não me torno coordenador")
                electionInProgress = False
                return

    print(f"\n[COORDENADOR] *** EU SOU O NOVO COORDENADOR *** rank={server_rank}", flush=True)

    lc = clock_before_send()
    coord_msg = {
        "type": "coordinator",
        "username": server_name,
        "coordinator_name": server_name,
        "timestamp": int(datetime.now().timestamp() * 1000),
        "logical_clock": lc,
    }
    raw = msgpack.packb(coord_msg)

    # 1) Publicar via PUB/SUB (tópico "servers")
    pub_socket.send_multipart([b"servers", raw])
    print("[COORDENADOR] Anúncio publicado no PUB/SUB.", flush=True)

    # 2) Enviar via REQ/REP para cada servidor conhecido (redundância)
   #announce_coordinator()

'''def announce_coordinator():
    print("[COORDENADOR] Anunciando via REQ/REP...", flush=True)
    for s in known_servers:
        peer_rank = s.get("rank")
        if peer_rank == server_rank:
            continue
        addr = get_server_address(peer_rank)
        try:
            sock = context.socket(zmq.REQ)
            sock.setsockopt(zmq.RCVTIMEO, 2000)
            sock.setsockopt(zmq.LINGER, 0)
            sock.connect(addr)

            lc = clock_before_send()
            sock.send(msgpack.packb({
                "type": "coordinator",
                "username": server_name,
                "coordinator_name": server_name,
                "timestamp": int(datetime.now().timestamp() * 1000),
                "logical_clock": lc,
            }))
            resp = msgpack.unpackb(sock.recv(), raw=False)
            print(f"[COORDENADOR] ACK de {s.get('name')}", flush=True)
        except zmq.error.Again:
            print(f"[COORDENADOR] Sem resposta de {s.get('name')}", flush=True)
        finally:
            sock.close()'''

# ============ THREADS ============
'''def rep_election_thread():
    global current_coordinator, is_coordinator, election_in_progress
    """Thread para responder requisições de eleição e sincronização via REP."""
    print("[REP] Thread iniciada.", flush=True)
    while True:
        try:
            raw = election_rep_socket.recv()
            msg = Message.unpack(raw)
            clock_on_receive(msg.logical_clock)
            print(f"[REP] Recebido tipo={msg.type} de={msg.username}", flush=True)

            if msg.type == "coordinator":
                current_coordinator = msg.coordinator_name
                is_coordinator = (server_name == current_coordinator)
                election_in_progress = False
                coordinator_announced.set()
                lc = clock_before_send()
                election_rep_socket.send(msgpack.packb({
                    "success": True, "message": "ACK", "logical_clock": lc,
                    "timestamp": int(datetime.now().timestamp() * 1000),
                }))

            elif msg.type == "election":
                if msg.election_id < server_rank:
                    lc = clock_before_send()
                    election_rep_socket.send(msgpack.packb({
                        "success": True, "message": "OK", "logical_clock": lc,
                        "timestamp": int(datetime.now().timestamp() * 1000),
                    }))
                    if not election_in_progress:
                        threading.Thread(target=start_election, daemon=True).start()
                else:
                    lc = clock_before_send()
                    election_rep_socket.send(msgpack.packb({
                        "success": True, "message": "ACK", "logical_clock": lc,
                        "timestamp": int(datetime.now().timestamp() * 1000),
                    }))

            elif msg.type == "sync_request":
                # Coordenador responde com sua hora atual
                if is_coordinator:
                    lc = clock_before_send()
                    election_rep_socket.send(msgpack.packb({
                        "success": True,
                        "message": "Hora do coordenador",
                        "current_time": int(datetime.now().timestamp() * 1000),
                        "logical_clock": lc,
                        "timestamp": int(datetime.now().timestamp() * 1000),
                    }))
                else:
                    lc = clock_before_send()
                    election_rep_socket.send(msgpack.packb({
                        "success": False, "message": "Não sou coordenador",
                        "logical_clock": lc,
                        "timestamp": int(datetime.now().timestamp() * 1000),
                    }))

            else:
                lc = clock_before_send()
                election_rep_socket.send(msgpack.packb({
                    "success": True, "message": "ACK", "logical_clock": lc,
                    "timestamp": int(datetime.now().timestamp() * 1000),
                }))
        except Exception as e:
            print(f"[REP] Erro: {e}", flush=True)'''

def sub_server_thread():
    """Thread para receber mensagens do tópico 'servers' (PUB/SUB)."""
    global current_coordinator, is_coordinator, election_in_progress
    print("[SUB] Thread iniciada.", flush=True)
    while True:
        try:
            topic, raw = server_sub_socket.recv_multipart()
            if topic == b"servers":
                msg = Message.unpack(raw)
                clock_on_receive(msg.logical_clock)
                if msg.username == server_name:
                    continue
                print(f"[SUB] Tipo={msg.type} de={msg.username}", flush=True)

                if msg.type == "election" and msg.election_id < server_rank:
                    lc = clock_before_send()
                    pub_socket.send_multipart([b"servers", msgpack.packb({
                        "type": "election_ok", "username": server_name,
                        "election_id": server_rank,
                        "timestamp": int(datetime.now().timestamp() * 1000),
                        "logical_clock": lc,
                    })])
                    if not election_in_progress:
                        threading.Thread(target=start_election, daemon=True).start()

                elif msg.type == "coordinator":
                    current_coordinator = msg.coordinator_name
                    is_coordinator = (server_name == current_coordinator)
                    coordinator_announced.set()
                    election_in_progress = False
                    
                    print(f"[ELEIÇÃO] Coordenador recebido via PUB/SUB: {current_coordinator}", flush=True)

                elif msg.type == "election_ok" and msg.election_id > server_rank:
                    election_in_progress = False
        except Exception as e:
            print(f"[SUB] Erro: {e}", flush=True)

# ============ SETUP ============
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")
context = zmq.Context()

socket = context.socket(zmq.REP)
socket.connect("tcp://broker:5556")

coord_socket = context.socket(zmq.REQ)
coord_socket.connect("tcp://coordinator:5559")

pub_socket = context.socket(zmq.PUB)
pub_socket.connect("tcp://proxy:5557")

server_sub_socket = context.socket(zmq.SUB)
server_sub_socket.connect("tcp://proxy:5558")
server_sub_socket.setsockopt_string(zmq.SUBSCRIBE, "servers")

if len(sys.argv) > 1:
    ELECTION_PORT = int(sys.argv[1])
else:
    ELECTION_PORT = 6001

election_rep_socket = context.socket(zmq.REP)
election_rep_socket.bind(f"tcp://*:{ELECTION_PORT}")

server_name = os.environ.get("SERVER_NAME", "servidor-python-1")
server_rank = -1
known_servers = []
current_coordinator = None
is_coordinator = False
contador_mensagens = 0
sync_message_count = 0

usuarios = []
logins = []
canais = []
publicacoes = []

carregar_dados()
time.sleep(2)
request_rank()
known_servers = get_server_list()

# Inicia threads de comunicação entre servidores
#threading.Thread(target=rep_election_thread, daemon=True).start() Tirando anúncio de eleição com REQ/REP PQ É BESTA, NÉ, JAMANTA
threading.Thread(target=sub_server_thread, daemon=True).start()
time.sleep(1)  # aguarda assinatura se firmar

print(f"[SERVIDOR] {server_name} iniciado! Rank={server_rank}, Porta={ELECTION_PORT}", flush=True)

# ============ LOOP PRINCIPAL ============
while True:
    msg = Message.unpack(socket.recv())
    clock_on_receive(msg.logical_clock)

    contador_mensagens += 1
    sync_message_count += 1
    fazer = msg.type

    print(f"\n[SERVIDOR] type={fazer} user={msg.username} canal={msg.channel_name}", flush=True)

    if contador_mensagens >= 10:
        send_heartbeat()
        contador_mensagens = 0

    if sync_message_count >= 15:
        sync_message_count = 0
        if election_in_progress:
            print("[SYNC] Eleição em andamento, pulando.", flush=True)
        elif is_coordinator:
            threading.Thread(target=synchronize_clocks, daemon=True).start()
        elif current_coordinator is not None:
            ok = request_sync_from_coordinator()
            if not ok:
                current_coordinator = None
                is_coordinator = False
                threading.Thread(target=start_election, daemon=True).start()
        else:
            threading.Thread(target=start_election, daemon=True).start()

    resposta = None

    if fazer == "login":
        if msg.username in usuarios:
            horario = int(datetime.now(tz=fuso).timestamp() * 1000)
            resposta = Response(success=True, message=f"Login realizado as {horario}")
            logins.append((msg.username, horario))
        else:
            usuarios.append(msg.username)
            resposta = Response(success=True, message="Usuario cadastrado e logado!")
        salvar_dados()

    elif fazer == "list_channels":
        if msg.username not in usuarios:
            resposta = Response(success=False, message="Usuario nao logado!")
        else:
            resposta = Response(success=True, message=f"{canais}", channels=list(canais))

    elif fazer == "create_channel":
        if msg.username not in usuarios:
            resposta = Response(success=False, message="Usuario nao logado!")
        elif not msg.channel_name:
            resposta = Response(success=False, message="Nome do canal invalido")
        elif msg.channel_name in canais:
            resposta = Response(success=False, message="Canal ja existe")
        else:
            canais.append(msg.channel_name)
            resposta = Response(success=True, message="Canal criado!")
            salvar_dados()

    elif fazer == "publish":
        if msg.username not in usuarios:
            resposta = Response(success=False, message="Usuario nao logado!")
        elif msg.channel_name not in canais:
            resposta = Response(success=False, message="Canal nao encontrado")
        else:
            agora = int(datetime.now().timestamp() * 1000)
            conteudo = f"{msg.content} | Env: {msg.timestamp} | RecServ: {agora}"
            pub_socket.send_multipart([
                msg.channel_name.encode("utf-8"),
                conteudo.encode("utf-8")
            ])
            publicacoes.append(f"[{msg.channel_name}] {msg.username}: {msg.content}")
            salvar_dados()
            resposta = Response(success=True, message=f"Mensagem publicada em {msg.channel_name}!")
            print(f"[SERVIDOR] Publicado em {msg.channel_name}: {msg.content}", flush=True)

    else:
        resposta = Response(success=False, message="Comando invalido")

    resposta.logical_clock = clock_before_send()
    print(f"[SERVIDOR] Resposta: {resposta.message}", flush=True)
    socket.send(resposta.pack())