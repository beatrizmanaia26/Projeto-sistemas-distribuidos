import os
import sys
import time
import threading
import uuid
import zmq
import zoneinfo
from datetime import datetime
import msgpack
from dataclasses import dataclass, asdict, field
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
data_lock = threading.Lock()

def salvar_dados():
    os.makedirs("/app/data", exist_ok=True)
    with data_lock:
        with open(DADOS_FILE, "wb") as f:
            pickle.dump({
                "usuarios": usuarios,
                "canais": canais,
                "logins": logins,
                "publicacoes": publicacoes,
                "publicacoes_ids": publicacoes_ids,
            }, f)
    print("Dados salvos.", flush=True)

def carregar_dados():
    global usuarios, canais, logins, publicacoes, publicacoes_ids
    if os.path.exists(DADOS_FILE):
        try:
            if os.path.getsize(DADOS_FILE) > 0:
                with open(DADOS_FILE, "rb") as f:
                    dados = pickle.load(f)
                usuarios        = dados.get("usuarios", [])
                canais          = dados.get("canais", [])
                logins          = dados.get("logins", [])
                publicacoes     = dados.get("publicacoes", [])
                publicacoes_ids = set(dados.get("publicacoes_ids", []))
                # Retrocompatibilidade: reconstruir IDs se necessário
                if not publicacoes_ids:
                    for pub in publicacoes:
                        if isinstance(pub, dict) and "id" in pub:
                            publicacoes_ids.add(pub["id"])
                print(f"Dados carregados: {len(usuarios)} usuarios, {len(canais)} canais, "
                      f"{len(publicacoes)} publicacoes", flush=True)
            else:
                print("Arquivo de dados vazio. Iniciando do zero.", flush=True)
        except (EOFError, pickle.UnpicklingError) as e:
            print(f"Erro ao ler arquivo ({e}). Iniciando do zero.", flush=True)

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

# ============ REPLICAÇÃO - CONSISTÊNCIA EVENTUAL ============
# Intervalo de replicação: a cada N mensagens de clientes processadas
REPLICATION_INTERVAL = 5
replication_count = 0
replication_lock = threading.Lock()

def replicar_dados():
    """
    Envia todos os dados locais (publicações, canais, usuários/logins) para os
    outros servidores via tópico 'servers' no proxy PUB/SUB.

    Estratégia: Consistência Eventual
    - Cada servidor mantém cópia completa dos seus dados.
    - Periodicamente (a cada REPLICATION_INTERVAL mensagens) transmite seus dados.
    - Ao receber dados de outro servidor, faz merge local (união + deduplicação).
    - Usa IDs únicos por publicação para evitar duplicatas.
    - Em caso de conflito de timestamp, prevalece o evento mais antigo
      (primeiro login, primeira publicação).
    - Se timestamps iguais, o rank mais baixo do servidor tem precedência
      (determinismo).
    """
    with data_lock:
        pubs_snapshot    = list(publicacoes)
        canais_snapshot  = list(canais)
        users_dict = {user: ts for user, ts in logins}

    payload = {
        "type": "sync_data",
        "username": server_name,
        "server_rank": server_rank,
        "logical_clock": clock_before_send(),
        "timestamp": int(datetime.now().timestamp() * 1000),
        "publications": pubs_snapshot,
        "channels": canais_snapshot,
        "users": users_dict,
    }
    raw = msgpack.packb(payload)
    pub_socket.send_multipart([b"servers", raw])
    print(f"[REPLICAÇÃO] Dados enviados: {len(pubs_snapshot)} publicações, "
          f"{len(canais_snapshot)} canais, {len(users_dict)} logins", flush=True)


