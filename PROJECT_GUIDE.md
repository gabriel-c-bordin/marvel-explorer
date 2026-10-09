# Marvel Mobile Challenge — Guia do Projeto

> Documento de produto e implementação baseado em `21_Desafio_Marvel.html`. A seção de estrutura abaixo descreve o projeto Android atual.

## 1. Visão do produto

Criar um aplicativo Android de exploração do universo Marvel usando dados reais da **Comic Vine API**. A ideia do desafio é ir além de uma lista simples: oferecer pesquisa, filtros, paginação e páginas de detalhe com contexto de personagens, equipes, edições e arcos de história.

O acervo da Comic Vine cobre quadrinhos em geral e não apenas Marvel. O aplicativo deve filtrar ou identificar os registros associados à editora Marvel e comunicar quando algum dado não estiver disponível.

### Proposta de diferencial

Construir um **dossiê navegável do universo Marvel**: começar por um personagem, equipe ou arco e percorrer relações entre eles (membros, poderes, aparições, edições e histórias). Isso transforma a consulta à API numa experiência de descoberta. A API fornece dados; a curadoria da jornada, os filtros úteis e a forma de apresentar essas relações são responsabilidade do app.

## 2. Regras de negócio e produto

### Regras exigidas ou descritas no desafio

1. O aplicativo é mobile e consome a Comic Vine API em `https://comicvine.gamespot.com/api/`.
2. Cada aluno deve obter sua própria `api_key` na Comic Vine.
3. As chamadas usam `format=json` e um cabeçalho `User-Agent` identificável para o aplicativo.
4. O conteúdo principal deve focar no universo Marvel. O identificador de editora indicado no HTML é `4010-31` (Marvel); confirme o resultado ao integrar a API.
5. A busca deve aproveitar os dados da API. O desafio sugere busca, filtros, paginação, imagens e telas de detalhes ricas.
6. A entrega indicada é o projeto compactado e um vídeo de apresentação anônimo. A data indicada no HTML é outubro de 2026.
7. Os colegas avaliam experiência de uso, design visual, uso da API, criatividade, funcionamento e apresentação. O desafio prevê reconhecimento para os quatro primeiros lugares.

### Regras de funcionamento recomendadas

- Consultar somente os campos necessários com `field_list` e limitar o tamanho de cada página (`limit`/`offset`) para reduzir tráfego e tamanho das respostas.
- Tratar busca vazia, resultados vazios, dados parciais, falha de rede, bloqueio/limite da API e erro de resposta com estados claros na interface.
- Não presumir que campos opcionais — imagem, nome real, poderes, editora, primeira aparição, relações — sempre existam. Mostrar um estado alternativo quando estiverem ausentes.
- Distinguir carregamento inicial de carregamento de próxima página; evitar duplicar itens ao paginar.
- Preservar o contexto do usuário ao abrir e fechar detalhes e permitir voltar à lista sem perder busca/filtros/página.
- Evitar consultas repetidas desnecessárias. Reutilizar resultados em cache local por consulta/página: frescos por 6 horas, disponíveis como contingência por até 7 dias, com limite de 40 páginas. A chave da API não é armazenada no cache. Espaçar chamadas remotas para evitar rajadas.
- Não usar os dados como se fossem oficiais da Marvel. A fonte é a Comic Vine, uma base comunitária de quadrinhos.
- Mostrar atribuição/link para a Comic Vine nas telas que usam seus dados, conforme os termos publicados pela API.

## 3. API: integração e cuidados

### Recursos mencionados no HTML

