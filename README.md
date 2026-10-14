# Ponto PJ

App Android para PJ controlar entrada e saída, ganhos por hora e relatório mensal.
Kotlin + Jetpack Compose, tema preto com amarelo escuro.

## O que o app faz

- **Ponto**: um toque registra entrada e saída, com cronômetro ao vivo e ganho do dia em tempo real.
- **Localização em tempo real**: mostra endereço, coordenadas e precisão; grava onde você bateu o ponto.
- **Valor da hora**: configurável em Ajustes. Cada registro guarda o valor da época, então mudar o valor não altera o histórico.
- **Dashboard**: ganhos do mês e meta, comparação com o mesmo período do mês anterior, últimos 7 dias, horas por dia da semana, médias, melhor dia, projeção do mês, entrada e saída médias.
- **Relatórios**: ganhos mês a mês, comparativo de 6 meses, detalhe dia a dia e botão para compartilhar o relatório em texto.
- **Lançamento manual**: para esquecimentos. Toque em um registro para editar ou excluir.

Os dados ficam só no celular.

## Como gerar o APK

### Opção 1: Android Studio (mais simples)

1. Instale o Android Studio e abra a pasta `PontoPJ`.
2. Espere o Gradle sincronizar.
3. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção 2: GitHub Actions (sem instalar nada)

1. Crie um repositório no GitHub e envie esta pasta para a branch `main`.
2. Abra a aba **Actions**, rode **Gerar APK** (ou espere o push disparar).
3. Baixe `PontoPJ-apk` em **Artifacts** e extraia o `app-debug.apk`.

### Opção 3: Linha de comando

Com JDK 17 e o Android SDK instalados:

```
./gradlew assembleDebug
```

## Instalar no celular

1. Copie o `app-debug.apk` para o aparelho.
2. Abra o arquivo e permita "instalar apps desconhecidos" para o app usado para abrir (Arquivos, Chrome, etc.).
3. Ao abrir o Ponto PJ, permita a localização.

O APK é assinado com a chave de debug, o que basta para uso pessoal.
