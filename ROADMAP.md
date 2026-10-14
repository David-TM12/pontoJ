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
