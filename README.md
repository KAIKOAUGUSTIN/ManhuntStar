# Manhunt*

Bots caçadores de **ManHunt** para servidores **Paper 26.2** (e 26.3 beta).

Os bots são entidades **Mannequin** (o "boneco com skin de jogador" do jogo) controladas pelo plugin
para agir como jogadores: correm, pulam, nadam, cavam, constroem torres e pontes, lutam com recarga de
arma, críticos e escudo, comem, sobem de fase, usam pérolas, besta, tridente, balde de lava, cavalo (com carga
de lança), barco e elytra, e seguem o jogador para o Nether e o End.

## Instalação

1. Coloque `ManhuntStar-1.5.2.jar` na pasta `plugins/` de um servidor **Paper 26.2** (ou 26.3).
2. Inicie o servidor. São criados `plugins/ManhuntStar/config.yml`, `messages.yml` e a pasta `skins/`.
3. Entre como operador e use `/manhunt create Cacador 3` perto de você, ou abra `/manhunt menu`.

Requisitos: Java 25 (o mesmo do Paper 26.x). Não depende de outros plugins.

## Comandos

Tudo fica em `/manhunt` e exige a permissão `manhuntstar.admin` (operadores têm por padrão). Os verbos são
os mesmos em tudo: `create`/`remove` para bots e grupos, `add`/`remove` para listas e `clear` para apagar um
valor. Posições aceitam `~ ~ ~`; sem posição vale onde você está.

**Partida**

| Comando | O que faz |
|---|---|
| `/manhunt start` | Começa a caçada na hora (solta todas as rodas e pula a contagem). |
| `/manhunt pause` · `/manhunt resume` | Pausa: congela cada bot onde está (até no ar, montado ou de barco), invencível e sem pensar, e para a contagem da roda, a proteção de quem entrou no meio, o tempo das fases e o renascimento. A pausa continua se o servidor reiniciar. |
| `/manhunt target <jogador>` · `/manhunt target clear` | Força todos a perseguirem um jogador / volta ao normal. |
| `/manhunt ring` · `/manhunt ring point [x y z \| clear]` | Mostra a roda; marca um ponto fixo para as rodas (centro em manual) ou apaga o ponto (volta ao automático). |
| `/manhunt players` · `players add\|remove <jogador>` · `players mode <off\|allowlist\|blocklist>` | Lista de jogadores que podem (ou não) ser caçados e disparar a roda. |

**Bots**

| Comando | O que faz |
|---|---|
| `/manhunt create <nome> [qtd] [dificuldade] [skin <jogador>] [at <x y z>] [fase] [agressividade]` | Cria bots onde você está (ou no ponto da roda, com o centro em manual), ou em `at x y z`. No modo MANHUNT eles nascem em roda. |
| `/manhunt remove <bot>` · `/manhunt remove all confirm` | Remove os bots com esse nome / apaga todos, inclusive o salvamento. |
| `/manhunt list` · `/manhunt info <bot>` | Lista os bots (vida, fase, alvo, local, o que a IA está fazendo). |
| `/manhunt tp <bot\|all> [jogador]` | Leva bots até um jogador (ou até você). |
| `/manhunt skin <bot> <jogador>` | Troca a skin. |
| `/manhunt spawnpoint <bot> [x y z \| clear]` | Ponto de renascimento de um bot. |
| `/manhunt equip item <item>` · `/manhunt equip armor <tipo>` | Dá item / armadura a todos os bots. |
| `/manhunt test <tnt-craft\|bow-burst\|firework-crossbow>` | Testa uma capacidade especial contra você. |

**Grupos** (o nome do grupo vem antes da ação)

| Comando | O que faz |
|---|---|
| `/manhunt group <grupo> role <hunter\|escape>` | Papel do grupo (caçador ou fugitivo). |
| `/manhunt group <grupo> enemy add\|remove <outro>` | Grupos inimigos lutam entre si. |
| `/manhunt group <grupo> spawnpoint [x y z \| clear]` | Ponto de renascimento do grupo. |
| `/manhunt group <grupo> phases <true\|false>` · `chaseplayers <true\|false>` | Fases e perseguir jogadores. |
| `/manhunt group <grupo> remove` | Remove o grupo e os bots dele. |

**Opções e menu**

| Comando | O que faz |
|---|---|
| `/manhunt settings [opção] [valor]` | Mostra ou muda opções (`pathmode`, `pathnodes`, `allowtowering`, `targetmode`, `glowhunters true red`, `countdown 20`, `ringradius auto`...). |
| `/manhunt reload` | Relê `config.yml` e `messages.yml`. |
| `/manhunt menu` | Menu em baú em quatro seções: Partida, Regras da caçada, Bots e Configurações. |

Dificuldades: `easy`, `medium`, `hard`, `terminator`.

Nomes com `%`, acento ou espaço vão entre aspas nos comandos, por exemplo `/manhunt create "Caçador%" 5` e
`/manhunt group "Caçador%" role escape`. O Tab já completa os nomes com as aspas.

Os nomes antigos das opções (`starttime`, `glowing`, `nodes`, `min_sec_range_nether_entering`, `botuseelytra`...)
continuam valendo no `/manhunt settings`, mas não aparecem no Tab.

## O que dá para configurar

