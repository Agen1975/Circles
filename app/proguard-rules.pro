
# Room 2.6.x (pulled in by WorkManager) keeps RoomDatabase subclasses but not their constructors.
# Under R8 full mode the no-arg constructor of WorkDatabase_Impl was stripped, so the app crashed
# at startup (NoSuchMethodException in androidx.startup.InitializationProvider).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
