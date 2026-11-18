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

## Installazione sul telefono

1. Scarica `app-release.apk` sul telefono.
2. Aprilo; se Android lo chiede, consenti l'installazione da questa fonte.
3. Apri LockPlayer una volta e consenti le notifiche.
4. (Facoltativo) Tieni premuto sulla home → Widget → LockPlayer.

## Come funziona

- `ShareActivity` riceve il link e lo passa al servizio di riproduzione, senza farti uscire da YouTube.
- `PlaybackService` (Media3 `MediaSessionService` + ExoPlayer) tiene viva la riproduzione e
  pubblica la sessione multimediale: è questo che fa comparire i controlli sul lock screen.
- `YouTubeResolver` usa [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
  per trovare lo stream audio del video e costruisce un manifest DASH, così l'audio viene
  scaricato a pezzi come fanno i client ufficiali.
- `YouTubeDataSource` imposta le richieste nel formato che YouTube si aspetta.
- I link agli stream scadono dopo alcune ore: in caso di errore l'app ne chiede uno nuovo e riprende.

## Limiti

- YouTube cambia spesso i suoi meccanismi: quando succede va aggiornata la versione di
  NewPipeExtractor in `app/build.gradle.kts` e ricompilata l'app.
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
