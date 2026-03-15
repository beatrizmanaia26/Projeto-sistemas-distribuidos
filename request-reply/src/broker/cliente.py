import zmq
from time import sleep
import json #eniar json do cliente p servidor
from datetime import datetime
import msgpack
import zoneinfo
context = zmq.Context()
socket = context.socket(zmq.REQ)
socket.connect("tcp://broker:5555")
fuso = zoneinfo.ZoneInfo("America/Sao_Paulo")
i = 0
while True:

    msg= msgpack.packb({"fazer": "logar", "msg":"loba", "timestamp": datetime.now(tz=fuso).isoformat()})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}")
    sleep(0.5)


    msg= msgpack.packb({"fazer": "criar", "msg":"eba", "timestamp": datetime.now(tz=fuso).isoformat()})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}")
    sleep(0.5)

    #para mandar mais mensagens:
    msg= msgpack.packb({"fazer": "remover", "msg":"eba2", "timestamp": datetime.now(tz=fuso).isoformat()})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}")
    sleep(0.5)
    
    
    #para mandar mais mensagens:
    msg= msgpack.packb({"fazer": "criar", "msg":"eba3", "timestamp": datetime.now(tz=fuso).isoformat()})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}")
    sleep(0.5)
    

    #para mandar mais mensagens:
    msg= msgpack.packb({"fazer": "listar", "msg":"eba4", "timestamp": datetime.now(tz=fuso).isoformat()})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send(msg)#envia pro servidor
    mensagem = socket.recv()#rcebe do servidor
    print(f"{msgpack.unpackb(mensagem)}") #Print sem ter os bytes da mensagem
    sleep(0.5)
    i += 1
