# Samsung NTN Icon Emulator 🛰️

Module **LSPosed + application Android** de **simulation visuelle** de l’icône satellite dans la barre d’état, sans aucune connexion satellite réelle. Projet expérimental pour Samsung Galaxy S23, KernelSU et One UI.

## Fonctionnalités
- Application mobile avec bouton ON/OFF, modes NTN / SOS simulé / Starlink simulé / recherche, signal fictif 0 à 4/4.
- Décalage horizontal et contraste blanc/noir.
- Aperçu graphique, synchronisation des réglages avec SystemUI.
- Module LSPosed traditionnel (Xposed API 82) qui dessine un indicateur **SIM** au-dessus de la barre d’état. Aucun accès au modem, aux SMS, à l'opérateur, aux appels d'urgence, à iptables ou au réseau.
- Une barre d’état Samsung peut refuser certains points d’accroche : dans ce cas, consulter les journaux LSPosed avec la recherche `SatelliteVisual`.

## Installer depuis le téléphone (sans PC)
1. Sur GitHub, ouvrir l’onglet **Actions**, puis **Compiler APK**. Ouvrir l’exécution réussie.
2. Dans **Artifacts**, télécharger **Satellite-Visual-LSPosed-APK** et extraire `app-debug.apk`.
3. Installer l’APK Android et ouvrir **Satellite Visual**.
4. Dans LSPosed → Modules → Satellite Visual : activer le module ; dans son périmètre sélectionner **System UI (com.android.systemui)** seulement.
5. Redémarrer le téléphone. Dans Satellite Visual, activer la simulation et choisir un mode, le signal et la position.
6. Si rien ne s’affiche, vérifier le journal LSPosed avec `SatelliteVisual` : le hook SystemUI peut demander une adaptation à ta version One UI.

## Dépannage / retour arrière
- À tout moment, désactiver la simulation dans l'application.
- Si SystemUI devient instable, désactiver le module dans LSPosed puis redémarrer.
- Retirer / désactiver FakeSim, qui n'est pas nécessaire et peut modifier le réseau.
- **Ne jamais interpréter l'indicateur factice comme une disponibilité réelle de Satellite SOS.**

## Statut
Code source complet ; la compilation passe par GitHub Actions. Le rendu sur Android 17 / One UI 9 est **expérimental, non vérifié sur un appareil**.
