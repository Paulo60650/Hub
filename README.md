# Hub — votre téléphone, regroupé

Appli Android (Kotlin + Jetpack Compose), design « Obsidian » : code PIN à la
première ouverture, écran d'accueil à deux pages, icônes colorées.

**Toutes les icônes sont de vraies applis intégrées à Hub** (aucune ne renvoie
vers une appli externe) :

| Appli | Ce qu'elle fait dans Hub |
|---|---|
| Notes, À faire, Journal, Habitudes | Stockage local |
| Budget | Dépenses, budget mensuel, camembert par catégorie |
| Calculette | Calcul simple |
| Appels | Clavier, appel direct, journal des appels |
| Contacts | Liste, recherche, ajout, suppression, appel, SMS |
| Messages | Lecture et envoi de **SMS** |
| Mail | Boîte de réception, lecture, réponse, envoi (IMAP/SMTP, mot de passe d'application) |
| Agenda | Événements du calendrier du téléphone : lire, créer, supprimer |
| Galerie | Photos du téléphone, plein écran, partage |
| Horloge | Heure mondiale, réveils, minuteur, chronomètre |
| Fichiers | Parcourir un dossier choisi, ouvrir, partager, créer, supprimer |
| Météo | Prévisions 7 jours (Open-Meteo), recherche de ville ou position |
| Coffre | Mots de passe et notes chiffrés (AES-256, clé du Keystore) |
| Dictaphone | Enregistrer, écouter, partager des mémos |
| Scanner | Photos de pages → PDF |
| Navigateur | Navigateur intégré (WebView) avec favoris |
| Audio | Lecteur de la musique du téléphone |
| Cartes | Carte OpenStreetMap, recherche de lieu, position |
| Réglages | Code PIN, délai de verrouillage, raccourcis réglages Android |

**Limites à connaître (Android ne permet pas mieux à une appli tierce) :**
SMS classiques uniquement (pas RCS ni WhatsApp) et pas de réception en temps
réel ; Mail demande un mot de passe d'application pour Gmail/Outlook/iCloud ;
la musique ne propose pas de commandes dans les notifications ; le Coffre
ne chiffre que du texte (pas de fichiers) ; Fichiers n'accède qu'au dossier
que vous désignez ; les itinéraires s'ouvrent dans votre appli de navigation.
Au premier usage de chaque appli, Android demande les autorisations utiles.

Données : stockées uniquement sur le téléphone. Code PIN : jamais en clair
(empreinte SHA-256 salée). Reverrouillage après 30 s en arrière-plan.

## Obtenir l'APK avec GitHub (une seule fois à configurer)

1. Sur https://github.com/new, créez un dépôt (par ex. `hub`), **vide**.
2. Envoyez-y ce dossier :
   - soit en ligne de commande, dans ce dossier :
     ```
     git init -b main
     git add .
     git commit -m "Hub"
     git remote add origin https://github.com/VOTRE_NOM/hub.git
     git push -u origin main
     ```
   - soit via la page du dépôt → *uploading an existing file* (glissez tout le
     contenu, y compris le dossier `.github`).
3. Dans le dépôt, onglet **Actions** : « Construire l'APK » démarre seul
   (≈ 4–6 min la première fois). Si besoin : *Run workflow*.
4. Quand il est vert, récupérez l'APK :
   - onglet **Releases** → « Hub – dernière version » → `Hub.apk`
   - lien direct toujours à jour :
     `https://github.com/VOTRE_NOM/hub/releases/latest/download/Hub.apk`
     (dépôt **public** requis pour ouvrir ce lien sans compte ; en privé,
     téléchargez depuis Actions → le run → *Artifacts* → `Hub-apk`).
5. Sur le téléphone : ouvrez le fichier, autorisez « Installer des applis
   inconnues » pour votre navigateur/gestionnaire de fichiers, puis installez.

Chaque `git push` sur `main` reconstruit l'APK. Il est signé avec la clé
`app/debug.keystore` du dépôt : les mises à jour s'installent par-dessus
l'ancienne version sans perdre vos données.

> Clé de signature publique (dans le dépôt) : pratique pour un usage
> personnel, mais à remplacer par une clé secrète si vous publiez un jour
> l'appli sur le Play Store.

## Si la construction échoue

Ouvrez le run rouge dans *Actions*, copiez les lignes `e: …` / `error:` et
envoyez-les à Claude : la correction est en général d'une ligne.

## Compiler en local (facultatif)

Android Studio → *Open* ce dossier → ▶︎. Ou : `gradle :app:assembleDebug`
(Gradle 8.9, JDK 17, SDK Android 34).
