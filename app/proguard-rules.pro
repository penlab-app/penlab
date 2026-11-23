# Extra R8 keep rules for the release build. None are needed so far: Jetpack Ink and Compose
# ship their own rules inside the libraries.

# ML Kit handwriting search pulls in WorkManager (it schedules the model download). WorkManager's
# Room database is created by name (WorkDatabase_Impl) through reflection; R8 would remove its
# constructor and the app would crash at start-up.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# ML Kit finds its parts by class name (manifest meta-data) and creates them by reflection.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
