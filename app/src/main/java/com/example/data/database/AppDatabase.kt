package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.models.Category
import com.example.data.models.Note
import com.example.data.models.Tip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Category::class, Note::class, Tip::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "note_review_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.noteDao())
                }
            }
        }

        suspend fun populateDatabase(noteDao: NoteDao) {
            // Seed Categories
            val cat1Id = noteDao.insertCategory(
                Category(
                    name = "مبانی یادگیری ماشین",
                    description = "یادداشت‌های مربوط به الگوریتم‌های پیش‌بینی، ریاضیات آماری و یادگیری عمیق",
                    colorHex = "#FF1A73E8"
                )
            ).toInt()

            val cat2Id = noteDao.insertCategory(
                Category(
                    name = "توسعه پیشرفته اندروید",
                    description = "نکات معماری کلین، توسعه جت‌پک کامپوز، مدیریت کارایی و گرافیک‌های خلاقه",
                    colorHex = "#FF34A853"
                )
            ).toInt()

            val cat3Id = noteDao.insertCategory(
                Category(
                    name = "طراحی تجربه کاربری (UI/UX)",
                    description = "اصول طراحی، تایپوگرافی، پالت‌های رنگی و الگوهای کارپسند گوگل",
                    colorHex = "#FFEA4335"
                )
            ).toInt()

            // Seed Notes for Machine Learning
            val note1Id = noteDao.insertNote(
                Note(
                    categoryId = cat1Id,
                    title = "مفهوم رگرسیون خطی",
                    chapter = "فصل ۱: یادگیری نظارتی",
                    pdfName = "Machine_Learning_Fundamentals.pdf",
                    pdfPage = 14,
                    content = """# یادگیری نظارتی: رگرسیون خطی ساده

رگرسیون خطی (Linear Regression) ساده‌ترین و محبوب‌ترین مدل پیش‌بینی داده‌های پیوسته است. شیوه کار بر مبنای منطبق کردن یک خط راست بر پراکندگی داده‌هاست.

## فرمول اصولی ریاضی:
y = w * x + b

در این بخش:
* w نشان‌دهنده وزن یا شیب خط (Slope) است.
* b عرض از مبدأ یا بایاس (Bias) نام دارد.

## لینک‌های درون‌متنی چندرسانه‌ای
* حتماً برای جزئیات فنی بیشتر به [سایت رسمی Scikit-Learn](https://scikit-learn.org) سر بزنید.
* ما قبلاً یک یادداشت تکمیلی برای [شبکه‌های عصبی عمیق](note://شبکه‌های عصبی عمیق) ایجاد کرده‌ایم که ساختار نورون را به این فرمول متصل می‌کند! روی آن کلیک کنید تا سریعاً باز شود.

## تصویر لوکال گرافیکی
می‌توانید نمودار برازش رگرسیون را به صورت محلی در زیر مشاهده کنید:
![نمودار رگرسیون خطی](sample_regression_chart)

دقت کنید که این تصویر مستقیماً از حافظه محلی لود می‌شود. شما در صفحه افزودن یادداشت می‌توانید عکس‌های گالری یا فایل‌های تصویری خود را درون متن جاسازی کنید!
"""
                )
            ).toInt()

            val note2Id = noteDao.insertNote(
                Note(
                    categoryId = cat1Id,
                    title = "شبکه‌های عصبی عمیق",
                    chapter = "فصل ۲: شبکه‌های عصبی",
                    pdfName = "Deep_Learning_HandsOn.pdf",
                    pdfPage = 56,
                    content = """# شبکه‌های عصبی عمیق (Deep Learning)

شبکه‌های عصبی عمیق مدل‌های پیشرفته‌ای هستند که ساختاری شبیه به مغز انسان دارند. هر لایه ورودی‌ها را پردازش کرده و ویژگی‌های انتزاعی‌تری استخراج می‌کند.

### ساختار کلی لایه‌ها:
1. **لایه ورودی (Input Layer)**
2. **لایه‌های پنهان (Hidden Layers)**
3. **لایه خروجی (Output Layer)**

برای شروع کار عمیق، پیش‌نیازی مانند مفاهیم پایه ریاضی الزامی است. بنابراین مطالعه یادداشت [مفهوم رگرسیون خطی](note://$note1Id) به شدت توصیه می‌شود.

همانطور که در صفحه ۵۶ کتاب آموزش کاربردی دیپ لرنینگ مطرح شده، تابع فعالساز نقش کلیدی در غیرخطی‌سازی شبکه ایفا می‌کند.
"""
                )
            ).toInt()

            // Seed Note for Android
            val note3Id = noteDao.insertNote(
                Note(
                    categoryId = cat2Id,
                    title = "اصول State در Compose",
                    chapter = "فصل ۲: مدیریت حالت",
                    pdfName = "Jetpack_Compose_by_Tutorials.pdf",
                    pdfPage = 45,
                    content = """# مدیریت حالت (State) در جت‌پک کامپوز

یکی از مفاهیم فوق‌العاده حیاتی در کامپوز، درک درست جریان حالت است. هر زمان متغیر وضعیت تغییر کند، صفحه مجدداً بازطراحی (Recomposition) می‌شود.

### نحوه تعریف ساده:
```kotlin
var count by remember { mutableStateOf(0) }
```

### ارتباط با طراحی بصری
برای بررسی نحوه استفاده از حالت‌ها در طراحی‌های مدرن، یادداشت جذاب [اصول ترکیب رنگ‌ها](note://اصول ترکیب رنگ‌ها) در بخش طراحی گرافیک را بخوانید.

می‌توانید فایل PDF مرجع را ذخیره کرده و با دکمه باز کردن PDF در بالای صفحه مستقیم به صفحه ۴۵ بروید!
"""
                )
            ).toInt()

            // Seed Note for UI/UX
            val note4Id = noteDao.insertNote(
                Note(
                    categoryId = cat3Id,
                    title = "اصول ترکیب رنگ‌ها",
                    chapter = "فصل ۱: هارمونی و کنتراست",
                    pdfName = "Universal_Principles_of_Design.pdf",
                    pdfPage = 112,
                    content = """# اصول پایه ترکیب رنگ‌ها در واسط کاربری

رنگ‌ها نقش احساسی و راهبری عالی در ترغیب چشم کاربر دارند. همیشه باید اصول ۶۰-۳۰-۱۰ را رعایت کنید:

* **۶۰ درصد**: رنگ غالب (معمولاً پس‌زمینه خنثی)
* **۳۰ درصد**: رنگ مکمل یا فرعی
* **۱۰ درصد**: رنگ تأکیدی (Accent) برای دکمه فعالیت‌ها و لینک‌ها

همانطور که در راهنمای طراحی صفحه ۱۱۲ کتاب اصول جهانی طراحی آمده است، از ترکیب‌های خلوت و کنتراست بالا استفاده کنید تا کاربران دچار خستگی زودرس نشوند.

برای پیاده‌سازی این پالت‌ها در اندروید، حتماً اصول برنامه‌نویسی را در یادداشت [اصول State در Compose](note://$note3Id) دنبال کنید.
"""
                )
            ).toInt()
        }
    }
}
