# Android Document Scanner Library

image::https://img.shields.io/badge/version-1.4-green.svg[]
image::https://img.shields.io/badge/minSDK-19-blue.svg[]
image::https://img.shields.io/badge/license-MIT-yellowgreen.svg[]

This library helps you to scan any document like CamScanner.

image::documentscannerMockup.png[]

## Requirements

You need to implement openCV to run this library via import module on Android Studio. Use OpenCV library on this repository.
Follow the lines below:

* File -> New -> Import Module
* Select *" openCVLibrary "* you've downloaded from this repository.
* Click finish
* Sync gradle
* File -> Project Structure -> Select *app* under modules (this is your app module) -> Go to *dependencies* tab
* Click *+* button -> Module dependency -> Select *openCVLibrary* -> Click ok -> Click ok
* Sync gradle

Add line below to your *top* level build.gradle

[source,bourne]
----
allprojects {
    repositories {
        /// ....
        maven { url "https://jitpack.io" }
    }
}
----

Add lines below to your *app* level build.gradle

[source,bourne]
----
    implementation 'io.reactivex.rxjava2:rxandroid:2.0.2'
    implementation 'com.github.mayuce:AndroidDocumentScanner:1.4'
----

And Sync the gradle

## Usage

To start ImageCrop process 

[source,java]
----
ScannerConstants.selectedImageBitmap=btimap
startActivityForResult(Intent(MainActivity@this, ImageCropActivity::class.java),Constants.REQUEST_CROP)
----

Catch the cropped image

[source,java]
----
if (requestCode==Constants.REQUEST_CROP && resultCode== Activity.RESULT_OK )
        {
            if (ScannerConstants.selectedImageBitmap!=null)
                imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap)
            else
                Toast.makeText(MainActivity@this,"Something wen't wrong.",Toast.LENGTH_LONG).show()
        }
----

### Additional Features

On above Android 9.0 there is magnifier to help user to see zoomed image to crop.

#### Customizing ImageCropActivity

You can customize something in layout via ScannerConstants.

[source,java]
----
    // ScannerConstants.java
    public static String cropText="CROP",
            backText="CLOSE",
            imageError="Can't picked image,
            please try again.",
            cropError="You have not selected a valid field. Please make corrections until the lines are blue.";
    public static String cropColor="#6666ff",backColor="#ff0000",progressColor="#331199"; // Default Colors 
    public static boolean saveStorage=false; // Make it true if you need image in your storage. 
----
## TO-DO

Nothing

## Thanks

* Thanks RX library to improve this project.
* Thanks OpenCV for this awesome library. - https://opencv.org/
and
* Inspiration from *aashari* . Thanks him for his source codes. - https://github.com/aashari/android-opencv-camera-scanner

[source,bourne]
----
MIT License

Copyright (c) 2019 Muhammet Ali YUCE

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
----
