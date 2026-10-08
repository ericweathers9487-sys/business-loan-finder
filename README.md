# Business Loan Finder (beta)

An Android app that helps home services businesses see which business loans they're likely to fit, explains why, and sends their details to a lender only when they choose to. It is loan discovery and referral, not a loan application or approval.

## How it works

1. Eight quick questions, all tap-to-answer except the two dollar amounts.
2. Every lender product is checked against its own rules. Each result is one of three states: **likely eligible**, **likely not eligible**, or **more info needed**.
3. Matches show a fit score out of 100, a likely amount range, and the reasons behind the score. Every point of the score has a written reason.
4. Non-matches show what's blocking them and, when possible, what would change the result.
5. The borrower picks a lender, enters contact details, and checks a consent box for that specific lender. Only then is a lead created. Leads below a lender's minimum fit score are never sent.
6. The lead server checks the lead again with the same rules, stores it encrypted, and alerts the lender. The lender sees an anonymized summary and gets the borrower's contact details only after accepting.

## What's in the project

| Folder | What it does |
|---|---|
| `core/.../matching/` | The eligibility engine, lead routing gate, and disclosures. Plain Kotlin, shared by the app and the server, so both run the exact same rules. |
| `core/.../catalog/SampleProducts.kt` | Four **sample** lenders for testing. Not real. Replace them. The app and server both read this list. |
| `core/.../api/` | The format the app uses to send a lead to the server. |
| `app/src/main/java/.../leads/` | Where leads go. With no server set, leads stay on the phone (test mode). |
| `app/src/main/java/.../ui/` | The screens. Colors, type, and shapes live in `Theme.kt`; light and dark mode are both supported. |
| `app/src/main/res/font/` | Plus Jakarta Sans, the app's typeface. Free under the SIL Open Font License; the license ships in the app (`assets/licenses/`). |
| `server/` | The lead server: re-checks, stores (encrypted), audits, and routes leads to lenders. See [server/README.md](server/README.md). |
| `core/src/test/`, `server/src/test/` | Unit tests for the engine, routing gate, lead format, and server. |
| `.github/workflows/android.yml` | Builds the app and the server image on GitHub, no computer setup needed. |

## Build it on GitHub (no Android Studio needed)

1. Create a new **private** repository on github.com.
2. Upload everything in this folder, including the hidden `.github` folder. (On a computer: unzip, then drag the folder contents onto the repo's "Add file → Upload files" page, or use `git push`.)
3. Open the repo's **Actions** tab. The "Build Android app" run starts on its own.
4. When it finishes, the APK is posted under the repo's **Releases**. On an Android phone, open
   `https://github.com/<you>/<repo>/releases/latest/download/business-loan-finder.apk`
   and tap the download to install. Your phone will ask you to allow installs from your browser.
   (The same file is also on the run's page as **test-apk-install-on-your-phone**, inside a zip.)

## Or build it in Android Studio

Open this folder in Android Studio and press Run. It downloads everything it needs on first open.

## Make the Play Store file (.aab)

Google Play needs a signed bundle. You create an upload key once and keep it forever.

1. Create the key on any computer with Java installed:
   ```
   keytool -genkeypair -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Turn it into text: `base64 -w0 upload.jks` (on a Mac: `base64 -i upload.jks`).
3. In the GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**. Add:
   - `RELEASE_KEYSTORE_BASE64` – the text from step 2
   - `RELEASE_KEYSTORE_PASSWORD` – the keystore password you chose
   - `RELEASE_KEY_ALIAS` – `upload`
   - `RELEASE_KEY_PASSWORD` – the key password you chose
4. Run the workflow again (Actions → Build Android app → Run workflow). Download **play-store-bundle**. That `.aab` is what you upload to Play Console.

Back up `upload.jks` and both passwords somewhere safe. Never commit the key to the repo.

## Must-do before real borrowers use it

- [ ] **Replace the sample lenders** in `SampleProducts.kt` with your pilot lenders' real criteria, and set `isSample = false` on those. Sample products can never send a real lead.
- [ ] **Change `applicationId`** in `app/build.gradle.kts` from `com.yourco.lending` to your own (e.g. `com.yourbusiness.loanfinder`). It can't change after the first upload.
- [ ] **Host a privacy policy** at a public URL and put it in `app/src/main/res/values/strings.xml`. A starting draft is in `PRIVACY_POLICY_DRAFT.md`.
- [ ] **Lawyer review** of the disclosures and consent wording in `Disclosures` (`LeadRouting.kt`). Contacting borrowers by phone or text has its own federal consent rules. If lenders pay you per lead, check whether you need a loan broker license in the states you serve; California, for example, licenses commercial loan brokers.
- [ ] **Deploy the lead server** (`server/`) and set the `LEAD_ENDPOINT` repo variable. Steps are in [server/README.md](server/README.md). It stores each lead encrypted, keeps the audit log, re-runs the eligibility engine (never trusting the phone's result), notifies the lender, and releases contact details only after the lender accepts.
- [ ] **Set the data retention period** (`RETENTION_DAYS`, default 365) with your lawyer, and put the same number in the privacy policy.

## Google Play requirements (checked October 2026)

- **Target API level:** new apps and updates must target Android 16 (API 36) as of August 31, 2026. This project targets 36.
- **Closed test first:** personal developer accounts created after November 13, 2023 must run a closed test with at least 12 testers opted in for 14 days in a row before applying for production.
- **Financial features declaration:** required in Play Console for any app with financial features.
- **Loan policy:** Play's personal loan rules apply to lead generators too, but they're written for consumer loans and don't mention business loans. Answer the declaration accurately. If Google classes the app as loan-related, expect to add APR and repayment term details to the listing and show licensing.
- **Data safety form:** the app collects name, email, phone, and business financial answers, and shares them with a lender only at the user's request.
- **Store listing:** Finance category, screenshots, a 512×512 icon (`play-store-icon-512.png` is included), a feature graphic, and the content rating questionnaire.

Sources: [Target API requirements](https://developer.android.com/google/play/requirements/target-sdk) · [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465) · [Financial Services policy](https://support.google.com/googleplay/android-developer/answer/9876821)

## Running the tests

```
./gradlew :core:test :server:test
```

## Versions

Pinned to Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Compose BOM 2025.09.01, compileSdk/targetSdk 36. Newer Compose and Lifecycle releases from mid-2026 require compileSdk 37 and AGP 9.2+, so upgrade those together.
