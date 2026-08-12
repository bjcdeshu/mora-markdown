# Third-party notices

Mora includes the following open-source software in its release runtime. This
notice records the dependency families declared by the app; build tools, test
libraries, and debug-only tooling are not shipped in a release APK.

Declared runtime entry points for this source revision:

| Runtime dependency family | Declared version source | License | Notice/source |
|---|---|---|---|
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | [AndroidX license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| AndroidX Core KTX | 1.18.0 | Apache-2.0 | [AndroidX license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| AndroidX Lifecycle ViewModel and Runtime Compose | 2.10.0 | Apache-2.0 | [AndroidX license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Jetpack Compose UI, Foundation, Material, Material 3, and Icons | Compose BOM 2026.06.00 | Apache-2.0 | [AndroidX license](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Kotlin standard library | Kotlin 2.3.21 toolchain | Apache-2.0 | [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) |
| kotlinx.coroutines Android | 1.11.0 | Apache-2.0 | [kotlinx.coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| kotlinx.serialization runtime | Resolved transitively | Apache-2.0 | [kotlinx.serialization license](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt) |
| commonmark-java core and GFM/task-list extensions | 0.29.0 | BSD-2-Clause | [commonmark-java license](https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt) |
| JSpecify nullness annotations | Resolved transitively | Apache-2.0 | [JSpecify license](https://github.com/jspecify/jspecify/blob/main/LICENSE) |
| Guava ListenableFuture compatibility artifact | Resolved transitively | Apache-2.0 | [Guava license](https://github.com/google/guava/blob/master/COPYING) |
| JetBrains annotations | Resolved transitively | Apache-2.0 | [JetBrains annotations license](https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt) |

The Gradle lock-free build resolves compatible transitive modules from these
entry points. Every Release build maps each resolved external runtime coordinate
to one of the documented license families below and fails if a module is not
covered. The exact audited graph is written to
`app/build/reports/runtime-third-party-notices.txt`.

## AndroidX and Jetpack Compose

Mora uses AndroidX Activity, Core, Lifecycle, and Jetpack Compose UI,
Foundation, Material, Material 3, and Material Icons components.

Copyright The Android Open Source Project contributors.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
The AndroidX source license is available in the
[AndroidX repository](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt).

## Kotlin

Mora includes the Kotlin standard library at runtime.

Copyright JetBrains s.r.o. and Kotlin Programming Language contributors.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
See the [Kotlin license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt).

## kotlinx.coroutines

Mora uses `kotlinx-coroutines-android` and its runtime dependencies.

Copyright Kotlin Programming Language contributors.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
See the
[kotlinx.coroutines license](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt).

## kotlinx.serialization

Mora includes `kotlinx.serialization` runtime components through AndroidX
SavedState dependencies.

Copyright Kotlin Programming Language contributors.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
See the
[kotlinx.serialization license](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt).

## commonmark-java

Mora uses `commonmark`, `commonmark-ext-gfm-tables`,
`commonmark-ext-gfm-strikethrough`, and `commonmark-ext-task-list-items`.

Copyright (c) 2015, Robin Stocker.

Licensed under the
[BSD 2-Clause License](https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt).

## JSpecify

Mora includes JSpecify's nullness annotations through AndroidX runtime
dependencies.

Copyright The JSpecify Authors.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
See the [JSpecify license](https://github.com/jspecify/jspecify/blob/main/LICENSE).

## Guava ListenableFuture

Mora includes Guava's standalone `listenablefuture` compatibility artifact
through AndroidX runtime dependencies.

Copyright The Guava Authors.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
See the [Guava license](https://github.com/google/guava/blob/master/COPYING).

## JetBrains annotations

Mora includes JetBrains Java annotations through Kotlin runtime dependencies.

Copyright 2000-2016 JetBrains s.r.o.

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
See the
[JetBrains annotations license](https://github.com/JetBrains/java-annotations/blob/master/LICENSE.txt).

---

Mora itself is distributed under the [MIT License](LICENSE). If a packaged
dependency's own notice or license differs from this summary, that dependency's
packaged terms govern.
