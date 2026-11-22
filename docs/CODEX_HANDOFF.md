# Dossier de passation à Codex — icône satellite Samsung / Windows ADB

> Dernière mise à jour : 22 novembre 2026. Résumé technique de la longue session ChatGPT qui a abouti à un module LSPosed fonctionnel. **Ce document est un résumé, pas un export intégral du chat.** Il ne contient pas les journaux privés non nécessaires. Le dépôt est public : ne jamais y pousser de données personnelles ou de dump système bruts.

## 1. Mission actuelle

Sur le **Samsung Galaxy S23 SM-S911U1** de l'utilisateur (firmware Samsung modifié, root **KernelSU**, **LSPosed** opérationnel), l'application **Samsung SATIconEmul’** affiche désormais une icône satellite **fictive** en haut dans la barre d'état. L'utilisateur souhaite **la forme authentique Samsung One UI**, pas la ressource graphique satellite générique **AOSP/Google Pixel** affichée actuellement, tout en préservant le fonctionnement des réseaux **Wi-Fi et 4G/5G Free**.

L'utilisateur est maintenant **sur un PC Windows** et veut que **Codex effectue les diagnostics et tests depuis ce PC avec ADB**, sans lui demander de copier/coller régulièrement les commandes dans Termux. Codex doit donc explorer le dépôt et, après autorisation USB, exécuter lui-même les commandes ADB locales, recueillir les résultats, comparer les ressources du firmware, proposer un correctif minimal et fournir un rapport. **Aucune action n'a encore été effectuée sur son PC.**

Ne pas supprimer la simulation, ne pas tenter d'établir une connexion satellite réelle, ne pas toucher au modem, à la SIM ou aux services d'urgence.

## 2. Dépôt, version, architecture

- Dépôt public courant : **https://github.com/territoire-de-antoine-dev/Samsung-SATellite-Icon-Emulator** (ancien nom `Samsung-NTN-Icon-Emulator`, les anciennes URL peuvent rediriger).
- Application affichée : **Samsung SATIconEmul’**.
- Package ID Android : **`fr.territoire.satvisual`**. **Ne pas le changer**, afin de conserver les préférences et périmètres LSPosed.
- Version publiée : **v2.2.0** (`versionCode=14`), APK bilingue FR/EN, ressource d'icône de lanceur personnalisée.
- Release publique : https://github.com/territoire-de-antoine-dev/Samsung-SATellite-Icon-Emulator/releases/tag/v2.2.0
- Principal code : `app/src/main/java/fr/territoire/satvisual/ModuleHook.java`. Configuration : `VisualConfig.java`, `ConfigProvider.java`. UI : `MainActivity.java`. Workflow : `.github/workflows/build-apk.yml`.
- Traductions : `app/src/main/res/values/strings.xml` (anglais), `values-fr/strings.xml` (français). Ne pas modifier l'UI pour cette mission.
- Root/LSPosed : module installé et activé **sur `com.android.systemui` uniquement**. Le slot dédié à l'effet graphique est `satellite_visual_demo`, jamais le slot réel `satellite`.
- La **v2.1.2** a été **visuellement confirmée** sur le S23 après un redémarrage total. La v2.2.0 a compilé mais son rendu n'a pas été validé appareil dans la session.
- Les builds GitHub Actions précédents utilisent la clé **debug éphémère** du runner : attention, la signature peut changer à chaque build et empêcher une mise à jour de l'APK installé. Ne pas désinstaller une version fonctionnelle sans accord, car cela efface potentiellement les paramètres.
- Les captures déjà publiques figurent dans `docs/screenshots/`. Les **deux captures comparatives récentes** de « vrai Samsung vs module » ont été partagées dans la conversation ChatGPT, mais ne sont **pas garanties présentes dans ce dépôt**. Ne pas les inventer ni recopier des éléments privés vers GitHub.

## 3. Comportement observé et objectif visuel

- Capture de référence : sur un téléphone Samsung avec essai satellite réel, on voit un pictogramme satellite et des ondes, en apparence entièrement blanches.
- Capture du module actuel : silhouette similaire mais pouvant montrer une seule onde pleine et une autre grisée / un rendu AOSP. L’utilisateur interprète cela comme la différence Samsung vs Google Pixel.
- **Hypothèse simple non testée au moment du transfert** : c'est uniquement une différence de **niveau 2/4 versus 4/4** ; l’app utilise `ic_satellite_connected_1` à 2/4 et `ic_satellite_connected_2` à 4/4. **Tester ce point en priorité** avant de modifier une ligne de code. Les visuels ne prouvent pas à eux seuls l'origine Samsung ou Pixel.
- Identifier **les ressources réellement fournies par le firmware Samsung** et si l'icône affichée est bien `com.android.systemui:drawable/ic_satellite_connected_2` ou autre. Une capture identique n'implique pas que la ressource soit Samsung-spécifique. Faire une comparaison sur appareils et captures, pas une supposition à partir des noms de ressources.