| Recurso | Caminho de lista | Uso sugerido no app |
|---|---|---|
| Personagens | `/characters/` | Busca, ficha, poderes, editora, aparições e relações |
| Equipes | `/teams/` | Equipes, membros e histórico |
| Edições | `/issues/` | Capa, número, data, personagens e créditos |
| Volumes | `/volumes/` | Séries e suas edições |
| Arcos | `/story_arcs/` | Histórias e edições relacionadas |
| Editoras | `/publishers/` | Identificar e explorar Marvel |
| Poderes | `/powers/` | Descobrir personagens por poder |
| Localizações e filmes | `/locations/`, `/movies/` | Conteúdo complementar, se fizer sentido no escopo |

Para um registro individual, a API também usa caminhos singulares com o tipo e o identificador, por exemplo `/publisher/4010-31/`. Os recursos efetivamente disponíveis e seus campos devem ser verificados na documentação/lista oficial antes de implementar cada tela.

### Formato de chamada

O HTML apresenta chamadas `GET` com parâmetros de consulta:

```text
https://comicvine.gamespot.com/api/characters/?api_key=SUA_CHAVE&format=json&filter=name:Wolverine&field_list=id,name,real_name,image,deck,powers,publisher&limit=20&offset=0
```

- `api_key`: chave pessoal emitida pela Comic Vine.
- `format=json`: seleciona JSON.
- `filter`: filtra por campos aceitos pelo recurso; não presuma que qualquer combinação de campos/relacionamentos seja suportada.
- `field_list`: reduz a resposta aos campos usados na tela.
- `limit` e `offset`: paginação ilustrada no exemplo Kotlin do desafio.
- `User-Agent`: cabeçalho descritivo, por exemplo `MarvelExplorerAndroid/1.0 (projeto acadêmico)`.

O HTML sugere filtrar Marvel no cliente verificando `publisher.name`, e apresenta o publisher ID `4010-31`. Use isso como ponto de partida, mas confirme o formato real de `publisher` na resposta. O exemplo de Kotlin no HTML é ilustrativo e incompleto: os tipos `CharacterResponse`, `TeamResponse` e `StoryArcResponse` precisam ser definidos, e devem existir conversão de respostas, estados de erro e gerenciamento seguro de configuração.

### Chave e termos de uso

- Uma chave embutida num APK pode ser extraída. Não a publique em repositório, capturas ou vídeo; mantenha-a fora do controle de versão. `BuildConfig`/arquivo local evita exposição acidental no Git, mas não torna a chave secreta dentro do aplicativo instalado.
- Para um protótipo acadêmico, chamadas diretas podem ser suficientes se a chave individual for mantida fora do repositório. Para distribuir publicamente ou proteger melhor a chave, avalie um serviço intermediário controlado pelo projeto.
- A página oficial da API informa uso **não comercial**, limite oficial de **200 requisições por recurso por hora**, bloqueio temporário por velocidade excessiva e recomenda cache para evitar chamadas duplicadas. O serviço pode recusar acesso.
- A página oficial também solicita crédito/link para a Comic Vine e restringe redistribuição dos dados em outro meio e reprodução/manipulação do conteúdo. Revise os termos antes de compartilhar ou publicar o app.

## 4. Experiência e telas

O HTML traz dois exemplos visuais: listagem de personagens e detalhes de um personagem. Recomenda-se esta navegação mínima:

1. **Início / descoberta** — proposta do app, destaques e atalhos para personagens, equipes e arcos.
2. **Explorar** — busca, categorias/filtros e lista paginada com imagem e dados curtos.
3. **Detalhe** — imagem, nome, nome real, resumo, editora, primeira aparição e aparições, quando fornecidos.
4. **Relações** — poderes, equipes, amigos/inimigos, edições ou arcos associados, apenas quando a resposta tiver dados utilizáveis.
5. **Sobre e fontes** — atribuição visível à Comic Vine e explicação de que o catálogo pode conter lacunas.

Filtros de categoria como “Heróis” e “Vilões” aparecem na referência visual do HTML, mas não são apresentados como classificação garantida da API. Só ofereça esses filtros se houver campo confiável para implementá-los; não derive uma classificação sem regra explícita.

## 5. Estrutura técnica atual

