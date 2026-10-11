# Neda (الأذان / Adhan)

Neda is a light Android app for the adhan and a few other alarms. Prayer times are calculated on the phone from latitude and longitude. There is no account, no subscription, and no ads.

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

## English

### What it does

- Five daily prayer alarms, plus sunrise shown as a time that does not ring.
- A countdown to the next prayer, the Hijri date, and one weather line (temperature and rain chance).
- A reminder before the adhan and an iqamah alarm after it, with durations you choose.
- Clock alarms, place alarms, and a focus timer.
- A quiet evening note for days such as Ashura, the White Days, Arafah, the two Eids, and published eclipses through 2028.
- A home-screen widget with a photo from your gallery, and shortcuts for prayer and settings.

### How it differs

The adhan uses the alarm stream, so it can ring in silent mode and vibrate mode when the alarm volume is up. Times are calculated on the device. Nothing in the app requires an account, and there are no ads.

### Privacy

The phone sends your latitude and longitude to Open-Meteo, over HTTPS, only to fetch the weather line. There is no API key and no other network call.

Prayer times, the Hijri date, and alarms stay on the device. Background location is used only for place alarms. Typed coordinates or a chosen city are enough for the adhan.

### Permissions

| Permission | Why |
| --- | --- |
| Exact alarms | Ring the adhan and clock alarms at the right minute. |
| Notifications | Show the alarm and the quiet occasion notes. |
| Full-screen intent | Open the ringing screen when the alarm is due. |
| Notification policy | Let the alarm channel bypass Do Not Disturb after you allow it. |
| Ignore battery optimizations | Ask the system not to delay a saved alarm. |
| Boot completed | Arm saved alarms again after restart or update. |
| Vibrate and wake lock | Vibrate with the alarm and keep the CPU awake while it plays. |
| Foreground service | Keep playback running while the alarm rings. |
| Internet | The weather line only. |
| Location, including background | One reading can fill coordinates. Background location is only for place alarms. |

### Build

1. Install Android Studio with Android SDK 36, or a JDK 17+ and the SDK on the command line.
2. Open the folder that contains `settings.gradle.kts`.
3. Run the `app` configuration on Android 8 or newer.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Android Studio writes `local.properties` on first sync. That file is not committed. A release APK is signed only when `keystore.properties` is present. Without it, the release build stays unsigned and debug builds are unchanged.

Screenshots will live in [docs/screenshots/](docs/screenshots/). None are checked in yet.

The GitHub About text on the repository page is set by the owner in the website settings. This README is the description in the project itself.

## العربية

ندى تطبيق أندرويد خفيف للأذان وبعض المنبّهات الأخرى. تُحسب مواقيت الصلاة على الهاتف من خط العرض وخط الطول. لا حساب، ولا اشتراك، ولا إعلانات.

الرخصة: رخصة غنو العمومية الإصدار 3.0. النص في [LICENSE](LICENSE).

### ماذا يفعل

- أذان للصلوات الخمس، والشروق وقت ظاهر لا يرن.
- عدّ تنازلي للصلاة التالية، والتاريخ الهجري، وسطر طقس واحد (الحرارة واحتمال المطر).
- تذكير قبل الأذان وإقامة بعده، بمدد تختارها.
- منبه ساعة، ومنبه عند دخول مكان، ومؤقت تركيز.
- تنبيه هادئ مساء اليوم السابق لمناسبات مثل عاشوراء والأيام البيض وعرفة والعيدين والخسوف والكسوف المنشورة حتى 2028.
- أداة للشاشة الرئيسية بصورتك من المعرض، واختصاران للصلاة والإعدادات.

### بماذا يختلف

الأذان على مسار المنبّه، فيرن مع الوضع الصامت ووضع الاهتزاز إذا كان صوت المنبّه مرفوعًا. الحساب على الجهاز. لا يحتاج التطبيق إلى حساب، ولا يعرض إعلانات.

### الخصوصية

يرسل الهاتف خط العرض وخط الطول إلى Open-Meteo عبر HTTPS لسطر الطقس فقط. لا مفتاح، ولا اتصال آخر.

مواقيت الصلاة والتاريخ الهجري والمنبهات تبقى على الجهاز. الموقع في الخلفية لمنبهات الأماكن فقط. الإحداثيات المكتوبة أو المدينة المختارة تكفي للأذان.

### الأذونات

| الإذن | السبب |
| --- | --- |
| المنبهات الدقيقة | رنين الأذان ومنبه الساعة في دقيقته. |
| الإشعارات | إظهار المنبه وتنبيهات المناسبات الهادئة. |
| ملء الشاشة | فتح شاشة الرنين عند حلول الوقت. |
| سياسة الإشعارات | تجاوز عدم الإزعاج بعد أن تسمح بذلك. |
| تجاهل تحسين البطارية | طلب عدم تأخير المنبه المحفوظ. |
| اكتمال الإقلاع | إعادة ضبط المنبهات بعد إعادة التشغيل أو التحديث. |
| الاهتزاز وإبقاء المعالج | الاهتزاز مع الرنين وإبقاؤه يعمل أثناء الصوت. |
| خدمة أمامية | استمرار التشغيل ما دام المنبه يرن. |
| الإنترنت | سطر الطقس فقط. |
| الموقع، بما فيه في الخلفية | قراءة واحدة قد تملأ الإحداثيات. الموقع في الخلفية لمنبهات الأماكن فقط. |

### البناء

1. ثبّت أندرويد ستوديو مع حزمة SDK 36، أو JDK 17 مع الحزمة من سطر الأوامر.
2. افتح المجلد الذي فيه `settings.gradle.kts`.
3. شغّل إعداد `app` على أندرويد 8 أو أحدث.

أوامر البناء في القسم الإنجليزي أعلاه. يُوقَّع إصدار النشر فقط إذا وُجد `keystore.properties`. من دونه يبقى إصدار النشر بلا توقيع، ولا يتغير بناء التصحيح.

لقطات الشاشة ستكون في [docs/screenshots/](docs/screenshots/). لا توجد لقطات في المستودع بعد.

وصف «حول» في صفحة GitHub يضبطه صاحب المستودع من إعدادات الموقع. هذا الملف هو وصف المشروع داخل المستودع.
