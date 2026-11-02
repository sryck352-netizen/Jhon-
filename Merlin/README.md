# Merlin — tocador de música para Android

Aplicativo Android em Java, com visual escuro e destaque roxo, focado em música offline. É compatível com arquivos de áudio acessíveis no armazenamento do celular, incluindo aqueles baixados pelo Snaptube, **sem depender de uma API não oficial do Snaptube**.

## Funcionalidades

- Biblioteca de músicas do aparelho com busca por faixa e artista.
- Importar várias músicas (MP3, AAC, M4A, FLAC, OGG e outros formatos que o Android reproduzir) pelo seletor de arquivos.
- Permissões modernas do Android 13+ (`READ_MEDIA_AUDIO`).
- Reprodução em segundo plano, com a tela bloqueada, e controles de mídia do Android.
- Playlists personalizadas: toque em `+ Playlist` para criar e **segure uma faixa** para incluí-la ou removê-la de uma playlist.
- Favoritos, modo aleatório, repetir todas ou repetir uma, avanço e retrocesso, e barra para avançar na música.
- Downloads do Snaptube que o Android indexar são exibidos automaticamente. Se não aparecerem, use **Importar** para escolher os arquivos.

## Limite da integração

O Merlin toca músicas baixadas, próprias ou licenciadas, que estejam acessíveis ao usuário. Não transmite o catálogo interno online do Snaptube nem faz download de serviços protegidos: isso exigiria integração oficial ou acesso autorizado a uma fonte de áudio.

## Como gerar o APK

No computador, instale Java 17, Android SDK 35 e Gradle 8.10.2. Na pasta do projeto, rode:

```bash
gradle assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Também existe um workflow de GitHub Actions em `.github/workflows/build-apk.yml` para compilar automaticamente o APK ao enviar o código para uma branch chamada `merlin`.

Instale apenas seus próprios APKs compilados em ambientes confiáveis. O APK de debug é adequado para testes, não para publicação na Play Store.
