# Compiler et contribuer

Java 17 ou 21, SDK Android 34, Python 3.10+. `ANDROID_HOME` doit désigner le SDK ; accepter ses licences avec les outils Android officiels. Le wrapper Gradle vérifie le SHA-256 de sa distribution.

```powershell
$env:JAVA_HOME = 'CHEMIN_DU_JDK'
$env:ANDROID_HOME = 'CHEMIN_DU_SDK'
./scripts/build.ps1
python -m unittest discover -s tests -v
```

Le script exécute les tests JVM, lint et la compilation optimisée, crée une clé de release **locale** dans `.private/`, signe l’APK dans `dist/` et actualise `release-manifest.json`. Sauvegarder `.private/` hors du dépôt : perdre la clé empêche les mises à jour signées avec cette identité. Ne jamais publier cette clé ou son mot de passe. Pour un fork, changer l’applicationId, les références Java/composants de l’installateur et la destination GitHub, puis signer avec sa propre clé.

Sur les autres systèmes, lancer dans `launcher/` : `sh gradlew --no-daemon --max-workers=1 testReleaseUnitTest lintRelease assembleRelease`. Sans variables de signature, cet APK de compilation est non signé et ne convient pas à l’installation. Variables disponibles : TVHOME_KEYSTORE, TVHOME_STORE_PASSWORD, TVHOME_KEY_ALIAS, TVHOME_KEY_PASSWORD.

Les tests de l’installateur simulent les pannes, le garde-fou HOME et les restaurations ; ils ne remplacent pas un test sur chaque firmware. Un profil supplémentaire doit identifier exactement fabricant/modèle/appareil/SDK et être contrôlé sur matériel réel, avec Cast et recherche vocale après redémarrage. Ne pas étendre le profil à toutes les Xiaomi sans preuve. Ne pas ajouter de désinstallation, d’effacement de données ou de désactivation de composants protégés.
