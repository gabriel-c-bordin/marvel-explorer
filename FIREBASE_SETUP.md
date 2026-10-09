# Configurar o perfil Firebase

O app usa Firebase Authentication para obter um UID e Cloud Firestore para salvar o perfil em `users/{uid}`. Sem configuração do projeto Firebase, o perfil e a progressão continuam guardados localmente.

## Preparar o projeto

1. Crie um projeto no Firebase Console e registre um app Android com o application ID `com.marvelexplorer`.
2. Baixe o `google-services.json` desse app e coloque-o em `app/google-services.json`. Esse arquivo é ignorado pelo Git.
3. Em **Authentication > Sign-in method**, habilite **Anonymous**. O app inicia uma sessão anônima persistente no aparelho.
4. Crie o banco Cloud Firestore em modo nativo.
5. Publique as regras de [`firestore.rules`](firestore.rules) no console Firebase.
6. Sincronize o Gradle e execute o app com Internet.

O Gradle aplica o plugin Google Services somente quando `app/google-services.json` está presente. Os IDs, regras e conexões reais ainda precisam ser fornecidos e configurados no Console.

## Dados e fotos

Nome, título, raça/espécie, equipe, URL futura da foto, XP, missões concluídas e conquistas são salvos no documento do UID autenticado. O Firestore mantém escritas locais pendentes quando o aparelho está offline; o perfil também guarda um cache local.

Fotos escolhidas da galeria e tiradas pela câmera ficam no aparelho por enquanto. O campo `photoUrl` está reservado para a URL HTTPS retornada pelo Cloudinary. O envio para Cloudinary ainda não está implementado; o Firestore não armazena os bytes da imagem.

O UID anônimo permite separar dados por instalação e restaurar a sessão no mesmo aparelho. Para transferir perfil entre aparelhos ou recuperar uma conta após reinstalar, será preciso vincular o UID anônimo a um método de login, como e-mail ou Google.

As regras propostas impedem um usuário autenticado de ler ou escrever o documento de outro UID. Como o XP é enviado pelo app cliente, um usuário tecnicamente avançado ainda pode alterar o próprio progresso; XP inviolável exige validar recompensas em backend/Cloud Functions.
