# Merlin 1.7 — arquivos originais e downloads permitidos

## Inclusões desta versão
- **Biblioteca > Importar:** selecionar músicas e vídeos (MP3, AAC, FLAC, M4A, OGG, WAV, MP4 etc.) já baixados no Snaptube ou salvos no telefone.
- **Biblioteca > Baixar áudio/vídeo autorizado:** colar endereço **HTTPS direto do arquivo**. O Android abre um seletor para escolher onde salvar sem acesso irrestrito ao armazenamento. O tipo recebido é conferido e o arquivo é importado para a biblioteca quando o download termina.
- **Compartilhamento:** o código já contém um importador que copia mídia recebida para armazenamento privado, protegendo acesso futuro. Porém o registro do Merlin no menu Compartilhar de outros aplicativos ainda precisa ser configurado no AndroidManifest e validado; **não considere o compartilhamento direto disponível nesta compilação**.
- Preferências, curtidas, playlists pessoais, Match, reprodução local e integrações existentes permanecem sem migração de banco de dados.

## O que este recurso NÃO faz
- Não conecta ao catálogo interno do Snaptube nem baixa do YouTube. Não converte links de páginas ou extrai faixas de vídeo, não ignora DRM ou anúncios de terceiros.
- O usuário deve possuir autorização para baixar o arquivo indicado e escolher o destino no seletor Android.
- A autorização de leitura ampla da biblioteca pode ser negada; a importação manual usa o seletor do sistema e continua disponível.

## Build
Java 17, Android SDK 35, Gradle 8.10.2.
# MERLIN — Player Android (versão 1.6.1)

O Merlin permanece no projeto Android Java original. Os dados existentes ficam nas mesmas chaves de SharedPreferences e o applicationId continua **com.merlin.player**.

## O que funciona pelo código
- Biblioteca local: arquivos que o Android identifica no armazenamento, com importação manual de áudio (e MP4 com faixa de áudio). O Snaptube é uma fonte de arquivos locais, **não um catálogo de streaming**.
- Reprodução local/segundo plano, MediaSession, avançar/voltar, fila, aleatório e repetição.
- Busca por músicas locais e música online via **Audius** (catálogo aberto) e, com client_id configurado, **Jamendo**.
- Somente músicas com endpoint de streaming autorizado aparecem como resultados reproduzíveis. Músicas comerciais indisponíveis não são prometidas.
- Playlists e favoritos locais. Backup de biblioteca nas configurações.
- Playlists colaborativas e Match implementados com backend Firebase opcional, confirmação de ações e convites por link/código. Sem Firebase configurado não sincronizam.
- Tema escuro com destaque violeta, painel Criar, capas e miniplayer.

## Configuração Jamendo (opcional, mas recomendada)
1. Crie uma conta de desenvolvedor em https://developer.jamendo.com/ e obtenha seu client_id para a API de leitura (o ID de exemplo na documentação é **só para testes**).
2. GitHub > Settings > Secrets and variables > Actions > Variables: crie `MERLIN_JAMENDO_CLIENT_ID` com o seu client_id.
3. Execute novamente `.github/workflows/merlin-build-public.yml` (workflow na branch main).
4. A API do Jamendo autoriza busca e reprodução de obras disponíveis nela. **Não** garante Taylor Swift nem outras faixas que não existam naquele catálogo.

O Audius oferece acesso de leitura sem autenticação na maioria dos endpoints, mas pode impor limites e a disponibilidade das faixas depende da API.

## Configuração da colaboração e Match
Veja [FIREBASE-SETUP.md](FIREBASE-SETUP.md). É necessário configurar o Firebase Realtime Database + Authentication, aplicar as regras de segurança e criar as variáveis do GitHub.
Os áudios **não são compartilhados**: sincronizam-se metadados de títulos e artistas. Cada pessoa precisa possuir sua própria cópia da música ou o streaming autorizado.

## Importar músicas do Snaptube
Use **Biblioteca > Importar**. Escolha os arquivos de áudio ou MP4 com áudio no seletor Android. Não é necessário conceder acesso ao Snaptube nem suas credenciais.

## Desenvolvimento e testes
- Java 17, Gradle 8.10.2, Android SDK 35.
- `gradle -p Merlin testDebugUnitTest assembleDebug`
- GitHub Actions executa testes, compila o APK, verifica a assinatura e publica um arquivo de teste na seção Releases.
- **Os testes automáticos não substituem testes reais em um Galaxy A15**, especialmente para streaming pela internet e convites entre dispositivos.

## IMPORTANTE: Atualização sem perder dados
Um APK só instala por cima do aplicativo atual quando ambos usam a mesma chave de assinatura e um versionCode maior. Os APKs de debug gerados em máquinas diferentes podem ter assinaturas incompatíveis. **Antes de tentar atualizar**, faça backup no Merlin em Configurações > Exportar backup. Se o Android exigir desinstalar, não faça isso antes de salvar o backup e confirmar que consegue restaurá-lo. Para atualizações futuras sem perda de dados, é preciso configurar uma chave de assinatura persistente e protegida para o workflow.

## Limites
- O Merlin não desbloqueia Spotify Premium, não retira anúncios do YouTube e não reproduz o áudio isolado de vídeos do YouTube.
- Sem anúncios próprios inseridos no Merlin; outras plataformas mantêm suas próprias regras.
- Busca online e reprodução precisam de rede, dependem do serviço e podem falhar. O app deve mostrar erro em vez de simular que a faixa está tocando.
