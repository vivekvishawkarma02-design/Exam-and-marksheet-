# Google Play Store पर ऐप पब्लिश करने के आसान कदम (सरल हिन्दी में गाइड)

यह गाइड आपको शुरू से अंत तक बताती है कि आप अपने फोन और GitHub की मदद से **School Report Card** ऐप को Google Play Console पर कैसे डालें।

---

## चरण 1: अपनी संपर्क जानकारी और वेब पेज तैयार करें (ज़्यादातर काम पूरा हो चुका है)

1. रिपॉज़िटरी के `public/` फ़ोल्डर में तीन वेब पेज बन चुके हैं:
   - `public/privacy.html` (गोपनीयता नीति)
   - `public/delete-account.html` (अकाउंट और डेटा हटाने की जानकारी)
   - `public/support.html` (सहायता पेज)
2. इन तीनों फाइलों में जहां `[YOUR_CONTACT_EMAIL]` लिखा है, वहाँ अपना असली सपोर्ट ईमेल लिखें (और `privacy.html` में `[YOUR_UPDATE_DATE]` की जगह आज की तारीख लिखें)।
3. इन फाइलों को **GitHub Pages**, **Firebase Hosting** या अपनी स्कूल वेबसाइट पर होस्ट करें ताकि हर फाइल का `https://...` लिंक खुल सके।
4. उन असली लिंक और अपने ईमेल को कोड में यहाँ भी डालें:
   - फाइल: `app/src/main/java/com/example/domain/LegalConfig.kt` (`CONTACT_EMAIL_PLACEHOLDER`, `PRIVACY_POLICY_URL`, `DELETE_ACCOUNT_URL`, `SUPPORT_URL`)

---

## चरण 2: फोन से ही अपलोड कीस्टोर (Upload Keystore) बनाएँ (बिना कंप्यूटर के)

Play Store के लिए एक स्थायी **Upload Keystore** चाहिए होता है। इसके लिए रिपॉज़िटरी में `.github/workflows/create-upload-keystore.yml` वर्कफ़्लो जोड़ दिया गया है।

1. यहाँ AI Studio में **Push to GitHub** बटन दबाकर ताज़ा कोड GitHub पर भेजें।
2. अपने फोन के ब्राउज़र में GitHub रिपॉज़िटरी खोलें → **Actions** टैब में जाएँ → **Create Upload Keystore (One-Time)** चुनें → **Run workflow** पर टैप करें।
3. काम पूरा होने पर उसी रन के अंदर:
   - **Summary** में आपको **SHA-1** और **SHA-256** दिखेंगे — इन्हें कॉपी कर लें।
   - नीचे **Artifacts** सेक्शन में `upload-keystore-private-1day` नाम की ZIP फ़ाइल **तुरंत डाउनलोड कर लें** (यह सुरक्षा के लिए 1 दिन बाद अपने आप हट जाएगी)।
4. उस ZIP को खोलने पर अंदर `my-upload-key.jks` और `PASSWORDS_AND_BASE64.txt` मिलेंगी।
   - **महत्वपूर्ण चेतावनी:** इस फ़ाइल और पासवर्ड को कम से कम दो सुरक्षित जगहों (जैसे पासवर्ड मैनेजर + सुरक्षित निजी बैकअप) में संभाल कर रखें। यदि यह खो गई, तो Play Store पर नया अपडेट डालने के लिए Google Support से की-रीसेट (Key Reset) कराना पड़ता है।

---

## चरण 3: GitHub Secrets और Firebase में SHA जोड़ें

1. **Firebase Console** (अपने प्रोजेक्ट की Settings → Android App — पैकेज नाम `com.vivek.schoolreportcard`) में जाएँ और:
   - Upload Keystore का **SHA-1** और **SHA-256** जोड़ें।
   - जब ऐप Google Play App Signing से लाइव हो जाए, तो Play Console के **App integrity → App signing** से मिलने वाला **App signing key certificate (SHA-1 और SHA-256)** भी Firebase Console में जोड़ना न भूलें — तभी Play Store से डाउनलोड किए गए ऐप में Google Sign-In काम करेगा!
   - ताज़ा `google-services.json` डाउनलोड करें (जिसमें `com.vivek.schoolreportcard` क्लाइंट शामिल हो)।
2. **GitHub रिपॉज़िटरी → Settings → Secrets and variables → Actions** में ये Secrets जोड़ें:
   - `UPLOAD_KEYSTORE_BASE64`: `PASSWORDS_AND_BASE64.txt` में दिया गया बेस64 कोड
   - `STORE_PASSWORD`: टेक्स्ट फ़ाइल में दिया स्टोर पासवर्ड
   - `KEY_PASSWORD`: टेक्स्ट फ़ाइल में दिया की पासवर्ड
   - `GOOGLE_SERVICES_JSON`: आपकी असली `google-services.json` फ़ाइल की पूरी सामग्री (ताकि रिलीज़ बिल्ड असली Firebase से जुड़े)

