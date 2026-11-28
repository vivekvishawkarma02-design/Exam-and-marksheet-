# Google Play स्कूल सब्सक्रिप्शन और Firebase डिप्लॉय गाइड — सरल हिन्दी में (`PLAY_BILLING_STEPS.md`)

यह गाइड आपको **केवल फोन के ब्राउज़र से** (बिना लैपटॉप/कंप्यूटर के) Phase 16B का पूरा सेटअप — Firebase Blaze प्लान, Google Play सब्सक्रिप्शन (`school_ad_free`), सर्वर सत्यापन (Cloud Functions), RTDN और GitHub Actions डिप्लॉय — बिल्कुल सही क्रम में करना सिखाती है।

> **संकेत:** जिन चरणों के आगे **`👤 [केवल आप कर सकते हैं]`** लिखा है, वे आपके निजी Google / Firebase / Play Console / GitHub खाते से ही हो सकते हैं।

---

## चरण 1: Firebase प्रोजेक्ट को Blaze (Pay-as-you-go) प्लान पर अपग्रेड करें `👤 [केवल आप कर सकते हैं]`

Firebase Cloud Functions (जो Google Play से खरीद की पक्की जाँच करते हैं) केवल **Blaze प्लान** पर चलते हैं।

1. अपने फोन के ब्राउज़र में खोलें: [https://console.firebase.google.com](https://console.firebase.google.com) और अपना प्रोजेक्ट चुनें।
2. बाएँ मेनू के नीचे **Spark plan → Upgrade** (या **Usage and billing → Details & settings → Modify plan**) पर टैप करें।
3. **Blaze (Pay as you go)** चुनें और अपना डेबिट/क्रेडिट कार्ड (International transactions चालू) जोड़ें।
4. **खर्च कितना होगा?**
   - Blaze में हर महीने का **मुफ्त कोटा (Free Tier)** शामिल होता है (20 लाख Function calls/माह मुफ्त)।
   - छोटे/मध्यम स्कूलों के लिए मासिक खर्च लगभग **₹0 से ₹10–₹50 प्रति माह** से भी कम रहता है।
5. **बजट अलर्ट ज़रूर लगाएँ (अनिवार्य सुरक्षा):**
   - Blaze सेटअप के दौरान (या [Google Cloud Billing → Budgets & alerts](https://console.cloud.google.com/billing/budgets) में) **₹100 या ₹200 प्रति माह** का बजट अलर्ट सेट करें ताकि 50%, 90% और 100% पर आपको ईमेल मिल जाए।

---

## चरण 2: Google Cloud में जरूरी API चालू करें और Service Account JSON बनाएँ `👤 [केवल आप कर सकते हैं]`

1. खोलें: [https://console.cloud.google.com](https://console.cloud.google.com) और ऊपर से वही Firebase प्रोजेक्ट चुनें।
2. **APIs & Services → Library** में जाएँ और ये APIs **Enable** करें:
   - **Google Play Android Developer API** (`androidpublisher.googleapis.com`)
   - **Cloud Pub/Sub API** (`pubsub.googleapis.com`)
   - **Cloud Functions API**, **Cloud Build API**, **Artifact Registry API** (यदि पहले से चालू न हों)
3. अब **IAM & Admin → Service Accounts** खोलें → **Create Service Account** पर टैप करें:
   - नाम रखें: `firebase-play-deployer`
   - **Create and Continue** दबाएँ।
   - इसे ये Roles दें:
     - **Firebase Admin** (`roles/firebase.admin`)
     - **Service Account User** (`roles/iam.serviceAccountUser`)
     - **Cloud Functions Developer** (`roles/cloudfunctions.developer`)
   - **Done** दबाएँ।
4. बनाए गए Service Account पर टैप करें → **Keys** टैब → **Add Key → Create new key → JSON → Create**।
   - एक `.json` फ़ाइल डाउनलोड होगी। इसे सुरक्षित रखें — इसे कभी भी Git में अपलोड न करें!
   - इस Service Account का ईमेल पता (जैसे `firebase-play-deployer@<project-id>.iam.gserviceaccount.com`) कॉपी कर लें।

---

## चरण 3: Google Play Console में Service Account को अनुमति दें `👤 [केवल आप कर सकते हैं]`

1. खोलें: [https://play.google.com/console](https://play.google.com/console)
2. बाएँ मेनू में **Users and permissions → Invite new users** पर टैप करें।
3. चरण 2 में कॉपी किया गया **Service Account ईमेल** डालें।
4. **App permissions** में अपना **School Report Card** ऐप चुनें और ये अनुमतियाँ टिक करें:
   - ✅ **View app information and download bulk reports (read-only)**
   - ✅ **View financial data, orders, and cancellation survey responses**
   - ✅ **Manage orders and subscriptions**
5. **Invite user** दबाएँ। (Service account को अपने-आप एक्सेस मिल जाता है — बदलाव लागू होने में कभी-कभी 15–30 मिनट लगते हैं।)

> **ध्यान दें:** इसी Service Account को Cloud Functions भी उपयोग कर सकें, इसके लिए आप इसका JSON चरण 8 में `FIREBASE_SERVICE_ACCOUNT_JSON` **और** `PLAY_SERVICE_ACCOUNT_JSON` (दोनों) GitHub Secrets में डाल सकते हैं।

---

## चरण 4: Google Play Console में सब्सक्रिप्शन प्रोडक्ट `school_ad_free` बनाएँ `👤 [केवल आप कर सकते हैं]`

> **महत्वपूर्ण:** ऐप के कोड में कोई कीमत hard-code नहीं है। ऐप सीधे Google Play से कीमत और अवधि पढ़ता है और सालाना बचत अपने-आप निकालता है।

1. Play Console में अपना ऐप खोलें → बाएँ मेनू में **Monetize with Play → Products → Subscriptions** पर जाएँ।
   *(यदि Subscriptions मेनू बंद दिखे तो पहले चरण 6 की तरह Internal Testing ट्रैक पर नया signed `.aab` अपलोड करें जिसमें Billing लाइब्रेरी शामिल है।)*
2. **Create subscription** पर टैप करें:
   - **Product ID (बिल्कुल यही लिखें):** `school_ad_free`
   - **Name:** `School Ad-Free Subscription`
   - **Create** दबाएँ।
3. अब ठीक **दो Base Plans** जोड़ें (**कोई तीसरा प्लान न बनाएँ**):
   - **Base Plan 1 — मासिक (Monthly):**
     - **Base plan ID (बिल्कुल यही लिखें):** `monthly`
     - **Auto-renewing** चुनें → **Billing period:** `1 month`
     - **Grace period:** `3 days` या `7 days` (सुझाव: `7 days`)
     - **Account hold:** चालू रखें (Play का डिफ़ॉल्ट `30 days`)
     - **Prices → Set prices:** भारत (India) के लिए **₹200** (और अन्य देशों के लिए अपनी इच्छा अनुसार) सेट करें।
     - **Save** → **Activate** दबाएँ।
   - **Base Plan 2 — वार्षिक (Yearly):**
     - **Base plan ID (बिल्कुल यही लिखें):** `yearly`
     - **Auto-renewing** चुनें → **Billing period:** `1 year`
     - **Grace period:** `7 days`
     - **Account hold:** चालू रखें
     - **Prices → Set prices:** भारत (India) के लिए **₹1500** सेट करें।
     - **Save** → **Activate** दबाएँ।

---

## चरण 5: Play Console में License Testers जोड़ें `👤 [केवल आप कर सकते हैं]`

License Testers बिना असली पैसे कटे Google Play टेस्ट कार्ड से खरीद, रिन्यूअल (हर 5 मिनट में), कैंसिल और रिफंड टेस्ट कर सकते हैं:

1. Play Console के मुख्य मेनू (All apps) में जाएँ → **Settings → License testing**।
2. **License testers** में अपना Gmail पता (और जिन टेस्ट फोन पर जाँच करनी है उनके Gmail पते) जोड़ें।
3. **License response** को `RESPOND_NORMALLY` रखें और **Save changes** दबाएँ।

---

## चरण 6: Signed Release `.aab` को Internal Testing ट्रैक पर अपलोड करें `👤 [केवल आप कर सकते हैं]`

Google Play Billing की कीमतें और खरीद केवल तब दिखती हैं जब ऐप Play Store के **Internal testing** (या Closed/Production) ट्रैक से इंस्टॉल किया गया हो।

1. AI Studio में **Push to GitHub** दबाएँ।
2. GitHub पर **Actions → Build Signed Release AAB → Run workflow** चलाएँ और तैयार `.aab` फ़ाइल डाउनलोड करें।
3. Play Console → **Testing → Internal testing → Create new release** में `.aab` अपलोड करके **Roll out to Internal testing** करें।
4. **Testers** टैब में अपना Gmail जोड़ें, **Copy link** करें और अपने फोन में उसी लिंक से Play Store से ऐप इंस्टॉल करें।

---

## चरण 7: Real-Time Developer Notifications (RTDN) Pub/Sub सेटअप करें `👤 [केवल आप कर सकते हैं]`

इससे रिन्यूअल, कैंसिल, ग्रेस पीरियड, अकाउंट होल्ड, एक्सपायरी और रिफंड की जानकारी अपने-आप सेकंडों में सर्वर तक पहुँचती है:

1. खोलें: [https://console.cloud.google.com/cloudpubsub/topic/list](https://console.cloud.google.com/cloudpubsub/topic/list) (अपना वही Firebase प्रोजेक्ट चुनें)।
2. **Create Topic** पर टैप करें:
   - **Topic ID (बिल्कुल यही लिखें):** `play-billing-rtdn`
   - **Create** दबाएँ।
3. बने हुए `play-billing-rtdn` टॉपिक के दाईं ओर **Permissions (या Info Panel → Add Principal)** खोलें:
   - **New principals:** `google-play-developer-notifications@system.gserviceaccount.com`
   - **Role:** `Pub/Sub Publisher`
   - **Save** दबाएँ।
4. अब टॉपिक का पूरा नाम कॉपी करें, जो इस तरह होगा:
   `projects/<आपका-FIREBASE_PROJECT_ID>/topics/play-billing-rtdn`
5. Play Console में अपना ऐप खोलें → **Monetize with Play → Monetization setup**।
6. **Real-time developer notifications** में वह पूरा टॉपिक नाम पेस्ट करें → **Send test notification** दबाकर जाँचें → **Save changes**।

---

## चरण 8: GitHub Secrets जोड़ें `👤 [केवल आप कर सकते हैं]`

अपने GitHub रिपॉज़िटरी में जाएँ: **Settings → Secrets and variables → Actions → New repository secret** और नीचे दिए गए Secrets जोड़ें:

| Secret का नाम | क्या मान (Value) डालें |
|---|---|
| `FIREBASE_PROJECT_ID` | आपका Firebase Project ID (जैसे `school-report-card-12345`) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | चरण 2 में डाउनलोड की गई `.json` फ़ाइल का पूरा टेक्स्ट (कॉपी-पेस्ट करें) |
| `PLAY_SERVICE_ACCOUNT_JSON` | *(सुझाया गया)* वही `.json` फ़ाइल का टेक्स्ट जिसे चरण 3 में Play Console अनुमति दी गई है |

> **सुरक्षा:** वर्कफ़्लो कभी भी इन Secrets को लॉग में प्रिंट नहीं करता और डिप्लॉय के तुरंत बाद अस्थायी फ़ाइल मिटा देता है।

---

## चरण 9: फोन से ही **"Deploy Firebase"** वर्कफ़्लो चलाएँ `👤 [केवल आप कर सकते हैं]`

> यह नया GitHub Actions वर्कफ़्लो पुराने मैनुअल Firestore Rules डिप्लॉय की जगह लेता है — एक ही बटन से **Firestore Rules**, **Indexes** और **Cloud Functions (`asia-south1`)** तीनों डिप्लॉय होते हैं।

1. अपने फोन में GitHub खोलें → अपने रिपॉज़िटरी के **Actions** टैब पर जाएँ।
2. बाईं सूची से **Deploy Firebase** चुनें।
3. **Run workflow → Run workflow** दबाएँ।
4. लगभग 2–4 मिनट में:
   - `functions/` बिल्ड और सभी यूनिट टेस्ट चलेंगे,
   - `firestore.rules`, `firestore.indexes.json` और 4 Cloud Functions (`verifyPurchase`, `handlePlayRtdn`, `createRewardedAdNonce`, `admobRewardedSsv`) आपके प्रोजेक्ट (`asia-south1`) में डिप्लॉय हो जाएँगे।
5. यदि कोई Secret छूटा होगा तो Actions में साफ हिन्दी/अंग्रेज़ी त्रुटि संदेश दिखेगा।

---

## चरण 10 (वैकल्पिक लेकिन अनुशंसित): AdMob Rewarded SSV (सर्वर सत्यापन) चालू करें `👤 [केवल आप कर सकते हैं]`

चरण 9 के बाद आपका AdMob SSV सर्वर URL तैयार हो जाता है:
`https://asia-south1-<आपका-FIREBASE_PROJECT_ID>.cloudfunctions.net/admobRewardedSsv`

1. AdMob Console खोलें → **Apps → School Report Card → Ad units → आपका Rewarded Ad Unit**।
2. **Advanced settings → Server-side verification** खोलें।
3. **Callback URL** में ऊपर वाला URL पेस्ट करें → **Verify URL** दबाएँ → **Save** करें।
4. जब AdMob में URL वेरिफाई हो जाए, तब आप चाहें तो Firestore के `appConfig/adsConfig` डॉक्यूमेंट में `requireServerVerifiedRewards: true` कर सकते हैं। जब तक यह `false` है, ऐप के 16A बाउंडेड नियम सुरक्षित रूप से काम करते रहेंगे (कोई रुकावट नहीं)।

---

## चरण 11: फोन पर पूरा सब्सक्रिप्शन टेस्ट करें `👤 [केवल आप कर सकते हैं]`

1. Internal Testing लिंक से इंस्टॉल ऐप खोलें और अपने **School Owner** खाते से लॉगिन करें।
2. **Settings → मेरे स्कूल के लिए विज्ञापन हटाएँ (Remove ads for my school)** खोलें।
3. जाँचें:
   - दोनों प्लान (**मासिक** और **वार्षिक**) असली Google Play कीमत के साथ दिख रहे हैं और वार्षिक बचत अपने-आप लिखी है।
4. **सब्सक्राइब करें** दबाएँ → Google Play टेस्ट कार्ड चुनें (**Test card, always approves**):
   - खरीद पूरी होते ही `verifyPurchase` सर्वर पर चलेगा, स्थिति **सक्रिय (Active)** हो जाएगी और अगले नवीनीकरण की तारीख दिखेगी।
   - स्कूल के **किसी भी सदस्य (Owner, Admin, Teacher, Clerk)** के फोन पर बैनर, इंटरस्टीशियल और "विज्ञापन देखें" बटन तुरंत गायब हो जाएंगे।
   - यदि कोई **गैर-ओनर (Teacher/Admin)** Settings खोले, तो उसे केवल `"आपका स्कूल विज्ञापन-रहित है"` दिखेगा (कोई खरीद/मैनेज बटन नहीं)।
5. **ऑटो-रिन्यूअल और कैंसिलेशन टेस्ट:**
   - टेस्ट सब्सक्रिप्शन कुछ मिनटों में रिन्यू होता है — **Manage subscription** दबाकर Google Play में कैंसिल करें → कुछ ही क्षणों में RTDN से ऐप में स्थिति **रद्द — अभी मान्य (Cancelled, valid until date)** दिखेगी और अवधि पूरी होने तक विज्ञापन बंद रहेंगे।
6. **रिफंड / निरस्तीकरण टेस्ट:**
   - Play Console → **Order management** में टेस्ट ऑर्डर को **Refund & Revoke** करें → RTDN से स्थिति तुरंत **निरस्त / रिफंड (Revoked)** हो जाएगी, Owner को छोटा नोटिस दिखेगा और विज्ञापन फिर से चालू हो जाएंगे।
   - ध्यान दें कि **सारा स्कूल डेटा और सभी काम के फ़ीचर हमेशा मुफ्त और खुले रहते हैं।**
