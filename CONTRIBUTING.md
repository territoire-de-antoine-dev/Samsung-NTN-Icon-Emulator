# Contribuer / Contributing 🛰️

## Français

Les contributions visant à améliorer la **compatibilité visuelle One UI**, les traductions, la documentation et la fiabilité du module LSPosed sont bienvenues.

1. Ouvrez une issue en décrivant votre **modèle Samsung**, votre version d'Android/One UI, votre version de LSPosed et les étapes de reproduction.
2. Vérifiez que les journaux publiés ne contiennent **aucune information personnelle**, aucun identifiant de compte, adresse ou jeton.
3. Privilégiez des modifications **purement graphiques** : l’application ne doit pas altérer les paramètres téléphoniques, le modem, la SIM ou les services de secours.
4. Pour une modification de texte, mettez à jour les ressources **anglaises** (`app/src/main/res/values/strings.xml`) et **françaises** (`app/src/main/res/values-fr/strings.xml`).
5. Construisez l’APK avec `gradle :app:assembleDebug` (Gradle 8.11.1, JDK 17, SDK Android 35), ou utilisez le workflow **Compiler APK**.
6. Indiquez les limites de compatibilité dans la demande de fusion. Une compilation réussie n’est pas une validation sur appareil.

### Publication / signature

Le workflow GitHub Actions construit une APK sur chaque push vers `main`. Pour une release, un mainteneur utilise un commit comportant **`[release]`**, après avoir mis à jour la version Gradle et les notes `RELEASE_NOTES.md`. Le job de publication crée un tag `vX.Y.Z` et joint l’APK dans **GitHub Releases**.

**Important** : les builds sont actuellement signés avec une clé debug du runner, qui peut changer. Ne jamais commiter une clé privée, un keystore de production ou un secret de signature dans ce dépôt public. Pour les mises à jour pérennes, configurer ultérieurement un stockage sécurisé pour les secrets GitHub Actions et une clé de publication stable.

Le code source est sous licence MIT ; les droits sur les marques et visuels tiers restent réservés à leurs titulaires.

## English

Contributions are welcome for **One UI visual compatibility**, localization, documentation and reliable LSPosed behavior.

1. Open an issue including your **Samsung model**, Android/One UI build, LSPosed version, and reproduction steps.
2. **Remove personal information**, account identifiers, addresses, and tokens from logs before posting them.
3. Keep changes **display-only**: this app must not alter telephony, SIM, modem, radio configuration, or emergency services.
4. Update both localized resources when editing text: **English** at `app/src/main/res/values/strings.xml` and **French** at `app/src/main/res/values-fr/strings.xml`.
5. Build using `gradle :app:assembleDebug` with Gradle 8.11.1, JDK 17, Android SDK 35, or use the **Compiler APK** workflow.
6. Report real-device compatibility limits in pull requests; a successful compilation is not a device test.

### Publishing and signing

GitHub Actions builds an APK on every push to `main`. After updating the Gradle version and `RELEASE_NOTES.md`, a maintainer can create a release with a commit message containing **`[release]`**. The release job creates the `vX.Y.Z` tag and attaches the APK to **GitHub Releases**.

**Important**: APKs are currently signed with the runner's ephemeral debug key. Do not commit any private signing key, production keystore or signing secret into this public repo. A stable, securely stored release key will be required for reliable in-place updates.

Source code uses the MIT license; third-party trademarks, branding and graphics are not licensed by this project.
