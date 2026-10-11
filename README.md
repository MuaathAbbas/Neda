# الأذان / Adhan

Android alarm app for reliable Adhan playback. Prayer times come from the batoulapps adhan library (`com.batoulapps.adhan:adhan:1.2.1`) and use only the latitude and longitude you type. The five prayers, and any clock alarms, are armed with `AlarmManager.setAlarmClock`. Playback uses `AudioAttributes.USAGE_ALARM`.

A saved place rings through the same alarm sound when you enter its circle, using `LocationManager.addProximityAlert`. Prayer math is not hand-written.

## English

### Open in Android Studio

1. Install a current Android Studio with Android SDK 36.
2. **File → Open** and choose this folder (the one that contains `settings.gradle.kts`).
3. Let Gradle sync. The app module uses Kotlin, minSdk 26, targetSdk 36.
4. Run the `app` configuration on a phone or emulator with Android 8 or newer.
5. Allow notifications when the system asks.

### Phase 2 — clock alarms

1. On first open, the app explains the three allowances. Allow notifications, exact alarms, and unrestricted battery. The card stays until each one is granted.
2. Tap **Add alarm**. Choose a time a minute or two ahead, add a label if you want, and tap **Save alarm**. Turn it off with the switch, edit it from the card, or delete it. It stays saved when you leave the app.
3. When it is due, the tone plays through `AudioAttributes.USAGE_ALARM` and the alarm notification is shown. **Stop sound** ends it. A once-alarm turns off. A daily alarm is armed for the next local time.
4. Reboot with the alarm still enabled. `BootCompletedReceiver` arms it again. The same happens after an app update, and on Huawei quick boot. A daily alarm missed while the phone was off moves to the next day.

### Phase 3 — prayer times

1. Under **Prayer times**, type latitude and longitude, or pick a city. A city only writes numbers into those fields. Leave them empty and nothing is calculated. The default method is **Umm al-Qura**. Muslim World League and Egyptian are in the same list.
2. Tap **Schedule adhan**. The list shows today’s Fajr, sunrise, Dhuhr, Asr, Maghrib, and Isha. Sunrise says it is not an alarm. Each of the five prayers can be switched off.
3. Change the method and tap **Schedule adhan** again. The times move. The coordinates in the fields are what was calculated.
4. The next prayer is armed with `setAlarmClock` and rings through the same alarm sound as a clock alarm. After it rings, and after reboot, the following day is calculated from those saved coordinates. Clock alarms from Phase 2 keep working.

### Places

1. Under **Places**, name the place and enter its latitude and longitude, or copy the prayer coordinates, or tap **Use my location**.
2. Choose a radius and tap **Save place**. Allow precise location. On Android 10 and newer, **Allow all the time** lets it ring while the app is closed.
3. Walk into that circle with location on. The same alarm sound plays. It stays quiet for 15 minutes if you are still inside. Turn the place off or delete it to stop. Reboot registers saved places again.
4. Clock alarms and prayer alarms keep working.

### Verify

1. Tap **Test alarm sound**. You should hear a short generated tone (not a recorded adhan). Playback uses `AudioAttributes.USAGE_ALARM`. If a Bluetooth headset is connected, the tone should stay on the phone speaker instead of following media audio. If you hear nothing, raise **alarm** volume, not media volume. The screen says so when the alarm stream is muted.
2. Under **Prayer times**, type a latitude and longitude (Makkah is about 21.4225 and 39.8262), or tap **Use my location** and allow location once. That reading is written into the fields. Nothing else is filled in for you. Choose a method and an Asr madhab, then tap **Schedule adhan**. The five prayers appear with their next alarm time. Sunrise is listed as not an alarm. Each prayer can be switched off. Clearing both fields and scheduling again removes the prayer alarms. After a prayer rings, and after reboot, the next time is calculated again from the saved coordinates.
3. Tap **Add alarm**. Choose a time a minute or two ahead, optionally turn on **Repeat every day**, and tap **Save alarm**. The system alarm icon should appear in the status bar. When it is due, the tone plays through `setAlarmClock`, and a high-importance alarm notification is shown. **Stop sound** ends it.
4. A once-alarm turns off after it rings. A daily alarm stays on and is armed for the next local time. The list shows that next time after you return to the app.
5. Turn an alarm off with its switch, edit it by tapping the card, or delete it. Reboot with a still-future alarm: `BootCompletedReceiver` arms it again. A daily alarm whose time was missed while the phone was off moves to the next day. Saved prayer alarms are recomputed from the stored coordinates on that same boot.
6. Optional: **Allow bypassing Do Not Disturb** opens notification-policy access. The alarm channel is already created with bypass enabled; Android honors that only after this grant.
7. **Unrestrict battery** asks Android to ignore battery optimization for this app. Some phones still need their own app-launch list as well.
8. If exact alarms are blocked, the card and saving both open the system exact-alarm screen. Grant access and save again.
9. Logcat tag: `Adhan`.