Projeto Android Studio em **Java**, com telas em XML, módulo `app` e configuração Gradle em Kotlin DSL. Os arquivos `.gradle.kts` configuram a compilação; o código do aplicativo é Java. A integração usa `HttpURLConnection` e o cache usa `SharedPreferences`.

```text
marvelProject/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/marvelexplorer/
│       │   ├── MainActivity.java                # personagens
│       │   ├── CatalogActivity.java             # outros recursos da API
│       │   ├── AssistantActivity.java            # chat NEXUS e exploração guiada
│       │   ├── ThemeSoundPlayer.java             # efeitos sonoros sintetizados
│       │   └── data/
│       │       ├── CharacterItem.java           # dados de personagem
│       │       ├── CatalogItem.java             # dados das outras seções
│       │       ├── ComicVineService.java        # endpoint de personagens
│       │       ├── ComicVineCatalogService.java # endpoints do catálogo
│       │       ├── ComicVineRepository.java     # consulta de personagens
│       │       ├── CatalogRepository.java       # consulta do catálogo
│       │       ├── CharacterPageCache.java      # cache de personagens
│       │       ├── ComicVineRequestThrottle.java # espaçamento entre chamadas
│       │       ├── OnDeviceMarvelAssistant.java  # Gemini Nano via ML Kit Prompt API
│       │       └── ImageDownloader.java         # carregamento de imagens
│       └── res/
│           ├── layout/                     # tela, cartão e diálogo de detalhes
│           ├── drawable/                   # fundos dos elementos
│           └── values/                     # strings, cores e estilos
├── gradle/                                  # wrapper Gradle
├── .gitignore
├── README.md
├── secrets.properties.example
└── local.properties                       # local; nunca versionar
```

O serviço concentra URL, cabeçalhos, resposta JSON e filtro Marvel. O repositório coordena a chamada e o cache. A atividade usa esses dados para preencher as telas XML, sem montar URLs da API.

## 6. Plano de trabalho

### Status em 29/09/2026

- [x] Criada a estrutura inicial do app Android, sem alterar os outros projetos do computador.
- [x] Implementados chamada HTTP, parsing dos campos, filtro de editora Marvel quando disponível, cache local e telas de busca, listagem e detalhe para personagens e outras seções.
- [x] Adicionados os outros oito endpoints de listagem citados no HTML do desafio; edições, editoras, poderes, filmes e locais são buscas gerais e identificadas como tal na tela.
- [x] Implementados cache com validade/contingência offline, espaçamento entre chamadas e link de atribuição à Comic Vine.
- [x] Fontes Java compilam contra o Android SDK 36; layouts, estilos e demais recursos passaram pela ferramenta de recursos do Android SDK.
- [ ] Configurar a API key local e confirmar uma chamada real no dispositivo/emulador; ainda não há chave neste diretório.
- [ ] Completar `assembleDebug` e conferir a interface em execução; o Gradle está bloqueado neste ambiente por falha ao abrir conexão local.
- [ ] Ajustar os modelos/filtros com base na resposta real da API.
- [ ] Fazer polimento visual depois que os dados reais forem confirmados.

### Fase 1 — Base

- [x] Criar/escolher o projeto Android e registrar versão mínima, pacote e configuração de build.
- [ ] Definir a ideia final e o fluxo principal do usuário.
- [ ] Obter chave individual; o mecanismo local de injeção no build já está configurado.
- [ ] Validar uma chamada de personagens e uma de publisher; inspecionar os campos e envelope JSON reais.
- [x] Definir modelo mínimo de personagem e tratamento de erro.

### Fase 2 — MVP

- [x] Implementar serviço Comic Vine com `User-Agent`, JSON, seleção de campos e parâmetros de paginação.
- [x] Implementar repositório e busca de personagens.
- [x] Filtrar/identificar Marvel conforme o publisher informado; validar com uma resposta real quando houver chave.
- [x] Criar listagem com busca, carregamento, erro, vazio e paginação.
- [x] Criar detalhes com campos opcionais e atribuição à fonte.
- [x] Permitir abrir e fechar detalhes mantendo a lista e a busca.

