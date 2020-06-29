package com.h4rz.documentscannerandroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.kotlinpermissions.KotlinPermissions
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    lateinit var btnPick: Button
    lateinit var imgBitmap: ImageView
    lateinit var mCurrentPhotoPath: String
    private lateinit var manager: SplitInstallManager
    var mySessionId = 0

    val GALLERY_INTENT_REQUEST_CODE = 1111
    val CAMERA_INTENT_REQUEST_CODE = 1231
    val ImageCropActivity_INTENT_REQUEST_CODE = 1234

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_INTENT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val selectedImagePath = data.data!!.path!!.replace("/raw/", "file:")
            val selectedImage = Uri.parse(selectedImagePath)
            var btimap: Bitmap? = null
            try {
                Log.e("IamgePath", "" + selectedImagePath)
                btimap = handleSamplingAndRotationBitmap(this, selectedImage)
                ScannerConstants.selectedImageBitmap = btimap
                val intent = Intent()
                intent.setClassName(
                    BuildConfig.APPLICATION_ID,
                    "com.h4rz.documentscanner.ImageCropActivity"
                )
                startActivityForResult(intent, ImageCropActivity_INTENT_REQUEST_CODE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (requestCode == CAMERA_INTENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri = Uri.parse(mCurrentPhotoPath)
            ScannerConstants.selectedImageBitmap = handleSamplingAndRotationBitmap(this, uri)
            val intent = Intent()
            intent.setClassName(
                BuildConfig.APPLICATION_ID,
                "com.h4rz.documentscanner.ImageCropActivity"
            )
            startActivityForResult(intent, ImageCropActivity_INTENT_REQUEST_CODE)
            //startActivity(Intent(MainActivity@this, ImageCropActivity::class.java))
        } else if (requestCode == ImageCropActivity_INTENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (ScannerConstants.selectedImageBitmap != null) {
                imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap)
                imgBitmap.visibility = View.VISIBLE
                btnPick.visibility = View.GONE
            } else
                Toast.makeText(MainActivity@ this, "Not OK", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        manager = SplitInstallManagerFactory.create(this)
        btnPick = findViewById(R.id.btnPick)
        imgBitmap = findViewById(R.id.imgBitmap)
        mCurrentPhotoPath = ""
        //askPermission()
        installModule()
    }

    private fun installModule() {
        val splitInstallManager = SplitInstallManagerFactory.create(this)

        val request = SplitInstallRequest
            .newBuilder()
            .addModule("documentscanner")
            .build()

        val listener =
            SplitInstallStateUpdatedListener { splitInstallSessionState ->
                if (splitInstallSessionState.sessionId() == mySessionId) {
                    when (splitInstallSessionState.status()) {
                        SplitInstallSessionStatus.INSTALLED -> {
                            toastAndLog("Dynamic Module installed")
                            askPermission()
                        }
                        SplitInstallSessionStatus.CANCELED -> {
                            toastAndLog("Dynamic Module cancelled")
                        }
                        SplitInstallSessionStatus.CANCELING -> {
                            toastAndLog("Dynamic Module cancelling")
                        }
                        SplitInstallSessionStatus.DOWNLOADED -> {
                            toastAndLog("Dynamic Module downloaded")
                        }
                        SplitInstallSessionStatus.DOWNLOADING -> {
                            toastAndLog("Dynamic Module downloading")
                        }
                        SplitInstallSessionStatus.FAILED -> {
                            toastAndLog("Dynamic Module failed")
                        }
                        SplitInstallSessionStatus.INSTALLING -> {
                            toastAndLog("Dynamic Module installing")
                        }
                        SplitInstallSessionStatus.PENDING -> {
                            toastAndLog("Dynamic Module pending")
                        }
                        SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                            toastAndLog("Dynamic Module user confirmation")
                        }
                        SplitInstallSessionStatus.UNKNOWN -> {
                            toastAndLog("Dynamic Module unknown")
                        }
                    }
                }
            }

        splitInstallManager.registerListener(listener)

        splitInstallManager.startInstall(request)
            .addOnFailureListener { e -> toastAndLog("Exception $e") }
            .addOnSuccessListener { mySessionId = it }
    }

    private fun toastAndLog(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        Log.v("***********MainActivity", s)
    }

    private fun askPermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            KotlinPermissions.with(this)
                .permissions(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
                )
                .onAccepted { permissions ->
                    setView()
                }
                .onDenied { permissions ->
                    askPermission()
                }
                .onForeverDenied { permissions ->
                    Toast.makeText(
                        MainActivity@ this,
                        "You have to grant permissions! Grant them from app settings please.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                .ask()
        } else {
            setView()
        }
    }

    private fun setView() {
        btnPick.setOnClickListener(View.OnClickListener {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Image Chooser")
            builder.setMessage("Where would you like to select the image?")
            builder.setPositiveButton("Gallery") { dialog, which ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, GALLERY_INTENT_REQUEST_CODE)
            }
            builder.setNegativeButton("Camera") { dialog, which ->
                dialog.dismiss()
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                        mCurrentPhotoPath = "file:" + photoFile.absolutePath
                    } catch (ex: IOException) {
                        Log.i("Main", "IOException")
                    }
                    if (photoFile != null) {
                        val builder = StrictMode.VmPolicy.Builder();
                        StrictMode.setVmPolicy(builder.build());
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
                        startActivityForResult(cameraIntent, CAMERA_INTENT_REQUEST_CODE)
                    }
                }
            }
            builder.setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        })
    }

}
