# JHON — Agente criador de jogos

O repositório Jhon- contém o aplicativo Android JHON Game Studio e o **estúdio de desenvolvimento** `games/aetheria/`.

## Caminho de execução
A interface Android coleta texto/voz e abre a criação de Issue no GitHub. Após o usuário confirmar o envio, a automação `jhon-coder.yml` reage às Issues `[JHON]` criadas pela conta proprietária e usa **GitHub Models** com a permissão `models: read` (cota sujeita às regras do GitHub).

O agente gera **arquivos de código reais** apenas dentro da pasta `games/aetheria/` e abre proposta de alteração (PR). O commit é versionado numa branch `jhon/issue-N-RUN`. O trabalho não é concluído até revisão e testes. Não há autonomia ilimitada sobre aplicativos externos nem acesso a repositórios que não tenham sido autorizados.

Jogo principal: **Aetheria — Ecos da Aurora**. A ordem completa está na Issue #1. A documentação preserva a direção aprovada. As malhas procedurais do primeiro teste são **temporárias**, não a meta artística final. Nunca afirmar que o resultado experimental reproduz artes cinematográficas.

## Gatilhos e acompanhamento
1. Issue nova com título começando `[JHON]` criada pelo proprietário: aciona a automação.
2. Issue antiga: adicionar label `jhon-auto` para acioná-la.
3. Execução manual: Actions > JHON agente de programação > Run workflow > número da Issue.
4. Ver progresso no Actions e revisar Pull Requests. Se o GitHub bloquear PR automático, o workflow deixa a branch no repositório e informa um link de comparação.

## Integração e limites
O token temporário do GitHub Actions é o usado para acessar os modelos — nenhuma senha ou chave secreta é incluída no APK.
A execução automática depende da cota do GitHub Models e das permissões de Actions/PR no repositório. Não garante que toda tarefa terá uma solução perfeita ou automática. Para edição/compilação completa de um jogo AAA serão necessários assets profissionais e QA em Android.

## Importante
O aplicativo JHON v2.0.1 já compilado é uma interface de voz/texto que **abre** Issues; não faz inferência de IA no dispositivo. O agente reside no servidor GitHub Actions, não no APK.
