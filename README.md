# Health Guardian Project 

Health Guardian is a digital health coach that leverages wearable technology while ensuring data sovereignty through Federated Learning. While most health applications process data in isolated categories, this application cross-analyzes data from different categories. By incorporating heart rate, sleep, physical activity, and mental health data, the system produces more robust predictions, particularly for the menstrual cycle prediction on irregular ones.

## 🌟 Key Features

The application integrates five health functionalities:
* **Heart Rate:** Calculates resting heart rate and detects anomalies to help prevent potential heart issues.
* **Physical Activity:** Predicts physical activity intensity and computes a daily physical activity score (MET) using accelerometer data.
* **Sleep Analysis:** Identifies sleep stages and determines a daily sleep score.
* **Mental Health:** Estimates a stress score using heart rate variability (HRV) and uses PHQ-9 and GAD-7 questionnaires to determine depression and anxiety scores.
* **Menstrual Cycle Tracking:** Predicts upcoming menstruations and identifies fertile windows. It leverages the cross-analysis of the metrics above to ensure robustness for irregular cycles.

## 📊 Model Performance

Our machine learning models were evaluated under both centralized and federated learning paradigms:
* **Physical Activity Intensity prediction:** The centralized model achieved an accuracy of 98%. Under Federated Learning, it maintained excellent performance, achieving 96% with FedAvg and 97% with FedProx.
* **Menstrual Cycle Prediction:** On a representative dataset (25% irregular cycles), the centralized LSTM model achieved a 1.00 day Mean Absolute Error (MAE) for cycle length, a 0.97 day MAE for ovulation day estimation, and a 93.91% accuracy for phase prediction. Under Federated Learning with 16 simulated clients, the model achieves a 1.90 days MAE for cycle length and a 83.59% accuracy fo phase prediction. This performance drop is due to the restricted number of clients.

---

## 🚀 Getting Started

You can test the application in two ways: by installing the pre-built APK directly on your Android device, or by building the project from the source code using Android Studio.

### Option A: Quick Installation (APK)
1. **Download the APK:** Go to the [Releases](https://github.com/elisacio/Health-Guardian-Project/releases/latest) page of this repository and download the `.apk` file to your Android device.
2. **Allow Unknown Sources:** When you tap the downloaded file, your phone may ask for permission to install unknown apps. Go to **Settings** and toggle on **Allow from this source**.
3. **Install & Launch:** Tap **Install**, then open the Health Guardian app.

### Option B: Build from Source (Android Studio)
1. **Install Android Studio:** Download it from [here](https://developer.android.com/studio). Choose the Custom installation and select all available components.
2. **Clone & Open:** Clone this Git repository. Open Android Studio, click the top-left menu (three horizontal lines) > **Open** > select the cloned application folder.
3. **Set up a Device:** You can use the Emulator or pair a physical Android device.
   * *To pair a physical phone:* Click on **Device Manager** > **Pair Devices Using Wi-Fi**. On your phone, go to **Developer Options** > **Wireless Debugging** > **Pair device with QR code** and scan the code on your screen. 
   * Double-click your device in "Running Devices" to mirror your screen in Android Studio.
4. **Run the App:** Click the **Run App** button (the green play triangle) in the top toolbar to build and launch the app.

---

## 📱 How to Use 

The application currently does not connect directly to a smartwatch. Instead, it uses synthetic human cohort data generated via a probabilistic model.

1. Launch the application.
2. Create a new account.
3. At the end of the registration process, you will be prompted to select one of 5 synthetic data files. These represent 5 different user profiles (varying in age, activity level, stress, and cycle regularity).
4. The selected CSV will populate the app's databases, allowing you to explore the Heart Rate, Activity, Sleep, Mental Health, and Menstrual Cycle dashboards.

---

## 📂 Project Structure

The project code is located in `app/src/main/java/com/example/applisante`. 

**Core Navigation & Auth:**
* `MainActivity.kt`: Main execution file and navigation handler.
* `Home.kt`: The main dashboard/home page.
* `Login.kt` / `Register.kt`: User authentication and profile setup.

**Feature Modules:**
* `HeartRate.kt`
* `PhysicalActivity.kt`
* `Sleep.kt`
* `MentalHealth.kt`, `MentalHealthGAD7.kt`, `MentalHealthGAD7Result.kt`, `MentalHealthPHQ9.kt`, `MentalHealthPHQ9Result.kt` 
* `MenstrualCycle.kt`, `MenstrualCycleCalendar.kt`, `MenstrualCycleHistory.kt`

**Federated Learning & AI:**
* `FL_PhysicalActivity.kt`: Handles federated learning updates for activity.
* `FL_Scheduler.kt`: Manages the timing and execution of local training.
* `MentrualPredictionManager.kt`: Manages the LSTM cycle prediction logic.

**Data & Utilities:**
* `Session.kt`: Manages active user session data.
* `UserDataBase.kt`: Local database management.
* `Utils.kt`, `Dev.kt`: Helper functions and development tools.


---

## 👥 Contributors
This application was developed by Badr Bikria, Damien Bonzom, Elisa Ciocarlan, and Hélia Galinier.
