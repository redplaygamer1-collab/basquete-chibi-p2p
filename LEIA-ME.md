# Basquete Chibi 3D — plugin Wi‑Fi Direto (Android nativo)

Projeto Android Studio completo que embrulha o teu `index.html` numa `WebView`
e liga-o a uma ponte nativa de **Wi‑Fi Direct** (`WifiP2pManager`), exatamente
o bloco `window.AndroidP2P` que já estava previsto no teu jogo.

## O que já está pronto e funcional

- Descoberta de aparelhos por perto (`discoverPeers`)
- Ligação a um aparelho escolhido (`connect`)
- Formação automática do canal de dados assim que a ligação P2P é feita
  (quem for "group owner" abre um `ServerSocket`; o outro liga-se como cliente)
- Envio/receção de mensagens JSON entre os dois telemóveis (`send` / evento `spn-p2p-message`)
- Pedido de permissões em runtime (localização + `NEARBY_WIFI_DEVICES` no Android 13+)
- Um ecrã simples de "lobby" já ligado ao botão **Amigo (Wi‑Fi Direto)** do menu principal,
  onde dá para procurar, ver a lista de amigos por perto e tocar para ligar.

## Como gerar o APK

1. Abre a pasta `BasqueteChibiP2P` no Android Studio (Open → seleciona esta pasta).
2. Deixa o Gradle sincronizar (a primeira vez demora, precisa de internet).
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. O APK fica em `app/build/outputs/apk/debug/app-debug.apk` — instala nos dois telemóveis.

Se preferires terminal: `./gradlew assembleDebug` dentro da pasta do projeto.

## Como testar entre 2 telemóveis

1. Instala o APK nos dois aparelhos e abre a app.
2. Aceita as permissões pedidas (localização / "dispositivos próximos").
3. Nos dois telemóveis, toca em **Amigo (Wi‑Fi Direto)** no menu.
4. Num dos telemóveis toca em **Procurar amigos** — ao fim de alguns segundos
   o outro aparelho deve aparecer na lista (o nome que aparece é o nome Wi‑Fi
   do telemóvel — podes mudá-lo nas definições de Wi‑Fi Direct do Android).
5. Toca no nome dele para ligar. O Android deve mostrar um pedido de ligação
   no outro aparelho — aceita.
6. Quando aparecer "Ligado! Canal de dados pronto a usar." nos dois, a ligação
   está feita e já dá para trocar mensagens.

## Onde entra o resto do teu jogo

O que pediste (o plugin em si) está pronto e funcional: descoberta, ligação e
canal de dados bidirecional. O que falta — e que é trabalho à parte, não
"Wi‑Fi Direto" em si — é ligares a jogabilidade (posições dos jogadores,
bola, placar) a este canal. Para isso, no teu código do jogo:

```js
// enviar algo ao amigo
window.SPNUnaP2P.send({ tipo:"input", dx:0.4, dz:-1, acao:"shot" });

// receber o que o amigo mandou
window.addEventListener("spn-p2p-message", function(e){
  var dados = e.detail; // já vem como objeto, não string
  console.log("recebido:", dados);
});
```

Se quiseres, na próxima mensagem posso implementar essa parte (sincronizar
posições/bola/placar entre os dois telemóveis, no estilo "anfitrião manda o
estado, o outro manda os comandos") — é um passo separado do plugin.

## Estrutura

```
BasqueteChibiP2P/
  app/src/main/java/com/spn/chibi/p2p/
    MainActivity.java       -> WebView + permissões
    P2PManager.java          -> WifiP2pManager, descoberta, ligação, socket TCP
    AndroidP2PBridge.java    -> window.AndroidP2P exposto ao JS
  app/src/main/assets/
    index.html                -> o teu jogo, com o botão "Amigo" já ligado
  app/src/main/AndroidManifest.xml -> permissões de Wi‑Fi/localização
```
