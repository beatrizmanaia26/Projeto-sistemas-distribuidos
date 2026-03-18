import zmq
from time import time, sleep
from random import randint
context = zmq.Context()
pub = context.socket(zmq.PUB)
pub.connect("tcp://proxy:5555")

while True:
    numero = randint(1,6)
    message = f"dice {numero}"
    print(f"mensagem: {message}", flush=True)
    pub.send_string(message)
    sleep(1)

pub.close()
context.close()