Tudo está comentado no `config.yml` e pode ser mudado no jogo. Destaques:

- **Roda e contagem antes da caçada** (`manhunt.contagem-segundos`, padrão 20): a roda não tem dono. Os
  bots esperam nela até um jogador que estava dentro sair (vale o primeiro); aí começa a contagem de
  vantagem, com barra no topo e títulos 3-2-1, e só então eles começam a caçar. Roda criada longe de todos
  espera alguém entrar e sair (ou o `/manhunt start`). Cada roda tem a sua contagem, e a roda é salva se o
  servidor reiniciar. Com `manhunt.contagem-ao-sair-da-roda: false` a contagem começa assim que os bots
  são criados; `0` = os bots soltam assim que alguém sai da roda.
- **Raio e centro da roda**: `manhunt.raio-roda` (0 = automático, cresce com a quantidade de bots) e
  `manhunt.centro-roda` (`automatico` = onde a roda é criada; `manual` = no ponto marcado com `/manhunt ring point`).
- **Quem entra no meio da caçada** (`manhunt.quem-entra-no-meio`): `protegido` (padrão) fica sem ser alvo
  pelo tempo da contagem da roda, no mínimo 5 s; `cacado` vira alvo na hora; `fora` não é caçado até a
  próxima caçada. Quem já estava na caçada, cai e volta continua sendo caçado.
- **Lista de jogadores** (`manhunt.lista-modo` e `/manhunt players`): `permitidos` (allowlist) = só os da lista são caçados e
  disparam a roda; `bloqueados` (blocklist) = os da lista não são caçados nem disparam a roda. O `/manhunt target` passa por cima.
- **Brilho** nos caçadores (`aparencia.brilho-cacadores`, com cor) e, separado, nos fugitivos. Vem desligado;
  liga e troca a cor direto no `/manhunt menu` (Configurações) ou com `/manhunt settings glowhunters true red`.
- **Fidelidade de jogador**: bots no TAB com skin e ping, contando no número de jogadores da lista de
  servidores e no `/list`, mensagens "entrou/saiu do jogo" e mensagens de morte do jogo.
- **Mobs hostis atacam os bots** (zumbis, esqueletos, creepers, aranhas no escuro, slimes por contato,
  piglins ignoram quem usa ouro...), com lista de exceções.
- Combate, mobilidade, pathfinding (A* ou direto), táticas, persistência, dificuldade padrão...

## Limitações

- Outros plugins que procuram "jogadores" (por exemplo a bússola de outro plugin de ManHunt) não enxergam
  os bots: eles aparecem no TAB, no `/list` e na contagem, mas não são jogadores para a API do servidor.
- Nomes de bots e grupos no menu são digitados no chat.
- As linhas de debug do A* são partículas (`/manhunt settings pathdebug true`), visíveis para quem tem `manhuntstar.admin`.
- A lista do TAB usa os pacotes internos do servidor; se uma versão futura do Paper mudar esse pacote,
  só essa função se desliga (com aviso no console) e o resto do plugin continua.

## Detalhes do plugin

- **Mira**: besta, arco, tridente e besta com fogos calculam o ângulo pela física real do projétil no servidor e
  antecipam o alvo pelo tempo de voo.
- **Besta com fogos**: o foguete sai a 1,6 bloco/tick, como na besta do jogo, com duração de voo 3 (explode
  entre 40 e 51 ticks, uns 64 blocos). O bot só
  atira se o foguete chega ao jogador antes de explodir e sem bloco no caminho; com a besta carregada, espera
  até 1 s o jogador entrar no alcance e, se não entrar, desiste sem atirar.
- **Cavalo**: no jogo só um jogador comanda o cavalo; com o bot montado, a IA do cavalo é desligada enquanto ele
  cavalga (o cavalo vira para onde o bot olha, galopa, pula obstáculos e vãos, boia na água) e religada quando ele desce.
- **Lança a cavalo**: montado, o bot segura a lança do nível da fase e faz a carga do jogo contra o alvo (o dano vem
  da velocidade do cavalo e a galope derruba quem estiver montado). Ele passa pelo alvo, segue reto, dá a volta e
  carrega de novo; depois de 3 passadas sem acertar, desce e luta com a espada. A pé não usa lança
  (`combate.lanca-a-cavalo`).
- **Barco no caminho**: o A* compara atravessar um lago de barco com dar a volta a pé. Quando a travessia
  compensa, o bot coloca um barco na beira, rema até a outra margem e quebra o barco ao descer
  (`mobilidade.barcos`).
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

O jar sai em `build/libs/ManhuntStar-1.5.2.jar`. O `build` também roda os testes e compila o código contra a
API do Paper 26.3 (`compileAgainst263`) para avisar de quebras de compatibilidade.

Diagnóstico em servidor de teste (flags de JVM): `-Dmanhuntstar.teste.mira=true` registra no console o acerto ou
erro de cada projétil dos bots; `-Dmanhuntstar.teste.cavalo=true` mostra detalhes do cavalo no `/manhunt info`.

## Créditos

Baseado no mod Fabric **T\* (Terminator Star)** de **OpaleMC**, licença CC0: o comportamento foi estudado
no mod e reescrito do zero para a API do Paper, com correções e otimizações.
