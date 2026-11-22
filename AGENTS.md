# Instructions pour Codex / repository agents

- This is a public Android / LSPosed project for a **display-only satellite icon demonstration**, never real satellite connectivity, operator changes or emergency SOS access.
- For work on the Samsung satellite status-bar icon, **read [docs/CODEX_HANDOFF.md](docs/CODEX_HANDOFF.md) first**; it contains the historical debugging context and Windows ADB testing plan.
- The known-good icon display was observed on a rooted Samsung Galaxy S23 SM-S911U1 with KernelSU and LSPosed. The latest release is v2.2.0 and cross-device compatibility is not guaranteed.
- Preserve package `fr.territoire.satvisual`, `com.android.systemui` LSPosed scope, and the independent demo icon slot `satellite_visual_demo`. Never repurpose the real `satellite` status slot or misrepresent satellite connectivity.
- **Start with read-only diagnostics.** Obtain explicit user approval before ADB privilege escalation, modifying phone state, rebooting, app install/uninstall, pushing commits, or publishing a release.
- Protect private device data. No credentials, IMEI, complete logcat dumps, network identifiers, or pulled proprietary Samsung APKs in this public repository.
- Test the already-existing graphic level 2/4 vs 4/4 before changing resource selection. Keep a tested fallback, use a separate branch, and do not modify the native holder initialization unnecessarily.
- The user wants Codex **on Windows PC with ADB** to run diagnostics, not to ask for repeated Termux copy/paste.
