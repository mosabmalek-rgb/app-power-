# PowerHub Field Reader

تطبيق Android ميداني لتسجيل قراءات عدادات المشتركين في منصة **PowerHub**. يستخدم التطبيق حسابات الموظفين الفرعية ذات صلاحية تسجيل القراءات، ويحفظ العمل محلياً عند انقطاع الشبكة ثم يرسله عند عودة الاتصال.

## ما الذي يقدمه الإصدار التجريبي؟

| الوظيفة | الحالة |
|---|---|
| الدخول ببريد الحساب الفرعي وكلمة مروره | متاح عبر Mobile API v1 |
| عزل المستأجر والفرع والصلاحية | يفرضه الخادم قبل كل طلب |
| تنزيل المشتركين والعدادات المسموح بها | متاح |
| تسجيل القراءة مع `clientOperationId` لمنع التكرار | متاح |
| التقاط صورة العداد | متاح من كاميرا الجهاز |
| تحديد الموقع بعد موافقة المستخدم | متاح اختيارياً |
| مزامنة القراءات في الخلفية عند عودة الإنترنت | متاح |
| التحصيلات والفواتير | غير متاحة عمداً في تطبيق القراءات؛ تبقى ضمن PowerHub لتفادي أي أثر مالي غير موثق |

## المتطلبات

يحتاج البناء المحلي إلى **JDK 21**، وAndroid SDK يتضمن `platforms;android-36.1` و`build-tools;36.0.0`. يتضمن المستودع Gradle Wrapper، لذلك لا يلزم تثبيت Gradle بشكل مستقل.

## إعداد عنوان المنصة

لا يضع التطبيق عنوان إنتاج افتراضياً. مرر عنوان PowerHub المنشور الذي ينتهي بشرطة مائلة عند البناء:

```bash
./gradlew assembleDebug \
  -PPOWERHUB_API_BASE_URL=https://your-powerhub-domain.example/
```

لا تضع كلمات مرور أو مفاتيح Supabase أو مفاتيح خدمة داخل التطبيق أو ملفات Gradle. يستخدم التطبيق عنوان API فقط، بينما تتم المصادقة عبر مسارات PowerHub المخصصة للموبايل.

## التهيئة البعيدة لتغيير الدومين

يدعم التطبيق ملف JSON ثابتاً عبر HTTPS، مثل رابط **Raw GitHub Gist**. عند كل تشغيل أو مزامنة، يتحقق التطبيق من الملف كل 30 دقيقة، ويحتفظ بآخر عنوان صالح محلياً إذا انقطع الإنترنت. يجب أن يتضمن الملف توقيع Ed25519 للحمولة `config_version + "\n" + api_base_url`؛ لا يقبل التطبيق أي ملف غير موقّع أو لا يطابق مفتاح التحقق المضمّن في APK. استخدم القالب `remote-config.example.json`، ثم مرر رابط الملف الخام ومفتاحه العام عند بناء الإصدار الأول:

```bash
./gradlew assembleRelease \
  -PPOWERHUB_API_BASE_URL=https://temporary-powerhub-host.example/ \
  -PPOWERHUB_REMOTE_CONFIG_URL=https://gist.githubusercontent.com/OWNER/GIST_ID/raw/powerhub-mobile-config.json \
  -PPOWERHUB_REMOTE_CONFIG_PUBLIC_KEY=BASE64_ED25519_PUBLIC_KEY
```

> يجب أن تكون قيمة `api_base_url` هي **جذر الموقع فقط** مثل `https://powerhub.example.com/`، وليس `.../api/`؛ لأن مسارات الموبايل تتضمن `/api/mobile/v1` تلقائياً. عند شراء الدومين، عدّل قيمة `api_base_url` في Gist فقط. لا تنشئ التطبيق ملف تهيئة غير صالح أو غير HTTPS، وتبقى آخر تهيئة صالحة محفوظة عند تعذر الوصول إلى Gist.

