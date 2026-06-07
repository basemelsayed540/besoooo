# Besoooo Android App

This folder is the Android source that wraps the GitHub Pages web app and receives Firebase Cloud Messaging notifications.

## Notifications

- Firebase Messaging is enabled in `app/build.gradle.kts`.
- Firebase configuration lives in `app/google-services.json`.
- Android notification permission and topic subscription are handled in `app/src/main/java/com/besoooo/app/MainActivity.java`.
- Incoming FCM messages are handled in `app/src/main/java/com/besoooo/app/FirebaseMessagingService.java`.
- The default notification channel id is `besoooo_notifications`.
- The app subscribes every install to the `all` topic.

## Send A Test Notification

Use Firebase Console:

1. Open Firebase Console.
2. Go to Cloud Messaging.
3. Create a campaign or send a test message.
4. Target topic: `all`.

For data messages, use keys:

```text
title=Notification title
body=Notification body
```

## Build

Debug build:

```powershell
.\gradlew.bat assembleDebug
```

Release signing is intentionally not committed. Keep `app/release.jks` local and put signing values in `gradle.properties` or pass them as Gradle properties:

```properties
BESOOOO_STORE_PASSWORD=your_store_password
BESOOOO_KEY_ALIAS=your_key_alias
BESOOOO_KEY_PASSWORD=your_key_password
```