### Layout

```
com.adhan.app
  ui          alarm list, prayer place, and editor; they do not play audio or compute prayer times
  domain      Alert = TIME | PRAYER | PLACE, plus daily clock rules
  scheduler   AlertScheduler + AlarmManager.setAlarmClock
  alert       AlertPlayer (USAGE_ALARM) and the alarm notification channels
  prayer      PrayerEngine wrapping batoulapps adhan
  geofence    GeofenceEngine via LocationManager proximity alerts
  store       preference store for alarms and the saved coordinates
```

Stable contracts: `Alert`, `AlertPlayer`, `AlertScheduler`, `PrayerEngine`, `GeofenceEngine`, `AlertStore`.

### Build from the command line

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # JDK 17+
export ANDROID_HOME="$HOME/Android/Sdk"               # SDK 36
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Android Studio writes `local.properties` with `sdk.dir` on first sync. That file is not committed.

## العربية

تطبيق أندرويد لمنبهات أذان يُعتمد عليها. مواقيت الصلاة تأتي من مكتبة batoulapps adhan (`com.batoulapps.adhan:adhan:1.2.1`) وتعتمد فقط على خط العرض وخط الطول اللذين تكتبهما. الصلوات الخمس، ومنبهات الساعة، تُضبط عبر `AlarmManager.setAlarmClock`. التشغيل يستخدم `AudioAttributes.USAGE_ALARM`.

المكان المحفوظ يرن بنفس صوت المنبه عند دخول دائرته، عبر `LocationManager.addProximityAlert`. لا معادلات فلكية مكتوبة يدويًا.

### الفتح في أندرويد ستوديو

1. ثبّت أندرويد ستوديو حديثًا مع حزمة SDK 36.
2. **File → Open** واختر هذا المجلد (الذي فيه `settings.gradle.kts`).
3. انتظر مزامنة Gradle. الوحدة `app` بلغة Kotlin، و`minSdk` هو 26، و`targetSdk` هو 36.
4. شغّل إعداد `app` على هاتف أو محاكي بأندرويد ٨ أو أحدث.
5. اسمح بالإشعارات عندما يطلبها النظام.

### المرحلة ٢ — منبهات الساعة

1. عند أول فتح يشرح التطبيق الأذونات الثلاثة. اسمح بالإشعارات والمنبهات الدقيقة والبطارية بلا قيود. تبقى البطاقة حتى يُمنح كل واحد.
2. اضغط **إضافة منبه**. اختر وقتًا بعد دقيقة أو دقيقتين، وأضف تسمية إن أردت، ثم **حفظ المنبه**. أوقفه بالمفتاح، أو عدّله من البطاقة، أو احذفه. يبقى محفوظًا بعد خروجك من التطبيق.
3. عند حلول الوقت تُشغَّل النغمة عبر `AudioAttributes.USAGE_ALARM` ويظهر إشعار المنبه. **إيقاف الصوت** يقطعه. المنبه الذي يرن مرة واحدة يتوقف. المنبه اليومي يُضبط للوقت المحلي التالي.
4. أعد تشغيل الجهاز والمنبه ما زال مفعّلًا. `BootCompletedReceiver` يضبطه من جديد. يحدث الأمر نفسه بعد تحديث التطبيق، وبعد الإقلاع السريع على هواوي. المنبه اليومي الذي فات والهاتف مطفأ ينتقل إلى اليوم التالي.

### المرحلة ٣ — مواقيت الصلاة

1. تحت **مواقيت الصلاة** اكتب خط العرض وخط الطول، أو اختر مدينة. المدينة تكتب أرقامًا في الحقلين فقط. إذا بقيَا فارغين لا يُحسب شيء. الطريقة الافتراضية **أم القرى**. رابطة العالم الإسلامي والهيئة المصرية في القائمة نفسها.
2. اضغط **جدولة الأذان**. تعرض القائمة فجر اليوم والشروق والظهر والعصر والمغرب والعشاء. الشروق ليس منبهًا. يمكن إيقاف كل صلاة من الخمس.
3. غيّر الطريقة ثم اضغط **جدولة الأذان** مرة أخرى. تتحرك المواقيت. الإحداثيات في الحقلين هي ما حُسب.
4. الصلاة التالية تُضبط عبر `setAlarmClock` وترن بنفس صوت منبه الساعة. بعد الرنين، وبعد إعادة التشغيل، يُحسب اليوم التالي من الإحداثيات المحفوظة. منبهات الساعة من المرحلة ٢ تبقى تعمل.

### الأماكن

