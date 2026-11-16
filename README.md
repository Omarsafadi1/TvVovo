# TV Vovô

Tela inicial para TV box Android (testado para Android 7.1, T95M) com canais libaneses gratuitos.

- **Canais:** edite `app/src/main/assets/channels.json` (nome, nome em árabe, link .m3u8, logo).
- **Atualizar canais sem reinstalar:** quando compilado pelo GitHub Actions, o app baixa a lista
  deste repositório toda vez que a tela inicial abre.
- **Controle:** setas escolhem o canal, OK assiste; dentro do canal ▲▼ trocam, números vão direto,
  Voltar retorna. Botão Menu (ou o último quadrado) abre as Configurações do Android.
- Fonte dos links: projeto público iptv-org (transmissões oferecidas pelos próprios canais).
