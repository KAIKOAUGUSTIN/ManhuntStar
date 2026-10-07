# Manhunt*

Bots caçadores de **ManHunt** para servidores **Paper 26.2** (e 26.3 beta).

Os bots são entidades **Mannequin** (o "boneco com skin de jogador" do jogo) controladas pelo plugin
para agir como jogadores: correm, pulam, nadam, cavam, constroem torres e pontes, lutam com recarga de
arma, críticos e escudo, comem, sobem de fase, usam pérolas, besta, tridente, balde de lava, cavalo, barco
e elytra, e seguem o jogador para o Nether e o End.

## Instalação

1. Coloque `ManhuntStar-1.1.1.jar` na pasta `plugins/` de um servidor **Paper 26.2** (ou 26.3).
2. Inicie o servidor. São criados `plugins/ManhuntStar/config.yml`, `messages.yml` e a pasta `skins/`.
3. Entre como operador e use `/bot create Cacador 3` perto de você, ou abra `/bot menu`.

Requisitos: Java 25 (o mesmo do Paper 26.x). Não depende de outros plugins.

## Comandos

Todos exigem a permissão `manhuntstar.admin` (operadores têm por padrão). `/manhuntstar` é um apelido de `/bot`.

| Comando | O que faz |
|---|---|
| `/bot create <nome> [qtd] [dificuldade] [fase] [agressividade]` | Cria bots onde você está. No modo MANHUNT eles nascem em roda em volta do jogador mais próximo. |
| `/bot create <nome> <qtd> <dificuldade> skin <jogador> [fase] [agr]` | Igual, com a skin de outro jogador. |
| `/bot spawn <nome> <qtd> <dificuldade> <x> <y> <z> [fase] [agr]` | Cria em coordenadas. |
| `/bot start` | Começa a caçada na hora (pula a contagem / solta os bots da roda). |
| `/bot menu` | Menu em baú: criar, gerenciar, opções, grupos e ações. |
| `/bot list` · `/bot info <bot>` | Lista os bots (vida, fase, alvo, local, o que a IA está fazendo). |
| `/bot remove <nome>` · `/bot deleteall confirm` | Remove bots (o segundo apaga tudo, inclusive o salvamento). |
| `/bot tp <bot> <jogador>` · `/bot tpall` | Teleporta bots. |
| `/bot target <jogador>` · `/bot cleartarget` | Força todos a perseguirem um jogador / volta ao normal. |
| `/bot settings [chave] [valor]` | Mostra ou muda opções (`pathmode`, `nodes`, `allowtowering`, `targetmode`, `glowing true red`, `starttime 20`...). `/bot gameplay` é igual. |
| `/bot group role <grupo> <hunter\|escape>` | Papel do grupo (caçador ou fugitivo). |
| `/bot group enemy add\|remove <grupo> <inimigo>` | Grupos inimigos lutam entre si. |
| `/bot group spawnpoint set\|clear <grupo> [x y z]` · `phaseprogression` · `trackplayers` · `delete` | Outras opções de grupo. |
| `/bot gamerule min_sec_range_nether_entering\|max_sec_range_nether_entering <s>` | Atraso para seguirem o jogador a outra dimensão. |
| `/bot setspawnpoint <x> <y> <z> <bot>` | Ponto de respawn de um bot. |
| `/bot skin <bot> <jogador>` | Troca a skin. |
| `/bot give <item>` · `/bot armor <tier>` | Dá item / armadura a todos os bots. |
| `/bot execute <tnt-craft\|bow-burst\|firework-crossbow>` | Testa uma capacidade especial contra você. |
| `/bot reload` | Relê `config.yml` e `messages.yml`. |
| `/mansave spawn [nome] [qtd]` · `/mansave clear` | Protetores Mansave. |

Dificuldades: `difficulty.easy`, `difficulty.medium`, `difficulty.hard`, `difficulty.terminator` (ou `facil`, `medio`, `dificil`, `terminator`).

Nomes com `%`, acento ou espaço vão entre aspas nos comandos, por exemplo `/bot create "Caçador%" 5` e
`/bot group role "Caçador%" escape`. O Tab já completa os nomes com as aspas.

## O que dá para configurar

