# Samsung SATIconEmul’ 🛰️

**Français** · [English](#english)

Module Android/LSPosed **expérimental et non officiel** permettant d’afficher une **icône satellite fictive** dans la barre d’état de certains appareils Samsung One UI, en utilisant les ressources graphiques déjà présentes dans Samsung SystemUI.

> ⚠️ **Simulation visuelle uniquement.** L’icône ne signifie pas que le téléphone est connecté à un satellite. L’application ne fournit **ni réseau satellite, ni appels ou messages d’urgence SOS, ni accès Starlink/T-Satellite**. Ne vous fiez jamais à cet indicateur en situation d’urgence.

## Français

### Fonctions

- **Affichage à la demande** d’une icône satellite dans la barre d’état grâce à LSPosed et à un emplacement d’affichage distinct : `satellite_visual_demo`.
- **Icône issue des ressources de Samsung SystemUI** lorsque les ressources natives sont disponibles : ce n’est pas une vraie connexion satellite.
- **Activation/désactivation** depuis l’application.
- **Quatre profils purement visuels** : satellite NTN connecté, SOS, T-Satellite/Starlink et recherche de satellite. Les profils sont des libellés de simulation, **pas des opérateurs ou services activés**.
- **Niveau de signal graphique de 0 à 4** (les ressources disponibles peuvent regrouper plusieurs niveaux sous le même dessin).
- **Bouton d’actualisation de SystemUI** et **diagnostic de la présence des ressources graphiques satellite**.
- **Français et anglais** : une seule APK contient les deux traductions. La langue suit celle d’Android. Sur les versions compatibles d’Android, on peut également choisir la langue dans les paramètres de langue par application.
- **Indépendance des connexions réelles** : le module ne modifie pas la SIM, les paramètres radio, le modem, la connectivité Wi-Fi ou les données mobiles.

### Configuration requise

- Téléphone **Samsung Galaxy** sous une version de **One UI compatible**.
- **Accès root** et environnement **LSPosed fonctionnel**.
- Module autorisé dans LSPosed pour le processus **System UI** : `com.android.systemui` **uniquement**.
- Android 10 (API 29) au minimum pour installer l’application. **Ce minimum ne garantit pas la compatibilité de SystemUI.**

**État des tests :** l’icône a été observée sur un **Samsung Galaxy S23 (SM-S911U1)** avec un firmware Samsung modifié, après chargement du module. La compatibilité n’est **pas vérifiée** sur les autres modèles/versions de One UI, ni sur des téléphones non rootés.

### Télécharger / installer

1. Ouvrir **[Releases](https://github.com/territoire-de-antoine-dev/Samsung-NTN-Icon-Emulator/releases/latest)** et télécharger le fichier `Samsung-SATIconEmul-v2.2.0.apk`.
2. Installer l’APK sur le téléphone. Si l’installation demande de remplacer une autre version signée avec une clé différente, **ne pas forcer** : lire la rubrique « Signatures » avant de désinstaller quoi que ce soit.
3. Activer **Samsung SATIconEmul’** dans **LSPosed → Modules** ; sélectionner uniquement **System UI (`com.android.systemui`)**.
4. Redémarrer le téléphone pour que LSPosed charge le module dans le nouveau processus SystemUI.
5. Ouvrir l’application, activer l’icône satellite fictive, sélectionner un profil et un niveau graphique, puis toucher **ACTUALISER SYSTEMUI** si nécessaire.

**Désinstallation / retour arrière :** désactiver le module dans LSPosed et redémarrer. Si SystemUI est instable, désactiver le module avant toute nouvelle tentative. Aucun changement de modem/SIM n’est nécessaire.

### Diagnostics (facultatifs, lecture seule)

Depuis Termux avec root :

```sh
su -c 'dumpsys package fr.territoire.satvisual | grep -E "versionCode=|versionName=" | head -n 4'
su -c 'logcat -d -b all 2>/dev/null | grep -F "SatelliteVisualNative:" | tail -n 50'
```

Les journaux peuvent contenir des informations sur le téléphone ; **relire avant partage public**. Si aucune icône n’apparaît, vérifier d’abord le périmètre LSPosed, le redémarrage de SystemUI, puis les messages `CONFIG_BROADCAST`, `HOLDER_`, `DEMO_NATIVE_HOLDER_REGISTERED`.

### Limitations et signatures

- Fonctionnement **expérimental** : la barre d’état de Samsung varie fortement selon le modèle et la version de One UI.
- Les profils T-Satellite/Starlink/SOS ne fournissent **aucun service réel**, et le dessin de l’icône ne constitue **jamais une preuve de couverture satellite**.
- Les versions distribuées par le workflow actuel sont des **APK de développement signées par la clé debug du runner GitHub Actions**. Cette clé peut changer entre deux compilations : **une mise à jour en place peut être refusée par Android**. Conserver une version fonctionnelle avant une nouvelle installation ; désinstaller efface les paramètres locaux. Pour publier des mises à jour compatibles, une clé de signature stable stockée de façon sécurisée est nécessaire.
- Le code ne demande pas les autorisations téléphone ni Internet. **Root/LSPosed restent des composants à haut niveau de privilège** : vérifier les sources et les APK avant installation.
- L’icône du lanceur et les noms Samsung, Starlink, T-Satellite sont utilisés à des fins de description/identification ; **ce projet n’est affilié à aucune de ces entreprises**.

### Code et licence

Application Android Java/Gradle. `MainActivity.java` : interface et traduction ; `VisualConfig.java` et `ConfigProvider.java` : paramètres ; `ModuleHook.java` : intégration graphique LSPosed dans SystemUI ; `.github/workflows/build-apk.yml` : compilation et publication.

Le **code source** est distribué sous **licence MIT** (`LICENSE`). Cette licence **ne concède aucun droit sur les marques, logos ou ressources graphiques de Samsung et de tiers**. Le logo de l’application est utilisé ici comme illustration de projet non officiel.

---

## English

**Samsung SATIconEmul’** is an **experimental, unofficial Android/LSPosed module** that adds a **simulated satellite icon** to the status bar of compatible Samsung One UI devices, using existing Samsung SystemUI icon resources.

> ⚠️ **Visual simulation only.** The icon does **not** indicate an actual satellite connection. This app does **not** enable satellite connectivity, SOS emergency calls/messages, Starlink, or T-Satellite. **Never rely on this indicator in an emergency.**

### Features

- **Toggle a fake satellite icon** inside Samsung’s status bar using LSPosed and a separate display slot (`satellite_visual_demo`).
- **Reuse native Samsung satellite drawables** when available rather than rendering a network connection state.
- **Four display profiles**: NTN connected, SOS, T-Satellite/Starlink, and searching. These are **simulation labels only**.
- **Graphic signal level 0–4**; native resources may show the same icon for adjacent levels.
- **SystemUI refresh** action and a **built-in satellite resource check**.
- **English and French in the same APK**, automatically selected from the Android device language. Supported Android versions may offer a per-app language choice.
- **No cellular/Wi-Fi state changes**: no modem, SIM, operator, radio, firewall, or network interception code.

### Requirements and confirmed compatibility

- A **compatible Samsung Galaxy / One UI** build.
- **Root** and a working **LSPosed** environment.
- LSPosed module scope set to **System UI (`com.android.systemui`) only**.
- Android 10/API 29 or later for installation. **That is not a guarantee of SystemUI compatibility.**

**Tested visually:** an icon appeared on a **Samsung Galaxy S23 (SM-S911U1)** running a modified Samsung firmware. **Other models, software builds, and unrooted phones are untested/not guaranteed.**

### Download and installation

1. Visit **[GitHub Releases](https://github.com/territoire-de-antoine-dev/Samsung-NTN-Icon-Emulator/releases/latest)** and download `Samsung-SATIconEmul-v2.2.0.apk`.
2. Install the APK. **Do not force an uninstall** if Android rejects the update due to a different signature; see “Signing and limitations” first.
3. In **LSPosed → Modules**, enable **Samsung SATIconEmul’** and select **System UI (`com.android.systemui`) only**.
4. Reboot so the new SystemUI process loads the module.
5. Open the app, turn on the simulated icon, choose a display profile and level, and tap **REFRESH SYSTEMUI** if needed.

**Rollback:** disable the module in LSPosed and reboot. If SystemUI becomes unstable, disable the module before further testing. No modem or SIM changes are needed.

### Troubleshooting (read-only)

With root access in Termux:

```sh
su -c 'dumpsys package fr.territoire.satvisual | grep -E "versionCode=|versionName=" | head -n 4'
su -c 'logcat -d -b all 2>/dev/null | grep -F "SatelliteVisualNative:" | tail -n 50'
```

Review logs for private device information before posting publicly. Check your LSPosed scope, the SystemUI restart, then `CONFIG_BROADCAST`, `HOLDER_`, and `DEMO_NATIVE_HOLDER_REGISTERED` messages.

### Signing and limitations

- **Experimental compatibility**: Samsung status-bar internals differ across One UI versions and devices.
- The indicator is **not real satellite coverage or SOS functionality**.
- Current GitHub Actions builds are **debug-signed on the build runner**, so the signing certificate can change between runs. **In-place APK upgrades may fail**. Uninstalling removes app preferences. A securely stored, consistent release signing key is needed for reliable future updates.
- The app does not request phone or Internet permissions. Root/LSPosed are powerful system modifications; review the source before installation.
- **Not affiliated with or endorsed by Samsung, Starlink, T-Mobile, or any satellite or mobile network provider.**

### Source code and licensing

The project uses Android Java/Gradle. `MainActivity.java` provides the localized interface; `VisualConfig.java`/`ConfigProvider.java` handle visual preferences; `ModuleHook.java` implements the LSPosed SystemUI integration; `.github/workflows/build-apk.yml` builds and publishes the APK.

**Source code** is available under the **MIT License** (`LICENSE`). This code license **does not grant rights to Samsung or third-party trademarks, logos, or graphics**. The launcher art identifies this unofficial project.
