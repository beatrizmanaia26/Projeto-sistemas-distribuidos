import zmq
from time import sleep

context = zmq.Context()
sub = context.socket(zmq.SUB)
sub.setsockopt_string(zmq.SUBSCRIBE, "")#vazio vai em td
sub.connect("tcp://proxy:5556")

while True:
    message = sub.recv_string()
    print(f"hr recebida: {message}", flush=True)

sub.close()
context.close()