## 4. Logiciel existant : points techniques prouvés par les journaux

Le module fonctionne par réflexion/injection dans **`com.android.systemui`**. Les signatures **vérifiées sur ce S23** sont :

```text
com.android.systemui.statusbar.phone.ui.StatusBarIconControllerImpl
  setIcon(CharSequence description, String slot, int resId)
  setIcon(String slot, com.android.systemui.statusbar.phone.StatusBarIconHolder holder)
  setIconVisibility(String slot, boolean visible)

com.android.systemui.statusbar.phone.ui.StatusBarIconList
  getIconHolder(int tag, String slot)
  getSlot(String slot)

com.android.internal.statusbar.StatusBarIcon
  StatusBarIcon(UserHandle, String, Icon, int, int, CharSequence, StatusBarIcon.Type)
```

Le `StatusBarIcon` à sept arguments a été créé avec succès (`STATUSBARICON_CONSTRUCTED params=7`). `StatusBarIconHolder.fromIcon` était indisponible sur ce firmware ; la **v2.1.2** a introduit la création d'un nouveau holder via son constructeur privé sans arguments puis affectation du champ/setter icon, sans écraser le slot `satellite` réel. Après redémarrage, **l'icône s'affichait enfin**. Le mécanisme doit être préservé : ne pas réintroduire les tentatives antérieures.

**Lookup actuel du drawable dans `ModuleHook.java` :**

```java
String strength = level == 0 ? "0" : level <= 2 ? "1" : "2";
String[] names = mode == 3
  ? new String[]{"ic_satellite_not_connected", "ic_satellite_searching",
                 "stat_sys_satellite_searching", "ic_satellite_connected_0"}
  : new String[]{"ic_satellite_connected_" + strength,
                 "stat_sys_satellite_connected_" + strength,
                 "stat_sys_satellite_" + strength,
                 "ic_satellite_connected", "stat_sys_satellite_connected",
                 "stat_sys_satellite", "ic_satellite"};
int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
```

L'app a déjà détecté `ic_satellite_connected_0`, `_1`, `_2` et `ic_satellite_not_connected` dans `com.android.systemui`.

Les réglages sont envoyés par **broadcast explicite** au processus SystemUI via `CONFIG_BROADCAST enabled=true level=X mode=Y`, car le `ContentProvider` peut journaliser `Unknown authority fr.territoire.satvisual.settings` au démarrage. Les anciens échecs d'installation de l'icône étaient :
1. Mauvais moment de création de `Handler` en Zygote ;
2. Mauvaise signature `setIcon` ;
3. Erreur `NullPointerException StatusBarIcon.pkg` quand on mettait à jour un slot sans holder ;
4. Mauvaise signature `getIconHolder` ;
5. Occupation du slot système satellite réel ;
6. Construction du `StatusBarIcon` à 7 arguments ;
7. Absence de factory statique `StatusBarIconHolder.fromIcon` ;
8. Réussite visuelle après création correcte du holder et **redémarrage du processus SystemUI**.
Les journaux d'un processus SystemUI démarré avant une mise à jour de l'APK peuvent correspondre **à l'ancienne version** même si `dumpsys package` affiche la version nouvellement installée. Il faut confronter **PID et heure** aux événements d'installation/reboot.

## 5. Limites de sécurité et de périmètre (à appliquer impérativement)

- **Lecture seule au départ** : diagnostic environnement, fichiers, dumpsys et ressources. Ne pas exécuter de reboot, `am force-stop`, `kill` de SystemUI, installation APK, modification LSPosed/root, commande `su` à effet de bord, changement réseau ou effacement sans autorisation explicite.
- Si `adb shell su -c` nécessite une confirmation KernelSU, demander à l'utilisateur d'approuver sur son écran ; ne jamais contourner une demande de privilèges.
- **Ne pas toucher à FakeSim Termux** ni aux règles `iptables` : ancien module indépendant `/data/adb/modules/fakesim_termux` ayant perturbé les données mobiles Free. Il devrait rester désactivé. Le vérifier seulement si utile **sans le relancer**.
- Ne pas faire passer une connexion satellite fictive pour un vrai état télécom ou un mécanisme SOS réel. Le projet est une **simulation visuelle** ; l'application et sa documentation doivent le préciser.
- Éviter le scan excessif des données du téléphone. Ne pas collecter messages, comptes, photos, mots de passe, identifiants, jetons, numéros SIM/IMEI, adresses MAC ni réseau complet.
- Aucun dump de firmware propriétaire ou APK Samsung récupéré sur le téléphone ne doit être commité / publié dans le dépôt public. Les métadonnées nécessaires (noms de drawables, tailles, signatures) et rendus comparatifs autorisés peuvent être décrits **sans redistribuer** des ressources propriétaires.
- Ne pas pousser vers `main` ni créer de nouvelle release sans validation du correctif. Créer une branche de travail, diffs isolés et sauvegarder l'APK fonctionnel.

