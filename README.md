# Gym Tracker

App Android per schede, allenamenti, progressi e community.

## Aggiornamenti Android

All'avvio l'app controlla l'ultima GitHub Release pubblicata in `lucaaa02/gymBuddy`. Se trova un APK con versione superiore, propone di scaricarlo e avvia la procedura di installazione Android. Android richiede comunque all'utente di autorizzare l'installazione e confermarla.

La GitHub Action in `.github/workflows/release-android.yml` costruisce e pubblica un APK quando viene inviato un tag di versione nel formato `vMAJOR.MINOR.PATCH`, per esempio `v1.0.1`.

### Configurazione una tantum delle chiavi

Per aggiornare l'app installata, ogni APK deve essere firmato con la stessa chiave. In GitHub apri **Settings > Secrets and variables > Actions** e crea questi repository secrets:

- `ANDROID_KEYSTORE_BASE64`: contenuto del keystore codificato in Base64.
- `ANDROID_KEYSTORE_PASSWORD`: password del keystore.
- `ANDROID_KEY_ALIAS`: alias della chiave.
- `ANDROID_KEY_PASSWORD`: password della chiave.

Per continuare ad aggiornare senza reinstallare un APK di debug già presente sul dispositivo, usa il keystore debug che ha firmato quell'APK (di solito `~/.android/debug.keystore`, alias `androiddebugkey`, password predefinite `android`). Codifica il file senza aggiungerlo al repository, ad esempio su Linux/macOS:

```bash
base64 -w 0 ~/.android/debug.keystore
```

Su macOS, se `base64 -w` non è disponibile:

```bash
base64 < ~/.android/debug.keystore | tr -d '\n'
```

Incolla il risultato come secret `ANDROID_KEYSTORE_BASE64`; configura gli altri tre secrets con i valori della stessa chiave. Non committare mai il keystore o la stringa Base64. Se non possiedi la chiave che ha firmato una versione già installata, Android non può aggiornarla in-place con una firma diversa: occorre installare manualmente la prima versione firmata con la chiave scelta. I dati di allenamento salvati su Firebase restano sul database; una reinstallazione può richiedere di accedere di nuovo.

### Pubblicare un aggiornamento

Dopo aver configurato i secrets, crea e invia un tag:

```bash
git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions compila la versione, assegna un `versionCode` crescente in base alle esecuzioni della workflow e pubblica l'APK nella Release. L'app lo rileva al successivo avvio. Pubblica versioni semantiche crescenti; il controllo legge la GitHub Release stabile più recente.

## Build locale

```bash
./gradlew assembleDebug
```
