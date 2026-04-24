import zmq
import msgpack
from datetime import datetime
import threading
import time

# ============ RELÓGIO LÓGICO ============
logical_clock = 0

def clock_before_send() -> int:
    global logical_clock
    logical_clock += 1
    print(f"[COORDENADOR][RELÓGIO] Incrementado antes de enviar: {logical_clock}", flush=True)
    return logical_clock

def clock_on_receive(received: int) -> None:
    global logical_clock
    old = logical_clock
    if received > logical_clock:
        logical_clock = received + 1
        print(f"[COORDENADOR][RELÓGIO] ATUALIZADO! Recebido={received} > local={old}. Novo={logical_clock}", flush=True)
    else:
        logical_clock += 1
        print(f"[COORDENADOR][RELÓGIO] MANTIDO! Recebido={received} <= local={old}. Novo={logical_clock}", flush=True)


# ============ ESTADO DOS SERVIDORES ============
# { nome: { "rank": int, "last_heartbeat": float } }
servers: dict = {}
servers_lock = threading.Lock()
next_rank = 1
HEARTBEAT_TIMEOUT = 30  # segundos


# ============ HANDLERS ============

def process_get_rank(msg: dict) -> dict:
    global next_rank
    server_name = msg.get("username", "").strip()
    if not server_name:
        return {"success": False, "message": "Nome do servidor inválido", "logical_clock": 0, "rank": 0, "current_time": 0}

    with servers_lock:
        if server_name in servers:
            rank = servers[server_name]["rank"]
            print(f"[COORDENADOR] Servidor já cadastrado: {server_name} (rank={rank})", flush=True)
        else:
            rank = next_rank
            next_rank += 1
            servers[server_name] = {
                "rank": rank,
                "last_heartbeat": time.time(),
            }
            print(f"[COORDENADOR] NOVO servidor cadastrado: {server_name} (rank={rank})", flush=True)

    return {"success": True, "message": "Rank atribuído", "rank": rank, "logical_clock": 0, "current_time": 0}


def process_get_server_list(msg: dict) -> dict:
    with servers_lock:
        server_list = [
            {"name": name, "rank": info["rank"]}
            for name, info in servers.items()
        ]
    print(f"[COORDENADOR] Retornando lista com {len(server_list)} servidores", flush=True)
    for s in server_list:
        print(f"  - {s['name']} (rank={s['rank']})", flush=True)
    return {"success": True, "message": "Lista de servidores", "server_list": server_list, "logical_clock": 0, "current_time": 0, "rank": 0}


def process_heartbeat(msg: dict) -> dict:
    server_name = msg.get("username", "").strip()
    with servers_lock:
        if server_name not in servers:
            return {"success": False, "message": "Servidor não cadastrado", "logical_clock": 0, "rank": 0, "current_time": 0}
        servers[server_name]["last_heartbeat"] = time.time()

    current_time = int(datetime.now().timestamp() * 1000)
    print(f"[COORDENADOR] Heartbeat de: {server_name} (rank={servers[server_name]['rank']})", flush=True)
    return {"success": True, "message": "Heartbeat OK", "current_time": current_time, "logical_clock": 0, "rank": 0}


# ============ THREAD DE LIMPEZA ============

def cleanup_loop():
    while True:
        time.sleep(10)
        now = time.time()
        with servers_lock:
            inativos = [
                name for name, info in servers.items()
                if now - info["last_heartbeat"] > HEARTBEAT_TIMEOUT
            ]
            for name in inativos:
                rank = servers.pop(name)["rank"]
                print(f"[COORDENADOR] Servidor REMOVIDO por inatividade: {name} (rank={rank})", flush=True)


# ============ MAIN ============
context = zmq.Context()
socket = context.socket(zmq.REP)
socket.bind("tcp://*:5560")

print("COORDENADOR PYTHON INICIADO", flush=True)
print("Porta: 5560", flush=True)
print("Aguardando servidores...\n", flush=True)

t = threading.Thread(target=cleanup_loop, daemon=True)
t.start()

while True:
    try:
        raw = socket.recv()
        msg = msgpack.unpackb(raw, raw=False)

        clock_on_receive(msg.get("logical_clock", 0))
        print(f"\n[COORDENADOR] Requisição: {msg.get('type')} de {msg.get('username')}", flush=True)

        msg_type = msg.get("type")
        if msg_type == "get_rank":
            response = process_get_rank(msg)
        elif msg_type == "get_server_list":
            response = process_get_server_list(msg)
        elif msg_type == "heartbeat":
            response = process_heartbeat(msg)
        else:
            response = {"success": False, "message": "Tipo desconhecido", "logical_clock": 0, "rank": 0, "current_time": 0}

        response["logical_clock"] = clock_before_send()
        socket.send(msgpack.packb(response))

    except Exception as e:
        print(f"[COORDENADOR] Erro: {e}", flush=True)
        err = {"success": False, "message": "Erro no coordenador", "logical_clock": clock_before_send(), "rank": 0, "current_time": 0}
        socket.send(msgpack.packb(err))