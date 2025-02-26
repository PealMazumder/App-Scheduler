### **<u>App Schedule</u>**

This Android application allows users to schedule any installed app to start at a specific time, cancel a scheduled start, or change the time of an existing scheduled app. The application supports multiple schedules for the same app.


### **<u>Features</u>**
Schedule App Launch: Users can schedule any installed app on their device to launch at a specific time.
Cancel Schedule: If the scheduled app has not yet started, users can cancel the schedule.
Modify Schedule: Users can change the time of an already scheduled app launch.
Multiple Schedules: The app supports multiple schedules without conflicts, ensuring smooth and efficient scheduling.
Schedule History: A record is kept of each schedule.


### **<u>Tools and Technologies Used</u>**
Jetpack Compose
Clean Architecture
MVI
Room Database
Compose Type safe Navigation
Dagger Hilt
Kotlin Coroutines
Kotlin Flow
Desugar Jdk Libs
Unit Test

### **<u>App Package Structure</u>**

📂 app
├── 📂 manifests
│   └── 📄 AndroidManifest.xml
├── 📂 kotlin+java
│   └── 📂 com.peal.appscheduler
│       ├── 📂 data
│       │   ├── 📂 local
│       │   │   ├── 📂 model
│       │   │   │   ├── 📝 ScheduleEntity.kt
│       │   │   ├── 📝 ScheduleDao.kt
│       │   │   ├── 📝 SchedulerAppDatabase.kt
│       │   ├── 📂 mappers
│       │   │   ├── 📝 ScheduleMappers.kt
│       │   ├── 📂 repositoryImpl
│       │   ├── 📂 wrapper
│       ├── 📂 di
│       │   ├── 📝 AppModule.kt
│       │   ├── 📝 DatabaseModule.kt
│       │   ├── 📝 RepositoryModule.kt
│       ├── 📂 domain
│       │   ├── 📂 enums
│       │   ├── 📂 mappers
│       │   ├── 📂 model
│       │   ├── 📂 repository
│       │   ├── 📂 usecase
│       │   ├── 📂 utils
│       ├── 📂 ui
│       │   ├── 📂 model
│       │   ├── 📂 navigation
│       │   ├── 📂 screens
│       │   ├── 📂 theme
│       ├── 📂 service
│       └── 📂 receiver


### **<u>ScreenShots</u>**
![img.png](img.png)
![img_1.png](img_1.png)
![img_2.png](img_2.png)
![img_3.png](img_3.png)


