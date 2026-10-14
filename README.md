# Ponto PJ

App Android para PJ controlar entrada e saída, ganhos por hora e relatórios.
Kotlin + Jetpack Compose, tema preto com amarelo escuro.

## O que o app faz

- **Ponto**: um toque registra entrada e saída, com cronômetro ao vivo e ganho do dia em tempo real.
- **Localização em tempo real**: mostra endereço, coordenadas e precisão; grava onde você bateu o ponto.
- **Valor da hora**: definido em Ajustes. Cada registro guarda o valor da época, então mudar depois não altera o histórico.
- **Lançamento manual**: data escolhida no calendário e horários em campos de hora (hh:mm) que só aceitam horas válidas e passam sozinhos da entrada para a saída.
- **Dashboard**:
  - ganhos do mês, meta, projeção e comparação com o mês anterior;
  - ganhos por dia com filtro de 7, 14, 21 dias ou 1 mês, barras clicáveis e linha de média móvel;
  - evolução acumulada do mês (com meta e projeção), rosca de horas por período do dia, calendário de atividade, ganhos por semana e horas por dia da semana;
  - indicadores: sequência de dias, horas extras, maior jornada, dias acima da meta, ritmo para bater a meta, entre outros.
- **Relatórios**: ganhos mês a mês, comparativo de 6 meses, detalhe dia a dia e **PDF** com o período que você escolher (mês, 30 dias, 3 meses, ano ou datas livres). O PDF é salvo em Downloads.
- **Importar de Excel/CSV**: em Ajustes > Importar e exportar. Lê `.xlsx` e `.csv`, reconhece colunas como Data, Entrada, Saída (e Entrada 2 / Saída 2 para quem para no almoço), deixa você conferir as colunas e ver uma prévia antes de importar. Registros que já existem são ignorados. Há um botão para baixar um modelo e outro para exportar seus registros.

Os dados ficam só no celular.

## Como gerar o APK

### Opção 1: GitHub Actions (sem instalar nada)

1. Envie esta pasta para um repositório no GitHub (branch `main`).
2. Abra a aba **Actions**, rode **Gerar APK** (ou espere o push disparar).
3. Baixe `PontoPJ-apk` em **Artifacts** e extraia o `app-debug.apk`.

### Opção 2: Android Studio

1. Abra a pasta `PontoPJ` e espere o Gradle sincronizar.
2. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção 3: linha de comando

Com JDK 17 e o Android SDK: `./gradlew assembleDebug`

## Atualizar o app já instalado

O APK é assinado com a chave fixa `app/pontopj.keystore`. Daqui para frente, instalar uma versão nova por cima mantém os registros.

Se a versão antiga foi assinada com outra chave (por exemplo, uma compilação anterior no GitHub Actions), o Android recusa a atualização por cima. Nesse caso é preciso desinstalar a versão antiga uma única vez, e os registros dela são apagados.
