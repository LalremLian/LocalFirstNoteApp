# Local First Note App

A modern, Local-first Android note-taking application built with **Jetpack Compose**. It features a multi-tab editing experience, an interactive workspace with drag-and-drop ordering, rich image support, end-to-end encryption, and real-time cross-device synchronization using Firebase Firestore.

## 🌟 Key Features

*   **Local-First Architecture**: Built on Room Database, your notes are always available and blazingly fast, even without an internet connection.
*   **Real-time Synchronization**: Seamlessly syncs your notes and images across all your devices using Firebase Firestore. Conflict resolution favors local drafts to prevent data loss.
*   **End-to-End Encryption (E2EE)**: All data is encrypted locally using AES-GCM and PBKDF2 before syncing to the cloud, ensuring your private notes stay private.
*   **Multi-Tab Editing**: An advanced editor screen that allows you to open multiple notes simultaneously in a horizontal tab row, similar to a desktop browser.
*   **Drag-and-Drop Workspace**: A fully interactive grid workspace. Organize your notes visually by simply dragging and dropping them into your preferred order.
*   **Rich Media Support**: Drag, drop, and rotate images within your notes. Images are compressed, Base64-encoded, and securely synced.

## 🛠️ Technology Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material Design 3)
*   **Local Database**: Room
*   **Cloud Sync**: Firebase Firestore
*   **Dependency Injection**: Dagger Hilt
*   **Image Loading**: Coil
*   **Architecture**: MVVM with Unidirectional Data Flow (UDF) (StateFlow, Channels, and Sealed Events)

---

## 🚀 Project Setup

Follow these steps to run the application locally in Android Studio:

### Prerequisites
*   Android Studio (Latest stable version recommended)
*   A Firebase Project (for cloud sync)

### Installation

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/LalremLian/LocalFirstNoteApp.git
    ```

2.  **Open the project:**
    *   Launch Android Studio.
    *   Select **Open** and choose the `LocalFirstNoteApp` directory.

3.  **Build and Run:**
    *   Allow Gradle to sync completely.
    *   Select your emulator or physical device.
    *   Click the **Run** button (Shift + F10).

---

## 📦 Releasing Instructions

To create a release build for the Google Play Store or distribution:

1.  **Update Versioning:**
    *   Open `app/build.gradle.kts`.
    *   Increment the `versionCode` (integer) and `versionName` (string).
    *   Sync Gradle.

2.  **Generate a Signed Bundle/APK:**
    *   In Android Studio, go to **Build** > **Generate Signed Bundle / APK...**
    *   Select **Android App Bundle** (for Google Play) or **APK** (for direct distribution). Click **Next**.
    *   Choose an existing keystore path or click **Create new...** to generate one.
    *   Enter the Key store password, Key alias, and Key password. Click **Next**.
    *   Select the **release** build variant.
    *   Click **Finish**.

3.  **Locate the Build:**
    *   Once the build completes, Android Studio will show a notification.
    *   Click "locate" in the notification, or navigate to `app/release/` to find your `.aab` or `.apk` file.

4.  **Distribution:**
    *   Upload the generated `.aab` file to the Google Play Console under your desired release track (Internal, Alpha, Beta, or Production).
