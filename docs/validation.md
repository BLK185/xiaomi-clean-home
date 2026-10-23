# Validation de la version 1.0.0

## Affiches officielles TF1 et Netflix : 1.0.2

Les petites images Android TV ne constituent pas la meilleure source disponible. Le site public TF1 publie des variantes HD avec une signature différente : le code retrouve exactement la même référence d’image dans l’index public TF1 et utilise son URL publiée, sans fabriquer de signature. Netflix est consulté via la page publique du titre identifié par son lien Android TV ; l’adresse de son affiche grand format est conservée intacte.

Contrôle sur la Xiaomi de référence : TF1 passe de 395×222 à 1280×720, Netflix de 853×480 à 1920×1080, avec fond plein écran et proportions conservées. L’aperçu local reste visible pendant la première recherche HD. Les résolutions ne sont pas garanties pour chaque titre ou pays ; un changement de page peut nécessiter une mise à jour du parseur. Les tests vérifient les signatures distinctes, la correspondance du visuel, le rejet des hôtes non autorisés et l’extraction des identifiants explicites.

Le bloc nom de l’application/titre du contenu se place désormais au-dessus du dock avec une marge de 18 dp, selon sa hauteur réelle. La position est recalculée quand le titre apparaît ; un libellé ne peut donc plus descendre sous le dock à cause d’un décalage fixe.

Aucun compte, cookie d’authentification ou mot de passe n’est utilisé. Seuls les domaines des éditeurs sont consultés, sans exécution de leurs scripts ; Netflix reçoit la référence du titre à afficher sur sa propre page publique. Pages limitées à 8 Mo décompressés et cinq secondes de lecture, requêtes annulables. Seuls l’index des images et les URL correspondantes sont conservés ; aucun HTML n’est écrit sur le stockage de la TV. Cache des correspondances : 24 heures, deux minutes pour un échec ; index TF1 : une heure. Les caches d’images restent bornés. Cette récupération ne crée pas de bande-annonce si les applications n’en exposent pas.

## Correction des aperçus : 1.0.1

Le chargement initial passe de 800 à 120 ms de temporisation ; les affiches en mémoire sont affichées directement. Cache mémoire limité à 24 Mo, cache privé sur disque limité à 24 Mo/16 fichiers et 24 heures ; les listes de programmes sont réutilisées pendant deux minutes. Une image en cours de téléchargement est partagée entre préparation et affichage pour éviter les doublons. La préparation se limite aux applications de la rangée et cesse quand l’accueil est quitté ou les aperçus désactivés.

Le décodage conserve 1920×1080 sans division inutile par deux. Les petites vignettes et les portraits ne sont plus agrandis sur tout l’écran. La variante publique YouTube maxres est essayée avec retour au visuel d’origine si elle manque ou n’est pas assez grande. Les URL Netflix signées ne sont pas modifiées. La sélection dépriorise les bannières « Collection Netflix » et les images d’événements sportifs Prime quand des titres de catalogue sont disponibles.

Sur la box de référence, les sources TF1 observées étaient en 395×222 ; les variantes HD essayées ont été refusées par leur serveur. Aucun gain de résolution TF1 n’est donc promis. Une vignette YouTube proposée en 640×360 disposait d’une variante publique en 1280×720. Les résultats varient suivant les images publiées par chaque application. Quatre nouveaux tests vérifient le décodage Full HD, le traitement des petites images, les variantes YouTube et les priorités de contenu.

## Publication initiale

- 208 tests JVM du launcher : réussis.
- 12 tests Python de l’installateur : réussis, avec protection des composants, refus des firmwares inconnus, conservation du VPN permanent, vérification de l’accueil avant nettoyage, restauration des états exacts après une coupure simulée, contrôle du SHA-256 et rejet des chemins ZIP sortants.
- `lintRelease` : aucune erreur bloquante. Les traductions héritées incomplètes sont informatives ; Android utilise la langue de repli. Des avertissements restent présents dans le code hérité.
- `assembleRelease` : réussi, réduction de code R8 et de ressources activée. Signature APK v2 vérifiée avec apksigner, clé RSA 3072 bits.
- Bootstrap Windows : téléchargement/extraction du Python portable vérifié et exécution de l’aide de l’installateur réussie.
- Sources publiques : liste Git inspectée, exclusions des sauvegardes, caches, APK, clés et photos personnelles contrôlées. ZIP construit à partir d’une liste explicite de fichiers autorisés.

Les fonctionnalités antérieures ont été contrôlées sur une Xiaomi `MiTV-AYFR0`/`soul`, Android 11 ; Chromecast a été confirmé avec image et son après nettoyage. Cette version change l’identité de l’application pour être distribuée indépendamment. Elle n’a pas été installée sur une Xiaomi réinitialisée pour la publication. Les tests simulés ne prouvent pas la compatibilité de tous les firmwares ni la disponibilité des aperçus de chaque service.