1. تحت **أماكن** سمِّ المكان واكتب خط عرضه وخط طوله، أو انسخ إحداثيات الصلاة، أو اضغط **استخدام موقعي**.
2. اختر نصف القطر ثم **حفظ المكان**. اسمح بالموقع الدقيق. في أندرويد ١٠ وما بعده، **السماح طوال الوقت** يجعله يرن والتطبيق مغلق.
3. ادخل الدائرة والموقع شغّال. يُشغَّل صوت المنبه نفسه. يبقى صامتًا ١٥ دقيقة إن بقيت داخلها. أوقف المكان أو احذفه ليهدأ. إعادة التشغيل تسجّل الأماكن المحفوظة من جديد.
4. منبهات الساعة ومنبهات الصلاة تبقى تعمل.

### التحقق

1. اضغط **تجربة صوت المنبه**. يُفترض أن تسمع نغمة قصيرة مولَّدة، وليست تسجيل أذان. التشغيل يستخدم `AudioAttributes.USAGE_ALARM`. إذا كانت سماعة بلوتوث متصلة، تبقى النغمة على سمّاعة الهاتف ولا تتبع صوت الوسائط. إذا لم تسمع شيئًا فارفع **صوت المنبه** لا صوت الوسائط. تظهر رسالة على الشاشة إذا كان مسار المنبه مكتومًا.
2. تحت **مواقيت الصلاة** اكتب خط العرض وخط الطول (مكة تقريبًا 21.4225 و 39.8262)، أو اضغط **استخدام موقعي** واسمح بالموقع مرة واحدة. تُكتب القراءة في الحقلين. لا يُملأ شيء غير ذلك نيابة عنك. اختر طريقة الحساب ومذهب العصر، ثم اضغط **جدولة الأذان**. تظهر الصلوات الخمس مع موعد المنبه التالي. الشروق معروض وليس منبهًا. يمكن إيقاف كل صلاة بمفتاحها. مسح الحقلين ثم الجدولة مرة أخرى يزيل منبهات الصلاة. بعد رنين الصلاة، وبعد إعادة التشغيل، يُحسب الموعد التالي من الإحداثيات المحفوظة.
3. اضغط **إضافة منبه**. اختر وقتًا بعد دقيقة أو دقيقتين، ويمكن تفعيل **التكرار كل يوم**، ثم **حفظ المنبه**. يظهر رمز المنبه في شريط الحالة. عند حلول الوقت تُشغَّل النغمة عبر `setAlarmClock`، ويظهر إشعار منبه بأهمية عالية. **إيقاف الصوت** يقطعه.
4. المنبه الذي يرن مرة واحدة يتوقف بعد الرنين. المنبه اليومي يبقى مفعّلًا ويُضبط للوقت المحلي التالي. تظهر القائمة هذا الموعد حين تعود إلى التطبيق.
5. أوقف منبهًا بمفتاحه، أو عدّله بالضغط على البطاقة، أو احذفه. أعد تشغيل الجهاز ومنبه ما زال في المستقبل: `BootCompletedReceiver` يضبطه من جديد. المنبه اليومي الذي فات وقته والهاتف مطفأ ينتقل إلى اليوم التالي. منبهات الصلاة المحفوظة تُعاد حسابها من الإحداثيات المخزّنة في إعادة التشغيل نفسها.
6. اختياري: **السماح بتجاوز عدم الإزعاج** يفتح الوصول إلى سياسة الإشعارات. قناة المنبه مُنشأة أصلًا بتجاوز عدم الإزعاج، ولا يطبّق أندرويد ذلك إلا بعد هذا الإذن.
7. **إلغاء تقييد البطارية** يطلب من أندرويد تجاهل تحسين البطارية لهذا التطبيق. بعض الهواتف تحتاج أيضًا قائمة تشغيل التطبيقات الخاصة بها.
8. إذا كانت المنبهات الدقيقة ممنوعة، تفتح البطاقة والحفظ شاشة النظام الخاصة بها. امنح الإذن ثم أعد الحفظ.
9. وسم Logcat: `Adhan`.

### الطبقات

```
com.adhan.app
  ui          قائمة المنبهات ومكان الصلاة ومحرر المنبه؛ لا تشغّل الصوت ولا تحسب المواقيت
  domain      Alert = TIME | PRAYER | PLACE، مع قواعد التكرار اليومي
  scheduler   AlertScheduler + AlarmManager.setAlarmClock
  alert       AlertPlayer بسمة USAGE_ALARM وقنوات إشعار المنبه
  prayer      PrayerEngine يغلّف مكتبة batoulapps adhan
  geofence    GeofenceEngine عبر تنبيهات القرب في LocationManager
  store       تخزين المنبهات والإحداثيات المحفوظة في التفضيلات
```

العقود الثابتة: `Alert` و`AlertPlayer` و`AlertScheduler` و`PrayerEngine` و`GeofenceEngine` و`AlertStore`.
