# JHON Game Studio — Android + agente de programação

O **Jhon** é um aplicativo Android para receber comandos de jogos por **texto e voz**, integrado a tarefas no GitHub. A automação de código roda no **GitHub Actions**, não no APK.

## Baixe o Jhon para Android
Versão experimental compilada: [JHON.apk](https://github.com/sryck352-netizen/Jhon-/releases/download/v2.0.1/JHON.apk). A compilação passou; a instalação no Galaxy A15 não foi validada em aparelho físico.

## Aetheria — Ecos da Aurora
- Projeto Godot 4.x (prova técnica 3D): [games/aetheria](games/aetheria).
- Primeira ordem de produção: [Issue #1](https://github.com/sryck352-netizen/Jhon-/issues/1).
- Código 3D inicial importado com sucesso pelo Godot em GitHub Actions. A arte da prova técnica é **provisória**; não representa o gráfico cinematográfico final.

## Ativar a IA do Jhon
O GitHub Models foi encerrado em **30 de julho de 2026**. Para a IA programar de verdade, é preciso conectar um provedor ativo:
1. Crie uma chave da API Gemini em **https://aistudio.google.com/apikey** (opção gratuita com cotas e termos próprios).
2. Acesse **https://github.com/sryck352-netizen/Jhon-/settings/secrets/actions**, clique **New repository secret**, nomeie **GEMINI_API_KEY** e cole a chave no campo **privado**. Nunca publique em arquivo/Issue ou mande no chat.
3. Abra [Actions > JHON agente de programação](https://github.com/sryck352-netizen/Jhon-/actions/workflows/jhon-coder.yml), selecione **Run workflow** e informe Issue **1**.
4. Confira execução e PRs. O agente propõe incrementos de código; a etapa cinematográfica exige modelos/rig/animações reais, testes de dispositivo e aprovação de alterações.

Alternativa: configurar `OPENAI_API_KEY` no lugar de `GEMINI_API_KEY`. A assinatura do ChatGPT não substitui uma chave de API, e uso de API pode ter custo.

**Transparência:** a IA não está ativa enquanto nenhuma das chaves estiver configurada. Não há jogo final nem APK de Aetheria disponível.
