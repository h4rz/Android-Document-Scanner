/*
 * *
 *  * Created by Muhammet Ali YÜCE on 3/5/19 4:26 PM
 *  * on Github: /mayuce
 *  * Copyright (c) 2019 . All rights reserved.
 *  * Last modified 3/5/19 4:16 PM
 *
 */

package com.h4rz.documentscanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.h4rz.documentscanner.helpers.ImageUtils;
import com.h4rz.documentscanner.helpers.ScannerConstants;
import com.h4rz.documentscanner.libraries.NativeClass;
import com.h4rz.documentscanner.libraries.PolygonView;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class ImageCropActivity extends Activity {

    private FrameLayout holderImageCrop;
    private ImageView ivRotate, ivMagicFilter, ivSave, ivRotate_crop;
    private ImageView imageView, imgBitmap;
    private ImageView btnImageCrop, btnClose,btnClose1, btnPolyRevert;
    private PolygonView polygonView;
    private Bitmap selectedImageBitmap, tempBitmapOrginal;
    //private Button btnImageCrop, btnClose;
    //private RelativeLayout topPanel, bottomPanel;
    private LinearLayout bottomPanel1, bottomPanel2;
    private NativeClass nativeClass;
    private ProgressBar progressBar;
    private boolean isInverted = false;
    private boolean isPolygonReset = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_crop_1);
        if (ScannerConstants.selectedImageBitmap != null)
            initializeElement();
        else {
            Toast.makeText(this, ScannerConstants.imageError, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setImageRotation() {
        Bitmap tempBitmap = ScannerConstants.selectedImageBitmap.copy(ScannerConstants.selectedImageBitmap.getConfig(), true);
        for (int i = 1; i <= 4; i++) {
            MatOfPoint2f point2f = nativeClass.getPoint(tempBitmap);
            if (point2f == null) {
                tempBitmap = ImageUtils.rotateBitmap(tempBitmap, 90 * i);
            } else {
                ScannerConstants.selectedImageBitmap = tempBitmap.copy(ScannerConstants.selectedImageBitmap.getConfig(), true);
                break;
            }
        }
    }

    private void setProgressBar(boolean isShow) {
        RelativeLayout rlContainer = findViewById(R.id.rlContainer);
        setViewInterract(rlContainer, !isShow);
        if (isShow)
            progressBar.setVisibility(View.VISIBLE);
        else
            progressBar.setVisibility(View.GONE);
    }

    private void setViewInterract(View view, boolean canDo) {
        view.setEnabled(canDo);
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                setViewInterract(((ViewGroup) view).getChildAt(i), canDo);
            }
        }
    }

    @SuppressLint({"CheckResult", "ClickableViewAccessibility"})
    private void initializeElement() {
        nativeClass = new NativeClass();
        btnImageCrop = findViewById(R.id.btnImageCrop);
        btnPolyRevert = findViewById(R.id.btnPolyRevert);
        imgBitmap = findViewById(R.id.imgBitmap);
        //topPanel = findViewById(R.id.topPanel);
        //bottomPanel = findViewById(R.id.bottomPanel);
        bottomPanel1 = findViewById(R.id.bottomPanel_1);
        bottomPanel2 = findViewById(R.id.bottomPanel_2);
        btnClose = findViewById(R.id.btnClose);
        btnClose1 = findViewById(R.id.btnClose_1);
        holderImageCrop = findViewById(R.id.holderImageCrop);
        imageView = findViewById(R.id.imageView);
        ivRotate = findViewById(R.id.ivRotate);
        ivRotate_crop = findViewById(R.id.ivRotate_crop);
        ivMagicFilter = findViewById(R.id.ivMagicFilter);
        ivSave = findViewById(R.id.ivSave);
        //btnImageCrop.setText(ScannerConstants.cropText);
        //btnClose.setText(ScannerConstants.backText);
        polygonView = findViewById(R.id.polygonView);
        progressBar = findViewById(R.id.progressBar);
        if (progressBar.getIndeterminateDrawable() != null && ScannerConstants.progressColor != null)
            progressBar.getIndeterminateDrawable().setColorFilter(Color.parseColor(ScannerConstants.progressColor), android.graphics.PorterDuff.Mode.MULTIPLY);
        else if (progressBar.getProgressDrawable() != null && ScannerConstants.progressColor != null)
            progressBar.getProgressDrawable().setColorFilter(Color.parseColor(ScannerConstants.progressColor), android.graphics.PorterDuff.Mode.MULTIPLY);
        setProgressBar(true);
        //btnImageCrop.setBackgroundColor(Color.parseColor(ScannerConstants.cropColor));
        //btnClose.setBackgroundColor(Color.parseColor(ScannerConstants.backColor));
        Observable.fromCallable(() -> {
            setImageRotation();
            return false;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((result) -> {
                    setProgressBar(false);
                    holderImageCrop.post(this::initializeCropping);
                    btnImageCrop.setOnClickListener(btnImageEnhanceClick);
                    btnPolyRevert.setOnClickListener(btnPolyRevertClick);
                    btnClose.setOnClickListener(btnCloseClick);
                    btnClose1.setOnClickListener(btnCloseClick);
                    ivRotate.setOnClickListener(onRotateClick);
                    ivRotate_crop.setOnClickListener(onRotateClickFromCropScreen);
                    ivMagicFilter.setOnClickListener(btnMagicFilter);
                    ivSave.setOnClickListener(btnSave);
                });
    }

    private View.OnClickListener btnPolyRevertClick = v -> {
        Bitmap tempBitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        SparseArray<PointF> pointFs = null;
        if (isPolygonReset) {
            try {
                pointFs = getEdgePoints(tempBitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            pointFs = getOutlinePoints(tempBitmap);
        }
        isPolygonReset = !isPolygonReset;
        polygonView.setPoints(pointFs);
        int padding = (int) getResources().getDimension(R.dimen.scanPadding);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
        layoutParams.gravity = Gravity.CENTER;
        polygonView.setLayoutParams(layoutParams);
        polygonView.setPointColor(getResources().getColor(R.color.blue));
    };

    private void initializeCropping() {

        selectedImageBitmap = ScannerConstants.selectedImageBitmap;
        tempBitmapOrginal = selectedImageBitmap.copy(selectedImageBitmap.getConfig(), true);
        //ScannerConstants.selectedImageBitmap = null;

        Bitmap scaledBitmap = scaledBitmap(selectedImageBitmap, holderImageCrop.getWidth(), holderImageCrop.getHeight());
        imageView.setImageBitmap(scaledBitmap);
        //ScannerConstants.selectedImageBitmap = scaledBitmap;

        Bitmap tempBitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
        SparseArray<PointF> pointFs;
        try {
            pointFs = getEdgePoints(tempBitmap);
            polygonView.setPoints(pointFs);
            polygonView.setVisibility(View.VISIBLE);

            int padding = (int) getResources().getDimension(R.dimen.scanPadding);

            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
            layoutParams.gravity = Gravity.CENTER;
            polygonView.setLayoutParams(layoutParams);
            polygonView.setPointColor(getResources().getColor(R.color.blue));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private View.OnClickListener btnImageEnhanceClick = new View.OnClickListener() {
        @SuppressLint("CheckResult")
        @Override
        public void onClick(View v) {
            setProgressBar(true);
            Observable.fromCallable(() -> {
                ScannerConstants.selectedImageBitmap = getCroppedImage();
                if (ScannerConstants.selectedImageBitmap == null)
                    return false;
                return false;
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((result) -> {
                        setProgressBar(false);
                        if (ScannerConstants.selectedImageBitmap != null) {
                            /*setResult(RESULT_OK);
                            finish();*/
                            tempBitmapOrginal = ScannerConstants.selectedImageBitmap;
                            polygonView.setVisibility(View.GONE);
                            holderImageCrop.setVisibility(View.GONE);
                            //bottomPanel.setVisibility(View.GONE);
                            //topPanel.setVisibility(View.VISIBLE);
                            bottomPanel1.setVisibility(View.GONE);
                            bottomPanel2.setVisibility(View.VISIBLE);
                            imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap);
                            imgBitmap.setVisibility(View.VISIBLE);
                            ivMagicFilter.performClick();
                        }
                    });


        }
    };

    private View.OnClickListener btnSave = v -> {
        ScannerConstants.finalImagePath = saveToInternalStorage(ScannerConstants.selectedImageBitmap);
        Log.i("****Image Stored At-", ScannerConstants.finalImagePath);
        Toast.makeText(this, "Image Stored At -" + ScannerConstants.finalImagePath, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    };

    private View.OnClickListener btnCloseClick = v -> {
        setResult(RESULT_CANCELED);
        finish();
    };

    private Bitmap applyMagicfilter(Bitmap selectedImageBitmap) {
        if (!isInverted) {
            selectedImageBitmap = nativeClass.applyMagicFilter(selectedImageBitmap);
        } else {
            selectedImageBitmap = tempBitmapOrginal.copy(tempBitmapOrginal.getConfig(), true);
        }
        isInverted = !isInverted;
        return selectedImageBitmap;
    }

    private View.OnClickListener btnMagicFilter = new View.OnClickListener() {
        @SuppressLint("CheckResult")
        @Override
        public void onClick(View v) {
            setProgressBar(true);
            Observable.fromCallable(() -> {
                ScannerConstants.selectedImageBitmap = applyMagicfilter(ScannerConstants.selectedImageBitmap);
                return false;
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((result) -> {
                        setProgressBar(false);
                        imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap);
                    });


        }
    };

    private View.OnClickListener onRotateClick = new View.OnClickListener() {
        @SuppressLint("CheckResult")
        @Override
        public void onClick(View v) {
            setProgressBar(true);
            Observable.fromCallable(() -> {
                ScannerConstants.selectedImageBitmap = ImageUtils.rotateBitmap(ScannerConstants.selectedImageBitmap, 90);
                return false;
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe((result) -> {
                setProgressBar(false);
                tempBitmapOrginal = ImageUtils.rotateBitmap(tempBitmapOrginal, 90);
                imgBitmap.setImageBitmap(ScannerConstants.selectedImageBitmap);
            });
        }
    };

    private View.OnClickListener onRotateClickFromCropScreen = new View.OnClickListener() {
        @SuppressLint("CheckResult")
        @Override
        public void onClick(View v) {
            setProgressBar(true);
            Observable.fromCallable(() -> {
                ScannerConstants.selectedImageBitmap = ImageUtils.rotateBitmap(ScannerConstants.selectedImageBitmap, 90);
                return false;
            }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe((result) -> {
                setProgressBar(false);
                tempBitmapOrginal = ImageUtils.rotateBitmap(tempBitmapOrginal, 90);
                initializeCropping();
            });
        }
    };

    private String saveToInternalStorage(Bitmap bitmapImage) {
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "cropped_" + timeStamp + ".png";
        File mypath = new File(directory, imageFileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(mypath);
            bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return mypath.getAbsolutePath();
    }

    protected Bitmap getCroppedImage() {
        try {
            SparseArray<PointF> points = polygonView.getPoints();

            float xRatio = (float) selectedImageBitmap.getWidth() / imageView.getWidth();
            float yRatio = (float) selectedImageBitmap.getHeight() / imageView.getHeight();

            float x1 = (points.get(0).x) * xRatio;
            float x2 = (points.get(1).x) * xRatio;
            float x3 = (points.get(2).x) * xRatio;
            float x4 = (points.get(3).x) * xRatio;
            float y1 = (points.get(0).y) * yRatio;
            float y2 = (points.get(1).y) * yRatio;
            float y3 = (points.get(2).y) * yRatio;
            float y4 = (points.get(3).y) * yRatio;
            return nativeClass.getScannedBitmap(selectedImageBitmap, x1, y1, x2, y2, x3, y3, x4, y4);
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(ImageCropActivity.this, ScannerConstants.cropError, Toast.LENGTH_SHORT).show());
            return null;
        }
    }

    private Bitmap scaledBitmap(Bitmap bitmap, int width, int height) {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), new RectF(0, 0, width, height), Matrix.ScaleToFit.CENTER);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    private SparseArray<PointF> getEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointFs = getContourEdgePoints(tempBitmap);
        return orderedValidEdgePoints(tempBitmap, pointFs);
    }

    private List<PointF> getContourEdgePoints(Bitmap tempBitmap) {
        MatOfPoint2f point2f = nativeClass.getPoint(tempBitmap);
        if (point2f == null)
            point2f = new MatOfPoint2f();
        List<Point> points = Arrays.asList(point2f.toArray());
        List<PointF> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            result.add(new PointF(((float) points.get(i).x), ((float) points.get(i).y)));
        }
        return result;
    }

    private SparseArray<PointF> getOutlinePoints(Bitmap tempBitmap) {
        // set minWidth & minHeight to 0 to get proper outline points
        float minWidth = (float) (0.10 * tempBitmap.getWidth());
        float minHeight = (float) (0.10 * tempBitmap.getHeight());
        float maxWidth = tempBitmap.getWidth() - minWidth;
        float maxHeight = tempBitmap.getHeight() - minHeight;
        SparseArray<PointF> outlinePoints = new SparseArray<>();
        outlinePoints.put(0, new PointF(minWidth, minHeight));
        outlinePoints.put(1, new PointF(maxWidth, minHeight));
        outlinePoints.put(2, new PointF(minWidth, maxHeight));
        outlinePoints.put(3, new PointF(maxWidth, maxHeight));
        return outlinePoints;
    }

    private SparseArray<PointF> orderedValidEdgePoints(Bitmap tempBitmap, List<PointF> pointFs) {
        SparseArray<PointF> orderedPoints = polygonView.getOrderedPoints(pointFs);
        if (!polygonView.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap);
        }
        return orderedPoints;
    }
}
