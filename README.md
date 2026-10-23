# Xiaomi Clean Home

Un accueil Android TV épuré, une veille photo administrée en HTTPS et un installateur réversible pour Xiaomi. Projet communautaire indépendant, dérivé de CleanYourTV 2.7.0, sous licence MIT.

**[Télécharger la version prête à installer](https://github.com/BLK185/xiaomi-clean-home/releases/latest)** · [Fonctionnement et sécurité](docs/security.md) · [Développement](docs/build.md)

## Installation Windows, sans compilation

1. Sur la Xiaomi neuve, terminer la configuration Android TV : Wi-Fi, compte Google et mises à jour. La laisser sur son réseau local habituel.
2. Dans **Paramètres → Préférences de l’appareil → À propos**, appuyer sept fois sur **Build**. Dans **Options pour les développeurs**, activer **Débogage USB**. Relever l’adresse IPv4 dans les paramètres réseau.
3. Télécharger `xiaomi-clean-home-1.0.0-windows.zip` dans les Releases, puis **extraire tout le ZIP** dans un dossier conservé sur le PC.
4. Double-cliquer sur **Install.cmd**, saisir l’IP et accepter la clé ADB affichée sur la TV. Si la connexion a été refusée avant cette validation, relancer le script. Lire puis accepter le plan présenté sur le PC.
5. Le script sauvegarde l’état d’origine, installe l’APK signé, vérifie le nouvel accueil avant le nettoyage, redémarre et contrôle son démarrage. Il demande ensuite la fermeture du débogage. Vérifier sur la TV qu’il est désactivé, puis tester Chromecast avec image et son.

Python portable et les outils ADB officiels Google sont téléchargés si nécessaire. Aucun Java/SDK Android n’est requis pour installer. Internet est nécessaire au premier lancement. Le script ne désactive pas SmartScreen et ne modifie pas la politique PowerShell permanente.

**Compatibilité du nettoyage automatique : Xiaomi TV Stick 4K, modèle `MiTV-AYFR0`, appareil `soul`, Android 11 / SDK 30 uniquement.** Le matériel de référence a été utilisé pour la configuration et le test Chromecast ; l’installateur de cette publication est testé par simulation, sans essai sur un appareil réinitialisé. La même marque ne garantit pas le même firmware.

Sur une autre Xiaomi Android TV 8 ou plus, le nettoyage est bloqué. `Install.cmd --install-only` permet d’essayer l’accueil sans désactiver de paquets ; sa compatibilité matérielle reste à vérifier. Le script nécessite ADB accessible sur le réseau local, port 5555. Certains firmwares exigent une connexion USB ou une méthode d’association différente et ne sont pas pris en charge par ce parcours automatique.

Les validations de sécurité Android sur la TV, la connexion aux comptes et l’installation des applications propriétaires restent manuelles. Les APK Netflix, Prime Video, YouTube, TF1+ et Jellyfin ne sont pas inclus ; l’installateur propose les fiches Google Play des applications manquantes du profil.

## Ce qui est appliqué

- Une seule rangée horizontale d’applications, défilante : ajout, retrait et réorganisation depuis l’accueil. Google Play reste accessible par un bouton rond près des paramètres.
- Netflix, Prime Video, YouTube, TF1+ et Jellyfin conservés ; Chromecast, micro/recherche vocale, Bluetooth, clavier, Google Play et mises à jour protégés.
- Désactivation réversible des 20 paquets présents du [profil contrôlé](profiles/xiaomi-soul-android11.json) : PatchWall, Xiaomi TV+, MiChannel, recommandations Google, statistiques Xiaomi et applications annexes. Un VPN configuré comme permanent est conservé. Aucun paquet n’est désinstallé, aucune donnée d’application effacée.
- Animations de fenêtres et de navigation à 0,5 ; animation des contenus conservée. L’APK utilise R8 et la réduction des ressources. Les scènes photo statiques cessent leur boucle de dessin après la transition.
- Fond lié à l’application et aperçu si son fournisseur Android TV expose des contenus lisibles : bande-annonce HTTPS compatible quand disponible, sinon affiche. Les services ne rendent pas tous leurs bandes-annonces accessibles ; ce n’est pas une garantie pour chaque application. Les publicités internes à YouTube et aux autres services ne sont pas supprimées.
- Veille photo locale avec heure, dossiers/thèmes, import Wi-Fi, sélection de photos et paramètres uniquement sur le site web. Douze styles originaux inspirés des diaporamas classiques et de macOS, dont panneaux coulissants, Ken Burns, mosaïques, reflets, tirages et pliage ; neuf transitions. Ce ne sont pas les animations propriétaires d’Apple.
- Cadrage centré, remplissage sans étirement par défaut ; option d’ajustement disponible. Import limité au paysage 16:9, après orientation EXIF, avec tolérance d’arrondi d’un pixel. Doublons de fichiers identiques bloqués par SHA-256 ; les variantes recadrées ou recompressées sont des fichiers différents.

Les applications déjà désactivées ne sont pas réactivées. Les cinq applications conservées doivent être installées et connectées pour être utilisables. « Optimiser » ne garantit pas un gain chiffré : les applications ouvertes et la lecture vidéo consomment toujours des ressources.

## Photos et réglages de veille

Sur la TV : **Paramètres de l’accueil → Veille photo → Ajouter des photos par Wi-Fi**. Ouvrir l’adresse **HTTPS** affichée sur un téléphone ou PC du même réseau. Comparer l’empreinte du certificat avec celle affichée sur la TV avant d’accepter le certificat local. Demander une connexion, comparer le code et l’autoriser sur la TV.

Un seul navigateur peut être autorisé à la fois. Les photos restent dans le stockage privé de l’application sur la box. Fermer l’écran de partage ou révoquer l’accès sur la TV ferme la session. Aucun débogage USB n’est nécessaire pour les ajouts suivants. Ne pas ouvrir de port sur la box Internet. [Détails de sécurité et limites](docs/security.md).

## Retour arrière et diagnostic

Conserver le dossier **backups/** du PC : il contient les réglages d’origine et des informations privées sur l’appareil. Réactiver le débogage et double-cliquer **Restore.cmd** pour restaurer la dernière sauvegarde. Une sauvegarde précise se sélectionne avec `Restore.cmd --backup "chemin/state.json"`. La restauration vérifie modèle, appareil et numéro de série, remet les états des paquets, les trois réglages modifiés et l’ancien accueil. L’APK et ses photos restent installés ; ils ne sont pas effacés.

La dernière sauvegarde concerne la dernière exécution : après plusieurs installations, choisir explicitement la première pour retrouver l’état initial. En cas de perte de connexion, la restauration peut être partielle : rétablir le réseau/ADB puis relancer avec le même fichier. Ne jamais partager publiquement `backups/`.

Depuis les sources, Python 3.10+ :

```sh
python installer/main.py plan --host 192.0.2.10
python installer/main.py install --host 192.0.2.10 --keep-app com.example.application
python installer/main.py restore --host 192.0.2.10 --backup backups/DATE/state.json
```

Remplacer l’IP d’exemple par celle de la TV. Le diagnostic `plan` est en lecture seule et laisse le débogage actif. `--yes` supprime uniquement la confirmation du plan sur PC. Sous macOS/Linux : `sh install.sh` ou `sh restore.sh`, avec Python 3.10+ installé.

## Origine et licences

Basé sur [CleanYourTV](https://github.com/Gathin23/cleanyourtv), version 2.7.0 ; détails dans [NOTICE](NOTICE). Code MIT, copyright Gathin conservé dans [LICENSE](LICENSE). Police Outfit sous SIL OFL dans [FONT-LICENSE-OFL.txt](launcher/FONT-LICENSE-OFL.txt). Les marques et services cités appartiennent à leurs titulaires. Aucune photo personnelle, clé de signature privée ni donnée d’un appareil n’est distribuée.
