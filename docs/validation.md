# Validation de la version 1.0.0

- 208 tests JVM du launcher : réussis.
- 12 tests Python de l’installateur : réussis, avec protection des composants, refus des firmwares inconnus, conservation du VPN permanent, vérification de l’accueil avant nettoyage, restauration des états exacts après une coupure simulée, contrôle du SHA-256 et rejet des chemins ZIP sortants.
- `lintRelease` : aucune erreur bloquante. Les traductions héritées incomplètes sont informatives ; Android utilise la langue de repli. Des avertissements restent présents dans le code hérité.
- `assembleRelease` : réussi, réduction de code R8 et de ressources activée. Signature APK v2 vérifiée avec apksigner, clé RSA 3072 bits.
- Bootstrap Windows : téléchargement/extraction du Python portable vérifié et exécution de l’aide de l’installateur réussie.
- Sources publiques : liste Git inspectée, exclusions des sauvegardes, caches, APK, clés et photos personnelles contrôlées. ZIP construit à partir d’une liste explicite de fichiers autorisés.

Les fonctionnalités antérieures ont été contrôlées sur une Xiaomi `MiTV-AYFR0`/`soul`, Android 11 ; Chromecast a été confirmé avec image et son après nettoyage. Cette version change l’identité de l’application pour être distribuée indépendamment. Elle n’a pas été installée sur une Xiaomi réinitialisée pour la publication. Les tests simulés ne prouvent pas la compatibilité de tous les firmwares ni la disponibilité des aperçus de chaque service.
