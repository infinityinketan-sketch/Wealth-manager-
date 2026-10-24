# Room, Compose, WorkManager and Play Billing ship their own consumer rules.
# Keep file/line info so the on-phone crash report stays readable after shrinking.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager loads its Play services provider by reflection (from the Android docs).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