def merge_dados(msg: dict):
    """
    Recebe dados de outro servidor e faz merge local.
    Usa deduplicação por ID para publicações e união simples para canais/logins.
    """
    global usuarios, canais, logins, publicacoes, publicacoes_ids

    sender      = msg.get("username", "?")
    sender_rank = msg.get("server_rank", 999)
    clock_on_receive(msg.get("logical_clock", 0))

    novas_pubs    = 0
    novos_canais  = 0
    novos_logins  = 0

    with data_lock:
        # ---- Publicações (Agora lendo "publications") ----
        for pub in msg.get("publications", []):
            if isinstance(pub, dict):
                pub_id = pub.get("id")
                if pub_id and pub_id not in publicacoes_ids:
                    publicacoes.append(pub)
                    publicacoes_ids.add(pub_id)
                    novas_pubs += 1

        publicacoes = [p for p in publicacoes if isinstance(p, dict)]
        publicacoes.sort(key=lambda p: (
            p.get("logicalClock", 0),
            p.get("timestamp", 0),
            p.get("serverRank", 0),
        ))

        # ---- Canais (Agora lendo "channels") ----
        for canal in msg.get("channels", []):
            if canal not in canais:
                canais.append(canal)
                novos_canais += 1

        # ---- Logins / Usuários (Agora lendo "users" como um dicionário) ----
        logins_dict: dict = {}
        for (u, ts) in logins:
            if u not in logins_dict or ts < logins_dict[u]:
                logins_dict[u] = ts

        # O Java e o Python agora mandam um dict {"bot1": 12345}
        users_recebidos = msg.get("users", {})
        if isinstance(users_recebidos, dict):
            for u, ts in users_recebidos.items():
                if u not in logins_dict or ts < logins_dict[u]:
                    logins_dict[u] = ts
                    novos_logins += 1

        logins = [(u, ts) for u, ts in logins_dict.items()]

        for u, _ in logins:
            if u not in usuarios:
                usuarios.append(u)

    print(f"[REPLICAÇÃO] Merge concluído: +{novas_pubs} pubs, "
          f"+{novos_canais} canais, +{novos_logins} logins", flush=True)

    if novas_pubs or novos_canais or novos_logins:
        salvar_dados()


    # Enviar ACK de volta no tópico 'servers'
    ack = {
        "type": "sync_ack",
        "username": server_name,
        "server_rank": server_rank,
        "logical_clock": clock_before_send(),
        "timestamp": int(datetime.now().timestamp() * 1000),
    }
    pub_socket.send_multipart([b"servers", msgpack.packb(ack)])
    print(f"[REPLICAÇÃO] ACK enviado para {sender}", flush=True)


# ============ SINCRONIZAÇÃO BERKELEY ============
SERVER_ADDRESSES = {
    1: ("servidorPython1", 6003),
    2: ("servidorPython2", 6004),
    3: ("servidorJava1",   6001),
    4: ("servidorJava2",   6002),
}

def get_server_address(rank: int) -> str:
    host, port = SERVER_ADDRESSES.get(rank, (f"servidor{rank}", 6000 + rank))
    return f"tcp://{host}:{port}"

def synchronize_clocks():
    if not is_coordinator:
        return
    print("\n[BERKELEY] Iniciando sincronização...", flush=True)
    print("[BERKELEY] Sincronização concluída (simplificada).", flush=True)

def request_sync_from_coordinator():
    global sync_response_received, known_servers
    if current_coordinator is None:
        return False
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
        if peer_rank <= server_rank:
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
            if server.get("rank") > server_rank:
                print(f"[ELEIÇÃO] Servidor com rank maior existe: {server.get('name')} "
                      f"(rank={server.get('rank')}), não me torno coordenador", flush=True)
                election_in_progress = False
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
    pub_socket.send_multipart([b"servers", raw])
    print("[COORDENADOR] Anúncio publicado no PUB/SUB.", flush=True)


# ============ THREAD SUB SERVIDORES ============
def sub_server_thread():
    """Recebe mensagens do tópico 'servers': eleição, coordenador e sync_data."""
    global current_coordinator, is_coordinator, election_in_progress

    print("[SUB] Thread iniciada.", flush=True)
    while True:
        try:
            topic, raw = server_sub_socket.recv_multipart()
            if topic != b"servers":
                continue

            # Tentar desserializar como dicionário puro (para sync_data)
            # e também como Message (para eleição/coordenador)
            try:
                d = msgpack.unpackb(raw, raw=False)
            except Exception:
                print(f"[SUB] Erro no unpack: {e}", flush=True)
                continue

            msg_type = d.get("type", "")
            sender   = d.get("username", "")

            # Ignorar mensagens próprias
            if sender == server_name:
                continue

            print(f"[SUB] Tipo={msg_type} de={sender}", flush=True)

            # ---- Replicação ----
            if msg_type == "sync_data":
                threading.Thread(target=merge_dados, args=(d,), daemon=True).start()
                continue

            if msg_type == "sync_ack":
                print(f"[REPLICAÇÃO] ACK recebido de {sender}", flush=True)
                continue

            # ---- Eleição ----
            if msg_type == "election":
                sender_rank = d.get("election_id", 0)
                clock_on_receive(d.get("logical_clock", 0))
                if sender_rank < server_rank:
                    lc = clock_before_send()
                    pub_socket.send_multipart([b"servers", msgpack.packb({
                        "type": "election_ok",
                        "username": server_name,
                        "election_id": server_rank,
                        "timestamp": int(datetime.now().timestamp() * 1000),
                        "logical_clock": lc,
                    })])
                    if not election_in_progress:
                        threading.Thread(target=start_election, daemon=True).start()

            elif msg_type == "election_ok":
                clock_on_receive(d.get("logical_clock", 0))
                if d.get("election_id", 0) > server_rank:
                    election_in_progress = False

            elif msg_type == "coordinator":
                clock_on_receive(d.get("logical_clock", 0))
                current_coordinator = d.get("coordinator_name", sender)
                is_coordinator      = (server_name == current_coordinator)
                coordinator_announced.set()
                election_in_progress = False
                print(f"[ELEIÇÃO] Coordenador recebido via PUB/SUB: {current_coordinator}", flush=True)

        except Exception as e:
            print(f"[SUB] Erro: {e}", flush=True)


