import zmq

context = zmq.Context()

pub = context.socket(zmq.XPUB)
pub.bind("tcp://*:5556")

sub = context.socket(zmq.XSUB)
sub.bind("tcp://*:5555")

zmq.proxy(pub, sub)

pub.close()
sub.close()
context.close()

#proxy: 2pub 3 sub, proxy faz conexao, proxy ta no meio
#pub1 -hr->            <- hr sub1
#pub2-num aleat-> proxy<-aleat sub2
#                     <-hr e aleat sb3
#                 |    |
#                5555   5556
#                bind     bind 