package team.clevel.documentscannerandroid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import com.kotlinpermissions.KotlinPermissions
import team.clevel.documentscanner.ImageCropActivity
import team.clevel.documentscanner.helpers.ScannerConstants
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var btnPick : Button
    lateinit var imgBitmap : ImageView
    lateinit var  mCurrentPhotoPath: String

    val GALLERY_INTENT_REQUEST_CODE = 1111
    val CAMERA_INTENT_REQUEST_CODE = 1231
    val ImageCropActivity_INTENT_REQUEST_CODE = 1234

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GALLERY_INTENT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            var selectedImage = data.data
            var btimap: Bitmap? = null
            try {
                Log.e("IamgePath",""+data.data.path)
                val inputStream = contentResolver.openInputStream(selectedImage)
                btimap = BitmapFactory.decodeStream(inputStream)
                ScannerConstants.selectedImageBitmap=btimap
                startActivityForResult(Intent(MainActivity@this, ImageCropActivity::class.java),ImageCropActivity_INTENT_REQUEST_CODE)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        else if (requestCode== CAMERA_INTENT_REQUEST_CODE && resultCode== Activity.RESULT_OK )
        {
            ScannerConstants.selectedImageBitmap=MediaStore.Images.Media.getBitmap(this.contentResolver, Uri.parse(mCurrentPhotoPath))
            startActivityForResult(Intent(MainActivity@this, ImageCropActivity::class.java),ImageCropActivity_INTENT_REQUEST_CODE)
            //startActivity(Intent(MainActivity@this, ImageCropActivity::class.java))
        }
        else if (requestCode==ImageCropActivity_INTENT_REQUEST_CODE && resultCode== Activity.RESULT_OK )
        {
            if (ScannerConstants.selectedImageBitmap!=null)
            {
                imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap)
                imgBitmap.visibility=View.VISIBLE
                btnPick.visibility=View.GONE
            }
            else
                Toast.makeText(MainActivity@this,"Not OK",Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnPick=findViewById(R.id.btnPick)
        imgBitmap=findViewById(R.id.imgBitmap)
        mCurrentPhotoPath = ""
        askPermission()
    }

    fun askPermission()
    {
        if (
            ContextCompat.checkSelfPermission( this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED||
            ContextCompat.checkSelfPermission( this, android.Manifest.permission.READ_EXTERNAL_STORAGE ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission( this, android.Manifest.permission.CAMERA ) != PackageManager.PERMISSION_GRANTED)
        {
            KotlinPermissions.with(this)
                .permissions(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA)
                .onAccepted { permissions ->
                    setView()
                }
                .onDenied { permissions ->
                    askPermission()
                }
                .onForeverDenied { permissions ->
                    Toast.makeText(MainActivity@this,"You have to grant permissions! Grant them from app settings please.", Toast.LENGTH_LONG).show()
                    finish()
                }
                .ask()
        }
        else
        {
           setView()
        }
    }

    fun setView()
    {
        btnPick.setOnClickListener(View.OnClickListener {
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("Image Chooser")
            builder.setMessage("Where would you like to select the image?")
            builder.setPositiveButton("Gallery"){dialog, which ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, GALLERY_INTENT_REQUEST_CODE)
            }
            builder.setNegativeButton("Camera"){dialog,which ->
                dialog.dismiss()
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (cameraIntent.resolveActivity(packageManager) != null) {
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
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
            builder.setNeutralButton("Cancel"){dialog,_ ->
                dialog.dismiss()
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        })
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val image = File.createTempFile(
            imageFileName, // prefix
            ".jpg", // suffix
            storageDir      // directory
        )
        Log.i("********image",image.absolutePath)
        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = "file:" + image.absolutePath
        Log.i("*****mCurrentPhotoPath",mCurrentPhotoPath)
        return image
    }

}