# ============ SETUP ============
fuso    = zoneinfo.ZoneInfo("America/Sao_Paulo")
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

server_name  = os.environ.get("SERVER_NAME", "servidor-python-1")
server_rank  = -1
known_servers      = []
current_coordinator = None
is_coordinator      = False
contador_mensagens  = 0
sync_message_count  = 0

# Estruturas de dados com IDs de publicação para deduplicação
usuarios        = []
logins          = []
canais          = []
publicacoes     = []         # lista de dicts com campo "id"
publicacoes_ids = set()      # set de IDs já vistos (deduplicação)

carregar_dados()
time.sleep(2)
request_rank()
known_servers = get_server_list()

threading.Thread(target=sub_server_thread, daemon=True).start()
time.sleep(1)

print(f"[SERVIDOR] {server_name} iniciado! Rank={server_rank}, Porta={ELECTION_PORT}", flush=True)

# ============ LOOP PRINCIPAL ============
while True:
    msg = Message.unpack(socket.recv())
    clock_on_receive(msg.logical_clock)

    contador_mensagens += 1
    sync_message_count += 1
    replication_count  += 1
    fazer = msg.type

    print(f"\n[SERVIDOR] type={fazer} user={msg.username} canal={msg.channel_name}", flush=True)

    # Heartbeat a cada 10 mensagens de clientes
    if contador_mensagens >= 10:
        send_heartbeat()
        contador_mensagens = 0

    # Sincronização de relógio a cada 15 mensagens
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
                is_coordinator      = False
                threading.Thread(target=start_election, daemon=True).start()
        else:
            threading.Thread(target=start_election, daemon=True).start()

    # ---- Replicação a cada REPLICATION_INTERVAL mensagens ----
    if replication_count >= REPLICATION_INTERVAL:
        replication_count = 0
        print(f"\n[REPLICAÇÃO] {REPLICATION_INTERVAL} mensagens processadas, replicando...", flush=True)
        threading.Thread(target=replicar_dados, daemon=True).start()

    resposta = None

    # ---- Handlers de mensagens de clientes ----
    if fazer == "login":
        if msg.username not in usuarios:
            usuarios.append(msg.username)
        horario = int(datetime.now(tz=fuso).timestamp() * 1000)
        logins.append((msg.username, horario))
        resposta = Response(success=True, message=f"Login realizado as {horario}")
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
            # Registro da publicação com ID único para deduplicação na replicação
            pub_record = {
                "id":            str(uuid.uuid4()),
                "username":      msg.username,
                "channelName":  msg.channel_name,
                "content":       msg.content,
                "timestamp":     msg.timestamp,
                "serverRank":   server_rank,
                "logicalClock": logical_clock,
            }
            with data_lock:
                publicacoes.append(pub_record)
                publicacoes_ids.add(pub_record["id"])

            # Publicar no proxy (conteúdo formatado para subscribers)
            conteudo = f"{msg.content} | Env: {msg.timestamp} | RecServ: {agora}"
            pub_socket.send_multipart([
                msg.channel_name.encode("utf-8"),
                conteudo.encode("utf-8"),
            ])
            salvar_dados()
            resposta = Response(success=True, message=f"Mensagem publicada em {msg.channel_name}!")
            print(f"[SERVIDOR] Publicado em {msg.channel_name}: {msg.content}", flush=True)

    else:
        resposta = Response(success=False, message="Comando invalido")

    resposta.logical_clock = clock_before_send()
    print(f"[SERVIDOR] Resposta: {resposta.message}", flush=True)
    socket.send(resposta.pack())