Tudo está comentado no `config.yml` e pode ser mudado no jogo. Destaques:

- **Contagem antes da caçada** (`manhunt.contagem-segundos`, padrão 20): os bots esperam na roda até o
  jogador sair dela; aí começa a contagem de vantagem, com barra no topo e títulos 3-2-1, e só então eles
  começam a caçar. Com `manhunt.contagem-ao-sair-da-roda: false` a contagem começa assim que os bots são
  criados. `0` = os bots soltam assim que o jogador corre, como no mod.
- **Brilho** nos caçadores (`aparencia.brilho-cacadores`, com cor) e, separado, nos fugitivos. Vem desligado;
  liga e troca a cor direto no `/bot menu` ou com `/bot settings glowing true red`.
- **Fidelidade de jogador**: bots no TAB com skin e ping, contando no número de jogadores da lista de
  servidores e no `/list`, mensagens "entrou/saiu do jogo" e mensagens de morte do jogo.
- **Mobs hostis atacam os bots** (zumbis, esqueletos, creepers, aranhas no escuro, slimes por contato,
  piglins ignoram quem usa ouro...), com lista de exceções.
- Combate, mobilidade, pathfinding (A*, Dijkstra ou direto), táticas, persistência, dificuldade padrão...

## Limitações

- Outros plugins que procuram "jogadores" (por exemplo a bússola de outro plugin de ManHunt) não enxergam
  os bots: eles aparecem no TAB, no `/list` e na contagem, mas não são jogadores para a API do servidor.
- Nomes de bots e grupos no menu são digitados no chat.
- As linhas de debug do A* são partículas (`/bot settings debugastar true`), visíveis para quem tem `manhuntstar.admin`.
- A lista do TAB usa os pacotes internos do servidor; se uma versão futura do Paper mudar esse pacote,
  só essa função se desliga (com aviso no console) e o resto do plugin continua.

## Detalhes do plugin

- **Mira**: besta, arco, tridente e besta com fogos calculam o ângulo pela física real do projétil no servidor e
  antecipam o alvo pelo tempo de voo.
- **Cavalo**: no jogo só um jogador comanda o cavalo; com o bot montado, a IA do cavalo é desligada enquanto ele
  cavalga (o cavalo vira para onde o bot olha, galopa, pula obstáculos e vãos, boia na água) e religada quando ele desce.
- **Bots longe dos jogadores** continuam se movendo: o plugin mantém os chunks em volta de cada bot carregados (com
  contagem, para um bot não soltar o chunk de outro) e impede que o Paper deixe o bot "inativo" por estar longe de jogadores.
- **Skins**: pela conta Mojang do nome, por um `.png` em `plugins/ManhuntStar/skins/` ou a skin de robô padrão.
- **Persistência**: bots, grupos e equipamento salvos em `bots.yml` e restaurados ao reiniciar.

## Desempenho

Um único tick para todos os bots; caminhos calculados fora da thread principal sobre cópias de chunks
reaproveitadas entre bots; tarefas pesadas espalhadas entre ticks. Em teste, 40 bots lutando entre si
deixaram o servidor em ~3 ms por tick (o limite é 50 ms).

## API para outros plugins

`dev.manhuntstar.api.ManhuntStarAPI` (listar bots, achar o bot de uma entidade) e os eventos
`BotEvents.BotDamageByPlayerEvent`, `BotFallDamageEvent`, `BotDeathEvent` e `BotKilledByPlayerEvent`.

## Compilar

Precisa do JDK 25.

```bash
./gradlew build
```

O jar sai em `build/libs/ManhuntStar-1.1.1.jar`. O `build` também roda os testes e compila o código contra a
API do Paper 26.3 (`compileAgainst263`) para avisar de quebras de compatibilidade.

Diagnóstico em servidor de teste (flags de JVM): `-Dmanhuntstar.teste.mira=true` registra no console o acerto ou
erro de cada projétil dos bots; `-Dmanhuntstar.teste.cavalo=true` mostra detalhes do cavalo no `/bot info`.

## Créditos

Baseado no mod Fabric **T\* (Terminator Star)** de **OpaleMC**, licença CC0: o comportamento foi estudado
no mod e reescrito do zero para a API do Paper, com correções e otimizações.
