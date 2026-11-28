# Google AdMob सेटअप गाइड — सरल हिन्दी में (`ADMOB_STEPS.md`)

यह गाइड आपको शुरू से अंत तक सरल भाषा में बताती है कि **School Report Card** ऐप के लिए Google AdMob कैसे सेटअप करें, 4 GitHub Secrets कैसे डालें, स्कूल के लिए अनुपयुक्त विज्ञापन कैसे बंद करें, और टेस्टिंग के दौरान अपना AdMob खाता सुरक्षित कैसे रखें।

> **महत्वपूर्ण सुरक्षा बात:**  
> - Debug ऐप (टेस्ट APK) में केवल **Google के आधिकारिक टेस्ट विज्ञापन** ही चलते हैं।  
> - Release बिल्ड (Play Store `.aab`) में असली AdMob IDs सीधे **GitHub Secrets** से आते हैं — इन्हें कभी कोड या Git में न लिखें।  
> - यदि GitHub Secrets खाली हों, तो भी ऐप बिना किसी क्रैश के चलेगा (बस विज्ञापन बंद रहेंगे)।

---

## चरण 1: Google AdMob खाता (Account) बनाएँ

1. अपने फोन या कंप्यूटर के ब्राउज़र में खोलें: [https://admob.google.com](https://admob.google.com)
2. अपने उसी Google खाते से साइन-इन करें जिसे आप ऐप के मालिक (Owner) के रूप में उपयोग करते हैं।
3. देश (**India**), समय क्षेत्र (**Kolkata / IST**), और मुद्रा (**INR ₹** या **USD**) चुनें।
4. नियम और शर्तें स्वीकार करके खाता बनाएँ।
5. आगे चलकर **Payments** सेक्शन में अपना नाम, पता और बैंक जानकारी भरनी होगी (पेमेंट मिलने से पहले Google पते का PIN वेरिफ़ाई करता है)।

---

## चरण 2: AdMob में अपना Android ऐप जोड़ें (`ADMOB_APP_ID` पाएँ)

1. AdMob के बाएँ मेनू में **Apps → Add app** पर टैप करें।
2. प्लेटफ़ॉर्म चुनें: **Android**।
3. सवाल पूछेगा *"Is the app listed on a supported app store?"*:
   - यदि ऐप अभी Play Store पर पब्लिश नहीं हुआ है तो **No** चुनें (पब्लिश होने के बाद आप इसे Play Store से लिंक कर सकते हैं)।
4. ऐप का नाम लिखें: `School Report Card` और **Add app** पर टैप करें।
5. अब **Apps → App settings** में जाएँ और **App ID** कॉपी कर लें।  
   यह कुछ इस तरह दिखेगी: `ca-app-pub-1234567890123456~1234567890` (बीच में `~` चिह्न होता है)।  
   ➡️ यही आपका **`ADMOB_APP_ID`** है।

---

## चरण 3: 3 Ad Units बनाएँ (Banner, Interstitial, Rewarded)

AdMob के बाएँ मेनू में **Apps → School Report Card → Ad units → Add ad unit** पर जाएँ और नीचे दिए गए **ठीक 3 Ad Units** बनाएँ:

1. **Banner (बैनर विज्ञापन)**
   - **Banner** चुनें → नाम रखें `Home_And_List_Banner` → **Create ad unit**।
   - मिली हुई **Ad unit ID** कॉपी करें (`ca-app-pub-................/..........`, बीच में `/` होता है)।
   - ➡️ यही आपका **`ADMOB_BANNER_ID`** है।
   - *(यह ऐप में केवल Today होम, संगठन चयन, स्कूल चयन, छात्र सूची, शिक्षक सूची, रिपोर्ट सूची और टाइमटेबल व्यू स्क्रीन के नीचे एक छोटे Adaptive Banner के रूप में दिखता है — कभी भी डेटा एंट्री, फीस, अंक, वेतन, उपस्थिति या खुले PDF पर नहीं।)*

2. **Interstitial (पूर्ण-स्क्रीन विज्ञापन)**
   - **Interstitial** चुनें → नाम रखें `Task_Finished_Interstitial` → **Create ad unit**।
   - मिली हुई **Ad unit ID** कॉपी करें।
   - ➡️ यही आपका **`ADMOB_INTERSTITIAL_ID`** है।
   - *(यह केवल काम पूरा होने के बाद — जैसे पूर्ण PDF प्रीव्यू बंद करने या सूची से वापस आने पर — 8 मिनट में अधिकतम 1 बार और दिन में अधिकतम 8 बार ही दिख सकता है।)*

3. **Rewarded (रिवॉर्ड विज्ञापन — विज्ञापन-रहित समय के लिए)**
   - **Rewarded** चुनें → नाम रखें `AdFree_Time_Rewarded` → **Create ad unit**।
   - Reward amount = `1`, Reward item = `ad_free_step` रखें → **Create ad unit**।
   - *(वैकल्पिक Phase 16B सर्वर सत्यापन / SSV: `Deploy Firebase` चलाने के बाद आप **Advanced settings → Server-side verification → Callback URL** में `https://asia-south1-<PROJECT_ID>.cloudfunctions.net/admobRewardedSsv` डाल सकते हैं — विस्तृत चरण `PLAY_BILLING_STEPS.md` में हैं।)*
   - मिली हुई **Ad unit ID** कॉपी करें।
   - ➡️ यही आपका **`ADMOB_REWARDED_ID`** है।
   - *(यूज़र अपनी इच्छा से 1–4 विज्ञापन देखकर 2 से 24 घंटे का **विज्ञापन-रहित समय** पा सकते हैं।)*

---

## चरण 4: GitHub Secrets में चारों IDs डालें

अपने GitHub रिपॉज़िटरी में जाएँ: **Settings → Secrets and variables → Actions → New repository secret** और नीचे दिए गए चारों नाम **बिल्कुल इसी स्पेलिंग में** जोड़ें:

| Secret का नाम | क्या मान (Value) डालें |
|---|---|
| `ADMOB_APP_ID` | चरण 2 से कॉपी की गई App ID (`ca-app-pub-...~...`) |
| `ADMOB_BANNER_ID` | चरण 3 से Banner Ad Unit ID (`ca-app-pub-.../...`) |
| `ADMOB_INTERSTITIAL_ID` | चरण 3 से Interstitial Ad Unit ID (`ca-app-pub-.../...`) |
| `ADMOB_REWARDED_ID` | चरण 3 से Rewarded Ad Unit ID (`ca-app-pub-.../...`) |

जब आप अगली बार **Actions → Build Signed Release AAB** चलाएँगे, तो ये चारों IDs अपने-आप Release `.aab` में जुड़ जाएँगे।

---

## चरण 5: स्कूल ऐप के लिए संवेदनशील विज्ञापन श्रेणियाँ (Sensitive Categories) ब्लॉक करें (अनिवार्य)

यह स्कूल स्टाफ का कार्यस्थल (workplace) ऐप है। कोड में पहले ही `MAX_AD_CONTENT_RATING_G` और `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` सेट है, लेकिन **AdMob Console में भी नीचे दी गई सभी श्रेणियाँ बंद (Block) करना अनिवार्य है**:

1. AdMob के बाएँ मेनू में जाएँ: **Blocking controls → Sensitive categories** (या **Apps → School Report Card → Blocking controls → Sensitive categories**)।
2. इन **सभी 6 श्रेणियों को BLOCKED (बंद)** करें:
   - ❌ **Gambling & Betting** (जुआ / सट्टा / कसीनो / फैंटेसी बेटिंग)
   - ❌ **Dating** (डेटिंग)
   - ❌ **Alcohol** (शराब / मदिरा)
   - ❌ **Politics** (राजनीतिक विज्ञापन)
   - ❌ **Religion** (धार्मिक विज्ञापन)
   - ❌ **Sexually Suggestive / Adult / Sensationalism** (वयस्क या आपत्तिजनक सामग्री)
3. साथ ही **Ad content rating** में अधिकतम रेटिंग **G (General audiences)** चुनें।
4. नीचे **Save** पर टैप करें।

---

## चरण 6: Google UMP सहमति (Consent) और Play Console अपडेट

1. **AdMob Privacy & messaging (UMP):**
   - AdMob में **Privacy & messaging** खोलें और यूरोपीय/यूके नियमों (GDPR) के लिए **European regulations** संदेश चालू कर दें (यदि आप केवल भारत में प्रकाशित कर रहे हैं तो भी इसे चालू रखना सुरक्षित है — ऐप का Google UMP SDK अपने-आप केवल उन्हीं देशों में फॉर्म दिखाता है जहाँ कानूनन ज़रूरी हो)।
2. **Play Console → App content:**
   - **Ads:** चुनें **"Yes, my app contains ads"**।
   - **Advertising ID:** चुनें **"Yes"** → उद्देश्य चुनें **"Advertising or marketing"**।
   - **Data safety:** `PLAY_DATA_SAFETY.md` में दिए गए टेबल के अनुसार अपडेट करें।
   - **Privacy Policy URL:** अपडेट की गई `public/privacy.html` होस्ट करें।

---

## चरण 7 (वैकल्पिक): रिमोट ऑन/ऑफ कंट्रोल (`appConfig/adsConfig`)

यदि आप कभी भी बिना ऐप अपडेट किए किसी विज्ञापन स्थान को तुरंत बंद या चालू करना चाहें, तो Firebase Console → Firestore में डॉक्यूमेंट बनाएँ/अपडेट करें:

- **Collection:** `appConfig`
- **Document ID:** `adsConfig`
- **Fields (सभी Boolean):**
  - `globalEnabled`: `true` (सभी विज्ञापन बंद करने के लिए `false`)
  - `todayBannerEnabled`: `true`
  - `orgChooserBannerEnabled`: `true`
  - `schoolChooserBannerEnabled`: `true`
  - `studentListBannerEnabled`: `true`
  - `teacherListBannerEnabled`: `true`
  - `reportListBannerEnabled`: `true`
  - `timetableViewBannerEnabled`: `true`
  - `pdfCloseInterstitialEnabled`: `true`
  - `rewardedAdFreeEnabled`: `true`
  - `requireServerVerifiedRewards`: `false` *(AdMob Console में SSV Callback URL वेरिफाई करने के बाद `true` करें)*

> **सुरक्षा:** `firestore.rules` में `appConfig/adsConfig` को केवल आपका **App Owner अकाउंट** ही बदल सकता है (सामान्य यूज़र केवल पढ़ सकते हैं)। `firestore.rules` में `isAppOwner()` के अंदर अपना ईमेल या Firebase Auth custom claim (`appOwner == true`) सेट कर लें।

---

## चरण 8: टेस्टिंग और सबसे ज़रूरी सावधानी (खाता बंद होने से बचाएँ!)

1. **हमेशा Test Ads से टेस्ट करें:**
   - इस प्रोजेक्ट का **Debug APK** अपने-आप केवल Google के आधिकारिक **Test Ad Unit IDs** का उपयोग करता है। टेस्ट विज्ञापनों पर ऊपर **"Test Ad"** लिखा दिखता है।
   - यदि आप Release बिल्ड अपने फोन पर टेस्ट कर रहे हों, तो AdMob Console → **Settings → Test devices → Add test device** में अपने फोन की **Advertising ID** जोड़ लें।
2. **⚠️ अपने असली (Live) विज्ञापनों पर कभी क्लिक न करें:**
   - असली Play Store/Release विज्ञापन दिखने पर उन्हें कभी भी खुद बार-बार न खोलें और न क्लिक करें, और न ही स्टाफ या दोस्तों से क्लिक करने को कहें।
   - ऐसा करने पर Google इसे **Invalid Traffic / Self-Clicking** मानकर आपका **AdMob खाता स्थायी रूप से बंद (Ban)** कर सकता है।
3. **सब्सक्रिप्शन टेस्टिंग (केवल Debug बिल्ड):**
   - Debug बिल्ड में **Settings** के सबसे नीचे **"डेवलपर विज्ञापन टेस्ट (केवल Debug बिल्ड)"** कार्ड मिलता है जहाँ आप लोकल रूप से **सब्सक्रिप्शन ON/OFF** और **24 घंटे नया-इंस्टॉल इंतजार** टेस्ट कर सकते हैं। यह कार्ड Release बिल्ड में मौजूद ही नहीं होता।
