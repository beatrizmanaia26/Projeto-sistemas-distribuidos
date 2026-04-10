import zmq
from time import sleep
from datetime import datetime
import msgpack
import zoneinfo
from dataclasses import dataclass, asdict
from random import randint
import threading

@dataclass
class Message:
    type: str
    username: str
    timestamp: int = 0
    channel_name: str = ""
    received_timestamp: int = 0
    content: str = ""
    def __post_init__(self):
        if self.timestamp == 0:
            self.timestamp = int(datetime.now().timestamp() * 1000)

    def pack(self) -> bytes:
        return msgpack.packb(asdict(self))

@dataclass
class Response:
    success: bool
    message: str
    timestamp: int
    channel_name: str = ""
    channels: list = None
    publication_status: str = ""
    @staticmethod
    def unpack(data: bytes) -> "Response":
        d = msgpack.unpackb(data)
        return Response(**d)




# ... (Definições de Message e Response iguais) ...

context = zmq.Context()

# Socket REQ (para falar com o Servidor via Broker)
req_socket = context.socket(zmq.REQ)
req_socket.connect("tcp://broker:5555")

# Socket SUB (para receber mensagens do Proxy)
sub_socket = context.socket(zmq.SUB)
sub_socket.connect("tcp://proxy:5558")

canais_disponiveis = []
canais_inscritos = []
mensagens = ["Oi", "Tudo", "Uau", "Sim", "Não", "Concordo", "Discordo", "Oito", "Tô sem ideia", "Falta um"]

# --- FUNÇÃO PARA OUVIR O PUB/SUB ---
def ouvir_proxy(username):
    while True:
        try:
            # Recebe a mensagem (Bloqueante, espera até chegar algo)
            topic, content = sub_socket.recv_multipart()
            agora = int(datetime.now().timestamp() * 1000)
            
            # O enunciado pede para mostrar: Canal, Mensagem, Timestamps
            print(f"\n[{username}] RECEBEU do canal {topic.decode()}: {content.decode()} | Timestamp recebimento bot: {agora}")
        except Exception as e:
            print(f"Erro ao receber: {e}")

# Inicia a Thread que vai ficar ouvindo o Proxy para sempre
# Importante passar o nome do bot (que vamos criar) para o print ficar claro
# Vamos iniciar a thread depois de definir o username

def enviar_req(msg: Message) -> Response:
    global canais_disponiveis
    req_socket.send(msg.pack())
    resposta = Response.unpack(req_socket.recv())
    
    # Atualiza a lista de canais, protegendo contra o None
    if resposta.channels is not None:
        canais_disponiveis = resposta.channels
    elif not canais_disponiveis: 
        canais_disponiveis = []
    sleep(1)
    return resposta

def inscrever(canal: str):
    global canais_inscritos
    if canal in canais_disponiveis and canal not in canais_inscritos:
        sub_socket.setsockopt_string(zmq.SUBSCRIBE, canal)
        canais_inscritos.append(canal)
        print(f"Inscrito com sucesso no canal: {canal}")
    elif canal in canais_inscritos:
        print(f"Já inscrito no canal.")
    else:
        print("Canal não existe.")

# --- LÓGICA DO BOT (Conforme o enunciado) ---
def iniciar_bot(bot_id):
    username = f"python-bot-{bot_id}"
    
    # 1. Login
    enviar_req(Message(type="login", username=username))
    
    # Inicia a thread de escuta agora que temos o nome
    thread_escuta = threading.Thread(target=ouvir_proxy, args=(username,), daemon=True)
    thread_escuta.start()

    # 2. Atualiza a lista de canais
    enviar_req(Message(type="list_channels", username=username))
    
    # 3. Regra: Se existirem menos que 5 canais, criar um novo
    if len(canais_disponiveis) < 5:
        novo_canal = f"canal-{username}"
        enviar_req(Message(type="create_channel", username=username, channel_name=novo_canal))
        enviar_req(Message(type="list_channels", username=username)) # Atualiza a lista
        sleep(1)
    
    # 4. Regra: Se estiver inscrito em menos de 3 canais, inscrever
    while len(canais_inscritos) < 3 and len(canais_disponiveis) > 0:
        # Pega um canal aleatório da lista que ainda não estamos inscritos
        canais_para_inscrever = [c for c in canais_disponiveis if c not in canais_inscritos]
        if not canais_para_inscrever:
            break # Não há mais canais diferentes para se inscrever
            
        canal_escolhido = canais_para_inscrever[randint(0, len(canais_para_inscrever)-1)]
        inscrever(canal_escolhido)
        sleep(1)

    # 5. Regra: Loop infinito publicando 10 mensagens (e depois repetindo)
    while True:
        if canais_disponiveis:
            canal_alvo = canais_disponiveis[randint(0, len(canais_disponiveis)-1)]
            print(f"\n{username} iniciando ciclo de publicações em: {canal_alvo}")
            
            for i in range(10):
                conteudo = mensagens[randint(0, len(mensagens)-1)]
                msg_pub = Message(type="publish", username=username, channel_name=canal_alvo, content=conteudo)
                
                resp = enviar_req(msg_pub)
                if not resp.success:
                    print(f"Erro ao publicar: {resp.message}")
                    break # Se der erro, tenta outro canal no próximo ciclo
                
                sleep(1)
        else:
            print("Nenhum canal disponível para publicar.")
            sleep(5)

i = 0
while True:
    username = f"python-bot-{i}"
    
    # 1. Login
    enviar_req(Message(type="login", username=username))
    
    # Inicia a thread de escuta agora que temos o nome
    thread_escuta = threading.Thread(target=ouvir_proxy, args=(username,), daemon=True)
    thread_escuta.start()

    # 2. Atualiza a lista de canais
    enviar_req(Message(type="list_channels", username=username))
    
    # 3. Regra: Se existirem menos que 5 canais, criar um novo
    if len(canais_disponiveis) < 5:
        novo_canal = f"canal-{username}"
        enviar_req(Message(type="create_channel", username=username, channel_name=novo_canal))
        enviar_req(Message(type="list_channels", username=username)) # Atualiza a lista
        sleep(1)
    
    # 4. Regra: Se estiver inscrito em menos de 3 canais, inscrever
    while len(canais_inscritos) < 3 and len(canais_disponiveis) > 0:
        # Pega um canal aleatório da lista que ainda não estamos inscritos
        canais_para_inscrever = [c for c in canais_disponiveis if c not in canais_inscritos]
        if not canais_para_inscrever:
            break # Não há mais canais diferentes para se inscrever
            
        canal_escolhido = canais_para_inscrever[randint(0, len(canais_para_inscrever)-1)]
        inscrever(canal_escolhido)
        sleep(1)

    # 5. Regra: Loop infinito publicando 10 mensagens (e depois repetindo)
    if canais_disponiveis:
            canal_alvo = canais_disponiveis[randint(0, len(canais_disponiveis)-1)]
            print(f"\n{username} iniciando ciclo de publicações em: {canal_alvo}")
            
            for i in range(10):
                conteudo = mensagens[randint(0, len(mensagens)-1)]
                msg_pub = Message(type="publish", username=username, channel_name=canal_alvo, content=conteudo)
                
                resp = enviar_req(msg_pub)
                if not resp.success:
                    print(f"Erro ao publicar: {resp.message}")
                    break # Se der erro, tenta outro canal no próximo ciclo
                
                sleep(1)
    else:
            print("Nenhum canal disponível para publicar.")
            sleep(5)
    i+=1