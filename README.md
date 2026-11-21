# Samsung NTN Icon Emulator — V2 🛰️

## Objectif
**Déclencher un indicateur satellite dans le vrai gestionnaire d'icônes de Samsung SystemUI**, plutôt que dessiner une surcouche personnalisée dans la barre d'état.

Cette application est un **prototype expérimental LSPosed pour One UI**. Elle ne crée aucune liaison T-Mobile, Starlink, satellite ou SOS réelle. L'indicateur peut prêter à confusion : ne jamais s'y fier pour contacter les secours.

## Nouveautés V2
- Suppression du rendu bricolé de la V1 (plus de `ViewOverlay` ni de picto « SIM » dessiné).
- Recherche de `StatusBarIconControllerImpl` (versions Samsung et AOSP).
- Essai d'ajout de l'icône native dans le slot `satellite` de SystemUI.
- Utilisation **exclusive de ressources satellite existantes** dans le système (`ic_satellite_connected_0/1/2`, etc.).
- Contournement conditionnel du filtrage Samsung `hideBySimplification` pour le seul slot satellite.
- Bouton de diagnostic indiquant si des ressources natives classiques ont été trouvées.
- Réglage du signal fictif 0 à 4 et mode recherche; étiquettes « T-Satellite » / « Starlink » dans l'application, mais **pas de changement du véritable nom de réseau**.

## Installation **sur téléphone uniquement**
1. GitHub → Actions → **Compiler APK** → dernier run réussi → Artifacts → télécharger et extraire `app-debug.apk`.
2. **Désactiver la V1 dans LSPosed**. Cette APK debug a probablement une autre signature que la V1 : si la mise à jour est refusée, **désinstaller l'ancienne application** puis installer la V2. Tes réglages pourront être réinitialisés.
3. LSPosed → Modules → **Satellite Visual** → activer et sélectionner uniquement `com.android.systemui`.
4. Redémarrer le S23. Ouvrir l'application et activer l'indicateur.
5. Appuyer sur **VÉRIFIER LES RESSOURCES SATELLITE**. Si aucun effet, exporter les journaux LSPosed correspondant à `SatelliteVisualNative`.

## Limitations
- **Aucune garantie que le gestionnaire d'icônes Samsung One UI 9 rende un slot injecté**. Sur les versions récentes, des icônes sont dessinées par une pipeline Kotlin/Compose spécifique et ignorent l'ancienne API.
- Si Samsung ne fournit aucun drawable satellite reconnu, la V2 n'affiche **rien** plutôt qu'une imitation grossière.
- L'indicateur ne simule **pas** la véritable liaison opérateur et n'altère pas la disponibilité d'urgence. La vraie identité d'opérateur T-Mobile et les flux radio restent inchangés.
- Cette expérimentation n'est pas encore validée sur Galaxy S23 sous One UI 9.

## Compilation
GitHub Actions construit automatiquement une APK debug. Pas de PC nécessaire.

## Sécurité / retour arrière
Si SystemUI devient instable, désactiver le module dans LSPosed et redémarrer. Désactiver également FakeSim Termux si tu l'utilises : ce module est indépendant de ce projet.
