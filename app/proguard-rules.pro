# kotlinx.serialization — reguły są też w artefakcie, poniższe to pas bezpieczeństwa dla klas projektu.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class pl.mysttic.trainings.**$$serializer { *; }
-keepclassmembers class pl.mysttic.trainings.** { *** Companion; }
-keepclasseswithmembers class pl.mysttic.trainings.** { kotlinx.serialization.KSerializer serializer(...); }

# Trasy nawigacji (kotlinx.serialization) i encje Room korzystają z nazw enumów — nie skracać.
-keepnames class pl.mysttic.trainings.domain.model.** extends java.lang.Enum
