# LockPlayer

App Android che fa suonare i video di YouTube **anche a schermo bloccato**, con i controlli
sulla schermata di blocco e un widget per la home.

Si usa insieme all'app ufficiale di YouTube: navighi lì col tuo account, poi tocchi
**Condividi → Ascolta con LockPlayer**. L'audio parte nel lettore di LockPlayer e continua in
background.

## Cosa fa

- Riceve i link dal tasto **Condividi** di YouTube (e da link aperti in altre app).
- Riproduce **solo l'audio**, anche a schermo spento.
- Controlli su **schermata di blocco**, notifica, cuffie Bluetooth e auto (⏮ ⏯ ⏭ + barra).
- **Coda**: se qualcosa sta già suonando, il nuovo brano viene messo in fila.
- **Widget home** con titolo e ⏮ ⏯ ⏭.
- Si mette in pausa quando scolleghi le cuffie.
- **Proposta automatica**: quando parte un video nell'app YouTube compare per qualche secondo
  una notifica «Ascolta a schermo bloccato». Toccandola, YouTube va in pausa e il brano
  continua in LockPlayer dallo stesso secondo. Non compare per video sotto il minuto (Shorts)
  né due volte per lo stesso video. Si disattiva dall'interruttore nell'app.

## Installazione sul telefono

1. Scarica `app-release.apk` sul telefono.
2. Aprilo; se Android lo chiede, consenti l'installazione da questa fonte.
3. Apri LockPlayer una volta e consenti le notifiche.
4. Per la proposta automatica: nell'app tocca **Concedi accesso** e attiva LockPlayer
   nell'«accesso alle notifiche». Se l'opzione è grigia (Android 13+): Info app → ⋮ →
   «Consenti impostazioni con restrizioni», poi riprova.
5. (Facoltativo) Tieni premuto sulla home → Widget → LockPlayer.

## Come funziona

- `ShareActivity` riceve il link e lo passa al servizio di riproduzione, senza farti uscire da YouTube.
- `PlaybackService` (Media3 `MediaSessionService` + ExoPlayer) tiene viva la riproduzione e
  pubblica la sessione multimediale: è questo che fa comparire i controlli sul lock screen.
- `YouTubeResolver` usa [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
  per trovare lo stream audio del video e costruisce un manifest DASH, così l'audio viene
  scaricato a pezzi come fanno i client ufficiali.
- `YouTubeDataSource` imposta le richieste nel formato che YouTube si aspetta.
- `YouTubeWatcherService` (accesso alle notifiche) legge titolo, canale e durata che l'app
  YouTube pubblica nella sua sessione multimediale; `YouTubeSearch` + `VideoMatcher` trovano
  lo stesso video, e `HandoffActivity` mette in pausa YouTube e passa il brano al lettore.
- I link agli stream scadono dopo alcune ore: in caso di errore l'app ne chiede uno nuovo e riprende.

## Limiti

- YouTube cambia spesso i suoi meccanismi: quando succede va aggiornata la versione di
  NewPipeExtractor in `app/build.gradle.kts` e ricompilata l'app.
- La proposta riconosce il video cercando titolo, canale e durata: in rari casi (caricamenti
  identici dello stesso brano) può prendere una versione diversa. In quel caso usa Condividi.
- Riprodurre YouTube in background senza Premium è contro i termini di servizio di YouTube:
  l'app è pensata per uso personale.
- NewPipeExtractor è sotto licenza GPLv3, quindi lo è anche questa app se la distribuisci.

## Compilare

Serve JDK 17+ e l'Android SDK (platform 36).

```
./gradlew assembleRelease          # APK in app/build/outputs/apk/release/
./gradlew testReleaseUnitTest      # test offline
LIVE_YT_TEST=1 ./gradlew testReleaseUnitTest   # test che legge un video vero da YouTube
```