---

## चरण 4: Signed Release AAB बनाएँ (.aab फ़ाइल)

1. GitHub → **Actions** → **Build Signed Release AAB** चुनें → **Run workflow** पर टैप करें (या नया `v1.0.0` टैग पुश करें)।
2. कुछ मिनट में बिल्ड पूरा होगा। नीचे **Artifacts** से `school-report-card-release-aab` डाउनलोड करें (इसमें `app-release.aab` होगी)।

---

## चरण 5: Google Play Console सेटअप (सही क्रम में)

1. Google Play Console में **Create app** दबाएँ:
   - नाम: `School Report Card`
   - ऐप का प्रकार: `App` | मुफ़्त (`Free`)
2. **Policy and programs → App content** को पूरा करें:
   - **Privacy Policy:** चरण 1 वाला `privacy.html` लिंक डालें।
   - **App Access (ज़रूरी!):** *"All or some functionality is restricted"* चुनें और Google Play की रिव्यू टीम के लिए एक **डेमो ईमेल + पासवर्ड लॉगिन** और **एक टेस्ट Organisation Code** दें ताकि वे ऐप के अंदर जाकर जांच सकें (क्योंकि बिना साइन-इन के सिर्फ लॉगिन स्क्रीन दिखती है)।
   - **Ads:** *"No, my app does not contain ads"* चुनें।
   - **Content rating:** प्रश्नावली भरें (`store_listing/store_listing.md` में उत्तर दिए हैं)।
   - **Target audience:** केवल **`18 and over`** (वयस्क / स्कूल स्टाफ) चुनें। बच्चों की उम्र वाला कोई विकल्प **न चुनें**।
   - **Data safety:** `PLAY_DATA_SAFETY.md` फ़ाइल देखकर हर सवाल का ठीक वही उत्तर भरें, और `delete-account.html` का लिंक डालें।
   - **Government apps / Financial features / News / Health:** `No` चुनें।
3. **Store listing (स्टोर पेज):**
   - `store_listing/store_listing.md` से शीर्षक, छोटा विवरण और पूरा विवरण कॉपी करें।
   - `store_listing/icon_512x512.png` (512×512 आइकन) अपलोड करें।
   - `store_listing/feature_graphic_1024x500.png` (1024×500 बैनर) अपलोड करें।
   - डेमो डेटा के साथ 6–8 साफ स्क्रीनशॉट (1080×1920 या 1080×2400) लेकर अपलोड करें (स्क्रीनशॉट में असली छात्रों के नाम/नंबर न दिखें)।
4. **Testing / Production:**
   - **ज़रूरी ध्यान दें:** अगर आपका नया *Personal* Play Console अकाउंट है (November 2023 के बाद बना), तो Production से पहले **Closed Testing** में कम से कम 12 टेस्टर्स को 14 दिन तक लगातार ऑप्ट-इन रखना Play Store का नियम है। यदि Organization अकाउंट है, तो सीधे Production या Closed Testing चुन सकते हैं।
   - **Release → Production (या Closed testing) → Create new release** में अपनी `app-release.aab` अपलोड करें और Play Console पर **Let Google manage and protect your app signing key** (Google Play App Signing) चालू रखें।
   - रिलीज़ नोट्स `store_listing/store_listing.md` से डालें और **Send for review** दबाएँ।

---

## आपके (Owner के) निर्णय और बदली जाने वाली चीजें (Checklist)

- [ ] `public/privacy.html`, `public/delete-account.html`, `public/support.html` में `[YOUR_CONTACT_EMAIL]` और `[YOUR_UPDATE_DATE]` भरें और वेब पर होस्ट करें।
- [ ] `app/src/main/java/com/example/domain/LegalConfig.kt` में होस्टेड URL और संपर्क ईमेल अपडेट करें।
- [ ] कानूनी/प्राइवेसी शर्तों की अंतिम जांच किसी जानकार से ज़रूर कराएँ (विशेषकर यदि आपका स्कूल बोर्ड अलग नियम मांगता हो)।
- [ ] Play Console के **App access** सेक्शन में रिव्यूअर के लिए एक डेमो अकाउंट (Email + Password, जो ईमेल से सत्यापित हो और किसी डेमो स्कूल संगठन का एडमिन हो) तैयार करके दें।
- [ ] Play App Signing चालू होने के बाद Play Console के **App signing SHA-1 और SHA-256** को भी Firebase Console में जोड़ें।
- [ ] स्टोर स्क्रीनशॉट केवल डेमो डेटा के साथ लें (असली छात्रों का डेटा नहीं)।
- [ ] `store_listing/store_listing.md` और `PLAY_DATA_SAFETY.md` में अंतिम नाम/ब्रांडिंग की पुष्टि करें।
