# Marvel Explorer

App Android acadêmico para explorar personagens e outros registros da Comic Vine API.

## Abrir no Android Studio

Abra a pasta `C:\Users\gabrielbordin-ieg\AndroidStudioProjects\marvelProject` como projeto. Assim como os outros projetos Java do Android Studio, a tela fica em `app/src/main/res/layout`, as classes Java ficam em `app/src/main/java`, e o módulo principal é `app`. Os arquivos `.gradle.kts` são apenas a configuração de build.

## Configuração local da chave

1. Copie `secrets.properties.example` para `secrets.properties` na raiz do projeto.
2. Preencha `comicvine.apiKey=SUA_CHAVE` no arquivo local.
3. Abra a pasta do projeto no Android Studio, sincronize o Gradle e execute o app.

`secrets.properties` está no `.gitignore`; não compartilhe esse arquivo. Uma chave incluída no APK ainda pode ser extraída do aplicativo instalado, então use a chave pessoal apenas para este protótipo acadêmico.

## O que já está implementado

- Busca de personagens via `GET /characters/`, em JSON.
- Tela de exploração para `/teams/`, `/volumes/`, `/story_arcs/`, `/issues/`, `/publishers/`, `/powers/`, `/movies/` e `/locations/`.
- Equipes, volumes e arcos são filtrados localmente pela editora Marvel. Os demais recursos aparecem como busca geral da Comic Vine, porque a listagem não fornece um vínculo com a editora que permita garantir o filtro.
- Seleção de campos e paginação com `limit` e `offset`.
- Cabeçalho `User-Agent` descritivo.
- Intervalo mínimo de 1,1 segundo entre requisições à API para reduzir rajadas.
- Identificação local de registros cuja editora é Marvel (ID `4010-31` ou nome `Marvel` / `Marvel Comics`).
- Lista com imagem quando a API fornece uma URL acessível; ficha com dados opcionais.
- Estados de chave ausente, carregamento, resultado vazio e erro.
- Cache local persistente por seção/consulta/página, válido por 6 horas. Se a API estiver indisponível, uma cópia de até 7 dias pode ser exibida. Cada cache é limitado a 40 páginas e não armazena a API key.
- Link de atribuição à Comic Vine visível no rodapé do app.

## NEXUS, IA local e sons

O app agora oferece o assistente NEXUS com ML Kit GenAI Prompt API e Gemini Nano no próprio aparelho, quando o Android AICore disponibiliza o modelo. A conversa curta é mantida pela tela e o contexto de quadrinhos vem das buscas à Comic Vine. A IA não exige uma chave de provedor e o prompt da conversa fica no aparelho; quando a chave Comic Vine está configurada, o termo de busca extraído da pergunta pode ser enviado à Comic Vine para localizar fatos.

Gemini Nano não está disponível em todos os aparelhos. Quando o modelo pode ser instalado, a tela oferece o botão **Preparar IA local** e só inicia o download depois do toque do usuário. Quando o modelo não está pronto ou não é compatível, o assistente muda para consulta guiada à Comic Vine e mostra os registros encontrados sem apresentar texto fixo como se tivesse sido gerado por IA.

Os efeitos de abertura, varredura, confirmação e alerta são sintetizados localmente; não há arquivos de áudio externos. O controle SFX está disponível nas telas e salva a preferência no aparelho. A interface usa fundo navy, painéis técnicos, ciano e vermelho Marvel.

Referências: [ML Kit Prompt API](https://developers.google.com/ml-kit/genai/prompt/android), [guia de configuração e exemplo Java](https://developers.google.com/ml-kit/genai/prompt/android/get-started) e [compatibilidade de dispositivos](https://developers.google.com/ml-kit/genai).

## Situação do desenvolvimento

A estrutura Android e o fluxo de busca estão montados. A integração real ainda depende de preencher a chave local e executar em um dispositivo/emulador com Internet. A chave não foi fornecida nem armazenada neste projeto.

Na revisão de 29/09/2026, os fontes Java compilaram contra o SDK Android 36, e os recursos XML foram compilados pela ferramenta do Android SDK. A compilação Android completa pelo Gradle não avançou porque o processo não conseguiu abrir a conexão local exigida pelo Gradle. Ainda não houve chamada autenticada nem execução em dispositivo: não existe `secrets.properties` nem dispositivo/emulador conectado. Portanto, os novos endpoints estão implementados, mas as respostas reais ainda não foram confirmadas.

## Observação

Este app usa a Comic Vine API, não uma API oficial da Marvel. Os campos da resposta podem estar ausentes; a interface trata esses dados como opcionais. Revise os termos de uso e mantenha atribuição à Comic Vine na versão de entrega.
