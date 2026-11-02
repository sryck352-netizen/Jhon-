# Merlin 1.5 — continuidade do projeto original

## Mudanças feitas sobre a branch merlin
- AudiusCatalog.java: pesquisa autorizada de músicas Audius. Somente as faixas que a API informa como reproduzíveis entram na fila.
- MusicService.java: MediaPlayer já existente agora trata URLs HTTPS autorizadas do Audius, sem abrir navegador. O Android continua controlando play, pause, seek, fila e segundo plano. Erros de streaming são exibidos.
- Track.java e AudioLibrary.java: preservam metadados e capa das músicas online tocadas. O armazenamento anterior de favoritas, imports e playlists locais NÃO foi reiniciado nem migrado destrutivamente.
- MainActivity.java: mostra resultados reproduzíveis Audius acima dos resultados apenas informativos do iTunes, mantém a busca local; botão play usa o player Merlin.
- CloudPlaylists.java: salas colaborativas existentes mantidas, com SSE do Firebase Realtime Database para sincronização em tempo real enquanto a sala está aberta. Consulta periódica de 8,5 s como fallback. Convites e 10 vagas seguem a regra anterior.
- Match: compartilhar favoritos é opt-in; compartilhar histórico é opção adicional. Compartilhamentos podem ser revogados. Após consentimento, atualiza recomendações e faixas auto-geradas ao mudar preferências. Músicas adicionadas manualmente não são excluídas pelo Match.
- Sincroniza IDs públicos Audius (não URLs de arquivos locais ou arquivos de áudio), permitindo reproduzir faixas Audius das playlists compartilhadas em outro celular.
- Icone: `merlin_icon.base64` substituído pelo arquivo fornecido pelo usuário, redimensionado para 192px JPEG de alta qualidade no build Gradle. Fundo e RM ciano, azul e roxo preservados.

## Ativação real dos grupos e Match
Sem o projeto Firebase do proprietário, as opções colaborativa e Match explicam que não estão ativadas: não fingem sincronização. Configure:
1. Firebase Authentication > Anonymous: ENABLE.
2. Firebase Realtime Database criado.
3. Publicar `firebase-database.rules.json` no Firebase Database > Rules, após revisão para o projeto.
4. GitHub repo Settings > Secrets and variables > Actions > Variables:
   - `MERLIN_FIREBASE_API_KEY`
   - `MERLIN_FIREBASE_DATABASE_URL`
5. Rerun workflow `Merlin Android 1.5`. O APK novo incorpora os parâmetros públicos do seu projeto.
A versão construída sem essas variáveis funcionará offline e com buscas Audius, mas NÃO terá colaboração funcional.

## Testes e limites
- CI deve compilar `assembleDebug`, executar `testDebugUnitTest`, verificar assinatura com `apksigner` e propriedades com `aapt`.
- Testar no Galaxy A15: permissões de áudio, imports Snaptube, reprodução local e Audius, Android em segundo plano, rotatividade, playlists preservadas após atualização, erro de internet sem travamento.
- Testar com 2 dispositivos depois de configurar Firebase, convite, remoção, edição, SSE (altere uma lista enquanto a outra está aberta), limite de 10 convidados, revogação de favoritos/histórico.
- O catálogo do Audius é diferente do Spotify: não há como prometer reprodução de todo o catálogo comercial.
- Conexões de áudio e SSE dependem de disponibilidade de API e conectividade. Não afirmamos teste em aparelho real sem realizá-lo.
- Faixas compartilhadas que são arquivos locais exigem cópia própria do áudio no dispositivo do participante.

## Identidade e compatibilidade
Mantidos applicationId `com.merlin.player`, SharedPreferences `merlin_library`, esquema de playlists pessoais, ícone da marca e minSdk 26. Atualização deve preservar dados **se** APK novo tiver a mesma assinatura do já instalado. Build debug é assinada automaticamente pelo GitHub Runner; assinaturas de diferentes runners podem variar, exigindo uma chave de assinatura de release estável para atualizar sem desinstalar.