## 6. Protocole ADB depuis PC Windows (Codex l'exécute, l'utilisateur ne colle rien dans Termux)

**Étape A — connexion et prérequis.** Vérifier la présence d'`adb` (Android Platform-Tools), de `git`, de Java/Gradle. Si ADB absent, indiquer le téléchargement officiel Android et proposer l'installation avec accord. L'utilisateur connecte le Galaxy S23 par câble USB de données, active les **Options développeur > Débogage USB** et accepte l'autorisation sur son écran. Le téléphone doit être en état `device` dans `adb devices -l`. Ne jamais désactiver une protection Android.

**Étape B — commandes purement diagnostiques.** Codex peut exécuter directement sur le PC, en PowerShell, sans Termux :

```powershell
adb version
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
adb shell dumpsys package fr.territoire.satvisual
adb shell dumpsys statusbar
```

Analyser les sorties en mémoire puis ne sauvegarder qu'un résumé utile et éventuellement un journal filtré. Pour les journaux LSPosed, `adb shell su -c 'logcat -d -b all'` peut être utilisé **après autorisation root** et uniquement en filtrant **localement** les lignes contenant `SatelliteVisualNative:`, sans publier le logcat intégral. Tester et adapter le quoting Windows selon le shell de Codex. Conserver la chronologie PID/horodatage et les lignes `CONFIG_BROADCAST`, `DEMO_NATIVE_HOLDER_REGISTERED`, `HOLDER_`, `INJECTION`.

**Étape C — test réversible de profil.** Commencer par **comparer visuellement niveau 2/4 et 4/4 via l'UI existante** en faisant uniquement varier le curseur, et vérifier si le dessin des ondes change. Préférer une manipulation manuelle par l'utilisateur de l'UI, ou demander explicitement accord avant l'automatisation du téléphone. Ne pas lier ce réglage à un indicateur réseau réellement connecté.

**Étape D — recherche de ressource Samsung authentique (lecture seule).** Identifier la version de `com.android.systemui` et les noms/références de ressources satellites. Si nécessaire utiliser `adb shell pm path com.android.systemui` puis, **après accord de l'utilisateur**, copier les APK SystemUI concernés localement sur PC avec `adb pull` vers un espace de travail privé (pas sur GitHub). Utiliser `aapt2`, `apkanalyzer`, éventuellement `apktool` en lecture seule pour inventorier les noms de drawables et les distinguer des ressources AOSP. Examiner si les packages Samsung `com.samsung.*` / overlays portent un autre drawable satellite ; tester une hypothèse à la fois. Comparer les **dessins**, pas les noms uniquement.

**Étape E — correction optionnelle.** S'il existe un meilleur drawable Samsung, changer **uniquement** la sélection de ressource utilisée par l'icône fictive, pas le moteur/holder. Ajouter un mécanisme de fallback sûr si nécessaire. Préserver `fr.territoire.satvisual`, les profils FR/EN et les avertissements de simulation. Compile sur branche, inspecte la diff et sollicite accord avant toute installation sur le S23. Sauvegarder la version fonctionnelle ; tenir compte des signatures d'APK GitHub Actions.

## 7. Critères de réussite

1. Le téléphone conserve l'accès **Wi-Fi + données mobiles** indépendamment de la simulation ; ne pas se limiter à voir les barres réseau.
2. L'icône satellite affichée correspond visuellement à la cible Samsung, avec la même forme/les mêmes ondes ; description claire de son origine drawable.
3. L'icône peut être activée/désactivée et les niveaux réglés comme avant. **Pas de modification de la connexion réseau réelle.**
4. Codex fournit un **rapport d'ADB automatisé** et des preuves de comparaison 2/4 vs 4/4, puis une proposition de patch minimal avec procédure de retour arrière.
5. **Aucune publication communautaire** ni remplacement de la release existante sans confirmation.

## 8. Démarrage suggéré à Codex

> Lis `docs/CODEX_HANDOFF.md`, inspecte `ModuleHook.java` et le workflow Android, puis travaille dans une branche de diagnostic. Je suis sur Windows et mon Galaxy S23 KernelSU/LSPosed peut être connecté en USB. Installe/repère ADB avec mon accord, détecte l'appareil, exécute toi-même les diagnostics en lecture seule et compare les icônes satellites à 2/4 et 4/4. Ne demande pas de recopier des commandes Termux. Ne modifie ni le modem ni les paramètres réseau ; demande mon accord avant toute installation, changement d'état ou publication. Rends un rapport avant de proposer un patch.