لتوقيع الملف بعد تغيير العنوان، احفظ المفتاح الخاص خارج المستودع ثم استخدم:

```bash
POWERHUB_REMOTE_CONFIG_PRIVATE_KEY=/secure/path/remote-config-ed25519-private.pem \
  ./scripts/sign-remote-config.sh 2 https://powerhub.example.com/ > powerhub-mobile-config.json
```

ارفع محتوى `powerhub-mobile-config.json` الناتج إلى Gist نفسه. لا ترفع المفتاح الخاص إلى Gist أو GitHub، ولا تغيّر المفتاح العام المدمج في APK؛ تغيير المفتاح العام يتطلب إصدار APK جديداً لأسباب أمنية.

## إصدار APK موقّع

لا يقبل البناء من نوع Release عنواناً تجريبياً أو توقيعاً افتراضياً. احتفظ بملف المفتاح وكلمات مروره خارج GitHub، ثم اضبط متغيرات البيئة الآتية داخل جلسة البناء فقط:

```bash
source /secure/path/release-signing.env
./gradlew assembleRelease \
  -PPOWERHUB_API_BASE_URL=https://your-powerhub-domain.example/
```

ينتج الملف الموقّع في `app/build/outputs/apk/release/`. قبل توزيعه، تحقق من التوقيع باستخدام `apksigner verify --verbose`، واحتفظ بنسخة احتياطية محمية من ملف المفتاح وبيانات الاسترداد؛ فقدانها يمنع تحديث التطبيق بنفس هوية Android مستقبلاً.

## البناء والاختبار

```bash
./gradlew testDebugUnitTest assembleDebug --no-daemon --no-configuration-cache \
  -PPOWERHUB_API_BASE_URL=https://your-powerhub-domain.example/
```

ينتج APK التجريبي في:

```text
app/build/outputs/apk/debug/app-debug.apk
```

يعمل GitHub Actions تلقائياً عند كل Push وPull Request إلى `main`، ويشغّل اختبارات الوحدة ويبني APK تجريبياً قابلاً للتنزيل من مخرجات التشغيل.

## عقد التكامل

يعتمد التطبيق على `Mobile API v1` في مستودع PowerHub. المسارات المستخدمة هي:

| المسار | الاستخدام |
|---|---|
| `POST /api/mobile/v1/auth/login` | دخول الموظف الميداني وإصدار access/refresh tokens. |
| `POST /api/mobile/v1/auth/refresh` | تجديد جلسة الموبايل. |
| `GET /api/mobile/v1/auth/me` | التأكد من الهوية والفروع المسموحة. |
| `GET /api/mobile/v1/subscribers` | تنزيل المشتركين والعدادات للفرع المخول. |
| `POST /api/mobile/v1/readings` | إرسال قراءة وصورة وموقع اختياري. |

تفاصيل العقد وقواعد منع التكرار موثقة في مستودع المنصة ضمن `docs/mobile-api-contract-v1.md`.

## تجربة ميدانية مقترحة

ابدأ بحساب فرعي من دور **فني** أو دور مخصص يملك قدرة `recordReadings`، وأكمل تغيير كلمة المرور المؤقتة من المنصة أولاً. بعد ذلك، سجل الدخول في التطبيق، وافتح مشتركاً من الفرع المسموح، وسجل قراءة مع صورة اختيارية. اختبر مرة واحدة مع الإنترنت ومرة أخرى دون اتصال؛ يجب أن تبقى القراءة في الطابور المحلي ثم تُرسل تلقائياً عند عودة الشبكة. راجع حالة القراءة والصورة وسجل التدقيق في PowerHub قبل اعتماد أي دفعة قراءات أو إصدار فواتير.

> لا تنشر نسخة Release أو توزعها خارج فريق التجربة قبل توقيعها بمفتاح إصدار مخصص، ومراجعة سجل التجربة الميداني، وتحديد عنوان Production النهائي للمنصة.
