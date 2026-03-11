# Projeto Sistemas distribuidos
https://gitlab.com/laferreira/fei/cc7261/-/blob/0ac2c7aa52eccf711b172028f15ac4c1baf380f3/projeto/parte1.md

Escolhemos as linguagens com alta compabilidade com 0MQ(https://zeromq.org/get-started/)

## Lingugem 

c java -> broker -> s java
c python ->       -> s python


Python (ciente e sevidor)- Pois é uma linguagem bastante conhecida pelos programadores, o que o torna mais fácil a implementação do servidor, que precisa de mais código. 

Java (ciente e sevidor)- Também é uma linguagem bastante conhecida pelos programadores, fornecendo mais capacidade para aplicar o cliente, já que os integrantes já obtiveram contato com a linguagem e que o cliente precisa de menos código.

## Serialização(troca de mensgem)
Utilizaremos message pack para serialização de dados, pois ele é similar ao JSON, porém garante melhor performance e menor uso de dados

## Persistência
Utilizaremos o pickle para o servidor salvar em arquivos, pois permite salvar objetos Python em arquivos. Ele converte os objetos em uma forma serializada, que pode ser armazenada ou transmitida. 
                                                    
Dockerfile (ou Containerfile) para a criação das imagens que serão necessárias para executar o projeto

docker-compose.yaml para execução de todos os containers do projeto considerando a execução de 2 clientes e 2 servidores para cada integrante do grupo
código fonte do(s) cliente(s), servidore(s) e broker implementados nas linguagens escolhidas