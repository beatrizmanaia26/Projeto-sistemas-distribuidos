import zmq
from time import time, sleep

context = zmq.Context()
pub = context.socket(zmq.PUB)
pub.connect("tcp://proxy:5555")

while True:
    time = str(time())
    message = f"time: {time}"
    print(f"publicando: {message}", flush=True)
    pub.send_string(message)
    sleep(1)

pub.close()
context.close()
