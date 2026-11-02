# Merlin 1.4 — Playlists e Match com Firebase

## Funcionalidades de código
- Playlists pessoais: criar, renomear, excluir com confirmação; adicionar ou remover músicas.
- Colaborativas: convites aleatórios de 160 bits, até 10 amigos mais o criador, alterações de faixas guardadas no servidor e consultas periódicas para sincronização.
- Match musical: convidados compartilham voluntariamente até 50 músicas curtidas; o aplicativo calcula recomendações coletivas por votos e artistas e permite publicar a playlist compartilhada.
- Menu Criar com três opções, ações persistentes e mensagens de confirmação.
- Arquivos originais do Snaptube permanecem no celular de cada usuário. O servidor armazena metadados, não arquivos de áudio.

## Ativação necessária para colaboração real
**O APK sem backend configurado não sincroniza entre celulares.**
1. Em https://console.firebase.google.com crie um projeto Firebase seu.
2. Em Authentication > Sign-in method, habilite autenticação Anonymous.
3. Crie o Realtime Database.
4. Em Database > Rules, publique o conteúdo de firebase-database.rules.json deste diretório.
5. Copie a Web API Key do Firebase e o URL do seu Realtime Database.
6. Configure GitHub Actions VARIABLES (Settings > Secrets and variables > Actions > Variables):
   MERLIN_FIREBASE_API_KEY = sua Web API Key
   MERLIN_FIREBASE_DATABASE_URL = endereço https://...firebasedatabase.app ou ...firebaseio.com
7. Execute o workflow Merlin 1.4 APK em Actions. Ele incorpora os dois valores de configuração e gera o APK com colaboração habilitada.
8. Teste convites e sincronização com 2 celulares antes de compartilhar.

## Funcionamento e limites
- Login anônimo: perder dados do app pode perder a propriedade de salas. Não remova os dados do app sem ter salvo convites.
- Convites expiram em 7 dias. O limite é 11 participantes por sala.
- O app atualiza as salas enquanto está aberto (polling), não envia notificações em tempo real quando fechado.
- O Match respeita consentimento: somente curtidas selecionadas são publicadas como metadados.
- Música encontrada em outra sala só toca se um arquivo correspondente estiver no celular do participante.
- Esta integração não concede Spotify Premium nem oferece músicas licenciadas sem autorização.
- As regras Firebase são necessárias: não configure leitura/escrita pública.
