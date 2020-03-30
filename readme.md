
# Android Document Scanner Library  
This library helps you to scan any document like CamScanner.  
  
![](demo.gif)
  
## Requirements  

To use this library in your project, you need to import modules **openCVLibrary** and **documentscanner** libraries from this repository.
    
Follow the lines below:  
  
* File → New → Import Module  
* Select **openCVLibrary** you've downloaded from this repository.  
* Select **documentscanner** you've downloaded from this repository.  
* Click finish  
* Sync gradle  
* File → Project Structure → Select **app** under modules (this is your app module) → Go to **dependencies** tab  
* Click **+** button → Module dependency → Select **openCVLibrary** → Click ok
* Click **+** button → Module dependency → Select **documentscanner** → Click ok  
* Sync gradle  
  
Add line below to your **project** level build.gradle  
    
```gradle  
dependencies {
	/// ....
	classpath 'com.github.dcendents:android-maven-gradle-plugin:2.1'
}
/// .....
allprojects {  
    repositories {  
        /// ....  
        maven { url "https://jitpack.io" }  
    }  
}  
```
  
Add lines below to your **app** level build.gradle  

```gradle  
    implementation 'io.reactivex.rxjava2:rxandroid:2.0.2'    
```
  
And Sync the gradle  
  
## Usage  
  
Refer **MainActivity.kt** in the app module for usages.
  
### Additional Features  
  
On above Android 9.0 there is magnifier to help user to see zoomed image to crop.  
    
## Thanks  
  
* Thanks RX library to improve this project.  
* Thanks OpenCV for this awesome library. - https://opencv.org/
* Inspiration from *aashari*. Thanks him for his source codes. - https://github.com/aashari/android-opencv-camera-scanner  
  