import zmq
from time import sleep
import json #eniar json do cliente p servidor


context = zmq.Context()
socket = context.socket(zmq.REQ)
socket.connect("tcp://broker:5555")

i = 0
while True:
    msg= json.dumps({"fazer": "criar", "msg":"eba"})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send_string(msg)#envia pro servidor
    mensagem = socket.recv_string()#rcebe do servidor
    print(f"{mensagem}")
    sleep(0.5)

    #para mandar mais mensagens:
    msg= json.dumps({"fazer": "remover", "msg":"eba2"})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send_string(msg)#envia pro servidor
    mensagem = socket.recv_string()#rcebe do servidor
    print(f"{mensagem}")
    sleep(0.5)
    
    
    #para mandar mais mensagens:
    msg= json.dumps({"fazer": "criar", "msg":"eba3"})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send_string(msg)#envia pro servidor
    mensagem = socket.recv_string()#rcebe do servidor
    print(f"{mensagem}")
    sleep(0.5)
    

    #para mandar mais mensagens:
    msg= json.dumps({"fazer": "listar", "msg":"eba4"})
    print(f"Mensagem {i}:", end=" ", flush=True)#imprimir no terminal
    socket.send_string(msg)#envia pro servidor
    mensagem = socket.recv_string()#rcebe do servidor
    print(f"{mensagem}")
    sleep(0.5)
    i += 1
