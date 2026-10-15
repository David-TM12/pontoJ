# Próximas ideias

## Login com conta Google (+ sincronização na nuvem)

Status: anotado, ainda não feito. Hoje os dados ficam só no celular.

**Você precisa fazer:**
1. Criar um projeto no Firebase (console.firebase.google.com).
2. Adicionar um app Android com o pacote `br.com.pontopj`.
3. Cadastrar o SHA-1 da chave fixa do app (`app/pontopj.keystore`):
   `6A:81:46:B1:B9:22:56:42:E9:2D:27:98:CF:0A:7E:F8:0B:17:C9:98`
4. Em Authentication, ativar o provedor Google.
5. Baixar o `google-services.json` e colocar em `app/`.

**No código:**
- Firebase Auth + Credential Manager (Google Sign-In), permissão de internet, plugin `google-services`.
- Tela de login com "Entrar com Google"; nome e foto em Ajustes, com opção de sair.
- Opcional: sincronizar os registros pelo Firestore (backup e troca de celular). A decidir: subir os registros que já estão no celular no primeiro login?

## Relógio: próximas etapas

Etapa 1 (feita): app do relógio com tela principal e sincronização com o celular.

Etapa 1.5 (feita): página de resumo do mês no relógio e vibração ao bater a meta do dia.

Etapa 1.6 (feita): telas Hoje, Semana e Ajustes no relógio; ritmo por dia útil no resumo do mês.

Etapa 2 (a fazer):
- Tile (painel ao deslizar o mostrador) com tempo, ganho e botão de saída.
- Complicação para o mostrador (tempo trabalhado ou ganho do dia).
- Atividade em andamento no mostrador, como em um treino.
- Modo ambiente (tela apagada) com cronômetro simplificado.
