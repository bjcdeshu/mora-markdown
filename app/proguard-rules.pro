# Keep source and line information so the private R8 mapping artifact can
# deobfuscate actionable release crash traces. Mora does not use reflection-
# based serialization and currently needs no class-wide keep rules.
-keepattributes SourceFile,LineNumberTable
