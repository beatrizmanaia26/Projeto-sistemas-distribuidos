import zmq
from time import sleep
import json #eniar json do cliente p servidor
from datetime import datetime
import msgpack
import zoneinfo
from dataclasses import dataclass, asdict
@dataclass
class Message:
    type: str
    username: str
    timestamp: int = 0
    
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
    
    @staticmethod
    def unpack(data: bytes) -> "Response":
        d = msgpack.unpackb(data)
        return Response(**d)


context = zmq.Context()
socket = context.socket(zmq.REQ)
socket.connect("tcp://broker:5555")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")
i = 0
while True:

    msg= msgpack.packb({"type": "login", "username":"loba", "timestamp": datetime.now(tz=fuso).timestamp()*1000})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    #mensagem = socket.recv()#rcebe do servidor
    #print(f"{msgpack.unpackb(mensagem)}") #Print sem ter os bytes da mensagem
    mensagem = Response.unpack(socket.recv())
    print(mensagem.message) #Print sem ter os bytes da mensagem
    sleep(0.5)


    """ 
    msg= msgpack.packb({"type": "criar", "username":"eba", "timestamp": datetime.now(tz=fuso).timestamp()*1000})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}") #Print sem ter os bytes da mensagem
    mensagem = Message.unpack(socket.recv())
    print(mensagem) #Print sem ter os bytes da mensagem
    sleep(0.5)

    #para mandar mais mensagens:
    msg= msgpack.packb({"type": "remover", "username":"eba2", "timestamp": datetime.now(tz=fuso).timestamp()*1000})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}") #Print sem ter os bytes da mensagem
    mensagem = Message.unpack(socket.recv())
    print(mensagem) #Print sem ter os bytes da mensagem
    sleep(0.5)
    
    
    #para mandar mais mensagens:
    msg= msgpack.packb({"type": "criar", "username":"eba3", "timestamp": datetime.now(tz=fuso).timestamp()*1000})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}") #Print sem ter os bytes da mensagem
    mensagem = Message.unpack(socket.recv())
    print(mensagem) #Print sem ter os bytes da mensagem
    sleep(0.5)
    

    #para mandar mais mensagens:
    msg= msgpack.packb({"type": "listar", "username":"eba4", "timestamp": datetime.now(tz=fuso).timestamp()*1000})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}") #Print sem ter os bytes da mensagem
    mensagem = Message.unpack(socket.recv())
    print(mensagem) #Print sem ter os bytes da mensagem
    sleep(0.5)
    """
    i += 1
