# Projeto Sistemas distribuidos
https://gitlab.com/laferreira/fei/cc7261/-/blob/0ac2c7aa52eccf711b172028f15ac4c1baf380f3/projeto/parte1.md

Escolhemos as linguagens com alta compabilidade com 0MQ(https://zeromq.org/get-started/)

## Lingugem 

c java -> broker -> s java
c python ->       -> s python


Python (ciente e sevidor)- Pois é uma linguagem bastante conhecida pelos programadores, o que o torna mais fácil a implementação do servidor, que precisa de mais código. 

Java (ciente e sevidor)- Também é uma linguagem bastante conhecida pelos programadores, fornecendo mais capacidade para aplicar o cliente, já que os integrantes já obtiveram contato com a linguagem e que o cliente precisa de menos código.

## Serialização (troca de mensagem)
Utilizaremos message pack para serialização de dados, pois ele é similar ao JSON, porém garante melhor performance e menor uso de dados.

### Python
Utilizamos a biblioteca `msgpack` para serializar dicionários Python em bytes binários. A serialização converte os dados em formato binário compacto para envio via ZeroMQ.

### Java
Utilizamos `jackson-dataformat-msgpack` junto com Jackson para serializar objetos Java em bytes binários. A classe `MessagePackUtil.java` encapsula a lógica usando `ObjectMapper` configurado com `MessagePackFactory()`. Os objetos Java (Message, Response) são anotados com `@JsonProperty` para mapeamento correto dos campos durante serialização/desserialização.

## Persistência
Utilizaremos arquivos locais para o servidor salvar dados em disco, permitindo recuperar informações entre sessões.

### Python
Utilizaremos o pickle para o servidor salvar em arquivos, pois permite salvar objetos Python em arquivos. Ele converte os objetos em uma forma serializada, que pode ser armazenada ou transmitida.

### Java
Utilizaremos Java Object Serialization (ObjectOutputStream/ObjectInputStream) para persistir dados em disco. Permite salvar objetos Java nativamente em arquivos `.ser`, mantendo a estrutura completa dos objetos (Maps, Sets, etc). O servidor Java salva dois arquivos: `users_data.ser` (Map com username → timestamp de login) e `channels_data.ser` (Set com nomes dos canais criados).
                                                    
Dockerfile (ou Containerfile) para a criação das imagens que serão necessárias para executar o projeto

docker-compose.yaml para execução de todos os containers do projeto considerando a execução de 2 clientes e 2 servidores para cada integrante do grupo
código fonte do(s) cliente(s), servidore(s) e broker implementados nas linguagens escolhidas


### servidor

-subir

docker-compose up

-derrubar

docker stop nome

## Parte 5: Consistência e Replicação

### Método Escolhido: Consistência Eventual

A Consistência Eventual foi escolhida como método de replicação de dados para este projeto.

### Justificativa da Escolha

#### Modelo Multi-Master
No projeto, mais de um servidor trata todos os dados simultaneamente. Cada servidor pode receber requisições de clientes via balanceamento round-robin do broker. Todos os servidores são ativos e processam operações de escrita (login, criação de canais, publicações). Não há um servidor primário único - todos são iguais em capacidade.

#### Compatibilidade com Arquitetura Existente
O projeto já possui componentes que facilitam a implementação:
- Eleição Bully: Coordenador eleito pode gerenciar sincronização
- Sincronização Berkeley: Relógios lógicos já implementados para ordenação de eventos
- Heartbeats periódicos: Infraestrutura de comunicação entre servidores
- Tópico "servers": Canal de comunicação pub-sub entre servidores já existe

#### Alta Disponibilidade e Performance
Clientes não precisam esperar que todos os servidores confirmem uma operação. Cada servidor responde imediatamente após salvar localmente. Sincronização acontece em background, sem bloquear operações. Sistema continua funcionando mesmo se alguns servidores estiverem temporariamente indisponíveis.

#### Tolerância a Falhas
Se um servidor cai durante sincronização, não há perda de dados. Quando o servidor volta, sincroniza automaticamente com os outros. Não há ponto único de falha na replicação. Falhas de rede temporárias não impedem o sistema de funcionar.

### Como Resolve o Problema do Projeto

Problema Original:
- Broker distribui requisições em round-robin entre servidores
- Cada servidor armazena apenas as mensagens que processou
- Se um servidor cai, perde-se parte do histórico
- Cliente que pede histórico recebe apenas dados parciais

Solução com Consistência Eventual:
1. Cada servidor continua processando requisições normalmente
2. Após processar dados (login, canal, publicação), salva localmente
3. Periodicamente (a cada N mensagens ou tempo T), servidor envia seus dados novos para outros servidores e recebe dados novos de outros servidores
4. Faz merge (união) dos dados usando timestamps e relógios lógicos
5. Eventualmente, todos os servidores terão todos os dados
6. Cliente pode pedir histórico a qualquer servidor e receber dados completos

### Implementação

#### Sincronização Periódica
A cada 20 mensagens processadas, o servidor inicia processo de replicação de dados.

#### Envio de Dados para Replicação
Servidor serializa seus dados locais (publicações, canais, usuários), envia via tópico "servers" ou diretamente para cada servidor conhecido, e usa relógio lógico para marcar timestamp dos dados.

#### Recebimento e Merge de Dados
Servidor recebe dados de outros servidores, compara timestamps e relógios lógicos, adiciona apenas dados que não possui localmente, e evita duplicatas usando IDs únicos.

#### Detecção de Conflitos
Usa relógios lógicos de Lamport já implementados. Em caso de conflito, prevalece o evento com maior timestamp. Se timestamps iguais, usa rank do servidor como desempate.

### Mudanças Necessárias no Código

#### Estrutura de Dados
Adicionar campo de timestamp e relógio lógico em todas as entidades. Adicionar ID único para cada publicação, canal e usuário.

#### Protocolo de Sincronização
Mensagens tipo "sync_data" para enviar dados. Mensagens tipo "sync_ack" para confirmar recebimento. Usar tópico "servers" já existente.

#### Persistência
Salvar timestamp junto com dados. Carregar dados com timestamps ao iniciar servidor.

### Garantias do Sistema

- Disponibilidade: Sistema sempre responde, mesmo durante sincronização
- Tolerância a Partições: Servidores podem ficar temporariamente isolados
- Consistência: Eventual (não imediata) - pode haver breve período onde servidores têm dados diferentes
- Convergência: Todos os servidores eventualmente terão os mesmos dados
- Ordenação: Relógios lógicos garantem ordem causal de eventos

### Comparação com Outras Abordagens

Token Ring: Mais complexo, requer controle de token, pode causar bloqueios. Token pode se perder.

Replicação Primária: Tem ponto único de falha, menor disponibilidade, mas garante consistência forte.

Quorum (Paxos/Raft): Muito complexo para este projeto, alto overhead, mas garante consistência forte.

Consistência Eventual: Alta disponibilidade, boa performance, simples de implementar, multi-master, mas dados temporariamente inconsistentes.