### Fase 3 — Diferencial e acabamento (atualizado 05/10/2026)

- [ ] Implementar a jornada de descoberta por relações (personagem ↔ poder/equipe/edição/arco), se os dados e limites da API permitirem.
- [x] Acrescentar equipes, volumes e arcos como segunda área de exploração, com outras listas da Comic Vine.
- [ ] Criar favoritos locais (funcionam sem novas chamadas à API).
- [ ] Refinar identidade visual, acessibilidade, estados sem imagem e layouts para diferentes tamanhos de tela.
- [ ] Revisar uso de requisições e evitar chamadas repetidas; tratar indisponibilidade e limites.
- [x] Criado o assistente conversacional NEXUS com suporte a Gemini Nano local via ML Kit Prompt API; o histórico curto é enviado no prompt para dar continuidade à conversa.
- [x] Consultas do assistente tentam buscar contexto de personagens, equipes, arcos ou edições na Comic Vine e limitam a resposta a dados disponíveis. Sem registros ou modelo local, informa a limitação em vez de simular uma resposta gerada.
- [x] Adicionados efeitos de interface sintetizados localmente e controle SFX persistente, sem dependência de assets externos.
- [x] Aplicada identidade visual sci-fi em navy/ciano/vermelho Marvel nas telas principais, catálogo, detalhes e assistente. A renderização ainda precisa ser conferida no dispositivo.
- [ ] Confirmar sincronização Gradle, compilação, renderização e inferência Gemini Nano em dispositivo compatível. A Prompt API é beta e Gemini Nano só está disponível em parte dos dispositivos Android.

### Fase 4 — Entrega

- [ ] Fazer uma rodada manual dos fluxos principais e das falhas de rede/API.
- [ ] Remover chaves e dados locais do pacote/fonte compartilhados.
- [x] Atualizar README com configuração, chave, execução, limites, fonte de dados, assistente local e efeitos sonoros.
- [ ] Gerar o pacote compactado do projeto conforme solicitado.
- [ ] Gravar vídeo de apresentação sem identificar o aluno, conforme o desafio.
- [ ] Conferir prazo/formato de entrega com a disciplina; o HTML indica outubro de 2026.

## 7. Critérios de aceite do MVP

- A pessoa consegue pesquisar personagens Marvel, abrir detalhes e retornar à busca sem perder seu contexto.
- A lista carrega em páginas e não repete itens ao avançar.
- A interface distingue carregamento, vazio, erro e conteúdo parcial.
- A integração envia chave, `format=json` e `User-Agent`, seleciona campos e não faz chamadas em excesso.
- Nenhum fluxo depende da existência de um campo opcional para não quebrar.
- A Comic Vine é identificada como fonte e recebe atribuição/link.
- O projeto compartilhado não contém a chave pessoal.

## 8. Referências e limites desta análise

- HTML do desafio: `C:\Users\gabrielbordin-ieg\AndroidStudioProjects\marvelProject\21_Desafio_Marvel.html`.
- [Comic Vine API — cadastro, documentação e termos de uso](https://comicvine.gamespot.com/api/).
- [Marvel — registro de editora na Comic Vine](https://comicvine.gamespot.com/marvel/4010-31/).
- [Comic Vine API — opções de consulta (fórum oficial)](https://comicvine.gamespot.com/forums/api-developers-2334/query-options-2226634/).

Este guia registra requisitos do HTML e condições publicadas pela Comic Vine, mas não substitui uma chamada de integração: a chave é pessoal e não foi usada. Campos, filtros e disponibilidade devem ser confirmados com respostas reais antes de fechar os modelos e as regras de tela.

