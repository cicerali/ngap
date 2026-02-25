# NGAP Protocol Encoder/Decoder

This project contains NGAP protocol encoders and decoders. The NGAP protocol is used on 5G and is defined with ASN.1 APER encoding rules.

**Note:** This project is **not production ready** and should only be used for test projects.

## Installation

You can use this library in your test application using JitPack.

### Step 1. Add the JitPack repository to your build file

Add it in your root `build.gradle` at the end of repositories:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

### Step 2. Add the dependency

Add the dependency to your module's `build.gradle` file:

```gradle
dependencies {
    implementation 'com.github.cicerali:ngap:0.9.2'
}
```
[![](https://jitpack.io/v/cicerali/ngap.svg)](https://jitpack.io/#cicerali/ngap)