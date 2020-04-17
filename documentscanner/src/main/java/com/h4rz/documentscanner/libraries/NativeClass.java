/*
 * *
 *  * Created by Muhammet Ali YÜCE on 3/5/19 4:26 PM
 *  * on Github: /mayuce
 *  * Copyright (c) 2019 . All rights reserved.
 *  * Last modified 3/4/19 4:56 PM
 *
 */

package com.h4rz.documentscanner.libraries;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.h4rz.documentscanner.helpers.ImageUtils;
import com.h4rz.documentscanner.helpers.MathUtils;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NativeClass {

    static {
        System.loadLibrary("opencv_java3");
    }

    private static final int THRESHOLD_LEVEL = 2;
    private static final double AREA_LOWER_THRESHOLD = 0.2;
    private static final double AREA_UPPER_THRESHOLD = 0.98;
    private static final double DOWNSCALE_IMAGE_SIZE = 600f;

    public Bitmap getScannedBitmap(Bitmap bitmap, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        PerspectiveTransformation perspective = new PerspectiveTransformation();
        MatOfPoint2f rectangle = new MatOfPoint2f();
        rectangle.fromArray(new Point(x1, y1), new Point(x2, y2), new Point(x3, y3), new Point(x4, y4));
        Mat dstMat = perspective.transform(ImageUtils.bitmapToMat(bitmap), rectangle);
        return ImageUtils.matToBitmap(dstMat);
    }

    private static Comparator<MatOfPoint2f> AreaDescendingComparator = (m1, m2) -> {
        double area1 = Imgproc.contourArea(m1);
        double area2 = Imgproc.contourArea(m2);
        return (int) Math.ceil(area2 - area1);
    };

    public MatOfPoint2f getPoint(Bitmap bitmap) {
        Mat src = ImageUtils.bitmapToMat(bitmap);
        // Downscale image for better performance.
        double ratio = DOWNSCALE_IMAGE_SIZE / Math.max(src.width(), src.height());
        Size downscaledSize = new Size(src.width() * ratio, src.height() * ratio);
        Mat downscaled = new Mat(downscaledSize, src.type());
        Imgproc.resize(src, downscaled, downscaledSize);

        List<MatOfPoint2f> rectangles = getPoints(downscaled);
        if (rectangles.size() == 0) {
            return null;
        }
        Collections.sort(rectangles, AreaDescendingComparator);
        MatOfPoint2f largestRectangle = rectangles.get(0);
        return MathUtils.scaleRectangle(largestRectangle, 1f / ratio);
    }

    //public native float[] getPoints(Bitmap bitmap);
    // Harsh Code
    private List<MatOfPoint2f> getPoints(Mat src) {
        // Blur the image to filter out the noise.
        Bitmap bitmap = Bitmap.createBitmap((int) src.size().width, (int) src.size().height, Bitmap.Config.ARGB_8888);
        Mat blurred = new Mat();
        //bitmap = ImageUtils.matToBitmap(src);
        Imgproc.medianBlur(src, blurred, 9);
        //bitmap = ImageUtils.matToBitmap(blurred);
        // Set up images to use.
        Mat gray0 = new Mat(blurred.size(), CvType.CV_8U);
        Mat gray = new Mat();

        // For Core.mixChannels.
        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint2f> rectangles = new ArrayList<>();

        List<Mat> sources = new ArrayList<>();
        sources.add(blurred);
        List<Mat> destinations = new ArrayList<>();
        destinations.add(gray0);

        // To filter rectangles by their areas.
        int srcArea = src.rows() * src.cols();
        // Find squares in every color plane of the image.
        for (int c = 0; c < 3; c++) {
            int[] ch = {c, 0};
            MatOfInt fromTo = new MatOfInt(ch);
            Core.mixChannels(sources, destinations, fromTo);
            // Try several threshold levels.
            for (int l = 0; l < THRESHOLD_LEVEL; l++) {
                if (l == 0) {
                    // HACK: Use Canny instead of zero threshold level.
                    // Canny helps to catch squares with gradient shading.
                    // NOTE: No kernel size parameters on Java API.
                    Imgproc.Canny(gray0, gray, 255.0 / 3.0, 255.0);

                    // Dilate Canny output to remove potential holes between edge segments.
                    Imgproc.dilate(gray, gray, Mat.ones(new Size(3, 3), 0));
                    //bitmap = ImageUtils.matToBitmap(gray);
                } else {
                    int threshold = (l + 1) * 255 / THRESHOLD_LEVEL;
                    Imgproc.threshold(gray0, gray, threshold, 255, Imgproc.THRESH_BINARY);
                    //bitmap = ImageUtils.matToBitmap(gray);
                }
                // Find contours and store them all as a list.
                Imgproc.findContours(gray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

                for (MatOfPoint contour : contours) {
                    MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
                    double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;

                    // Approximate polygonal curves.
                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

                    if (isRectangle(approx, srcArea)) {
                        rectangles.add(approx);
                    }
                }
            }
        }

        if (rectangles.size() != 0)
            return rectangles;
        return getRectanglesFromTextDetection(src);
    }

    private List<MatOfPoint2f> getRectanglesFromTextDetection(Mat originalMat) {
        Log.i("***** NATIVE CLASS - ", "Detecting from text");
        Mat src = originalMat;
        Bitmap enhancedBitmap = ImageUtils.matToBitmap(src);
        enhancedBitmap = applyMagicFilter(enhancedBitmap);
        src = ImageUtils.bitmapToMat(enhancedBitmap);
        Scalar CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
        Mat mGrey = new Mat(src.size(), CvType.CV_8UC4);
        Imgproc.cvtColor(src, mGrey, Imgproc.COLOR_RGBA2GRAY);
        Mat mRgba = new Mat(src.size(), CvType.CV_8UC4);
        mRgba = src;
        List<MatOfPoint2f> rectangles = new ArrayList<>();
        MatOfKeyPoint keypoint = new MatOfKeyPoint();
        List<KeyPoint> listpoint;
        KeyPoint kpoint;
        Mat mask = Mat.zeros(mGrey.size(), CvType.CV_8UC1);
        int rectanx1;
        int rectany1;
        int rectanx2;
        int rectany2;
        int imgsize = mGrey.height() * mGrey.width();
        Scalar zeos = new Scalar(0, 0, 0);

        List<MatOfPoint> contour2 = new ArrayList<MatOfPoint>();
        Mat kernel = new Mat(1, 50, CvType.CV_8UC1, Scalar.all(255));
        Mat morbyte = new Mat();
        Mat hierarchy = new Mat();

        Rect rectan3;

        FeatureDetector detector = FeatureDetector
                .create(FeatureDetector.MSER);
        detector.detect(mGrey, keypoint);
        listpoint = keypoint.toList();

        for (int ind = 0; ind < listpoint.size(); ind++) {
            kpoint = listpoint.get(ind);
            rectanx1 = (int) (kpoint.pt.x - 0.5 * kpoint.size);
            rectany1 = (int) (kpoint.pt.y - 0.5 * kpoint.size);
            rectanx2 = (int) (kpoint.size);
            rectany2 = (int) (kpoint.size);
            if (rectanx1 <= 0)
                rectanx1 = 1;
            if (rectany1 <= 0)
                rectany1 = 1;
            if ((rectanx1 + rectanx2) > mGrey.width())
                rectanx2 = mGrey.width() - rectanx1;
            if ((rectany1 + rectany2) > mGrey.height())
                rectany2 = mGrey.height() - rectany1;
            Rect rectant = new Rect(rectanx1, rectany1, rectanx2, rectany2);
            try {
                Mat roi = new Mat(mask, rectant);
                roi.setTo(CONTOUR_COLOR);
            } catch (Exception ex) {
                Log.d("mylog", "mat roi error " + ex.getMessage());
            }
        }

        Imgproc.morphologyEx(mask, morbyte, Imgproc.MORPH_DILATE, kernel);
        Imgproc.findContours(morbyte, contour2, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
        List<MatOfPoint> usefulContours = new ArrayList<MatOfPoint>();
        for (int ind = 0; ind < contour2.size(); ind++) {
            //rectan3 = Imgproc.boundingRect(contour2.get(ind));
            rectan3 = Imgproc.boundingRect(contour2.get(ind));
            if (rectan3.area() > 0.5 * imgsize || rectan3.area() < 100
                    || rectan3.width / rectan3.height < 2) {
                Mat roi = new Mat(morbyte, rectan3);
                roi.setTo(zeos);

            } else
                usefulContours.add(contour2.get(ind));
        }

        Rect firstRect = new Rect();
        Rect lastRect = new Rect();


        for (int ind = 0; ind < usefulContours.size(); ind++) {
            rectan3 = Imgproc.boundingRect(contour2.get(ind));
            if (ind == 0) {
                lastRect = rectan3;
            } else if (ind == usefulContours.size() - 1) {
                firstRect = rectan3;
            }
            //Imgproc.rectangle(mRgba, rectan3.br(), rectan3.tl(), CONTOUR_COLOR);
        }

        if (firstRect.width > lastRect.width)
            lastRect.width = firstRect.width;
        else
            firstRect.width = lastRect.width;

        Rect newRectangle = new Rect(lastRect.br(), firstRect.tl());

        Imgproc.rectangle(mRgba, newRectangle.br(), newRectangle.tl(), CONTOUR_COLOR);

        List<Point> points = new ArrayList<>();
        int minX = newRectangle.x;
        int minY = newRectangle.y;
        int maxX = newRectangle.x + (newRectangle.width + 100);
        int maxY = newRectangle.y + newRectangle.height;
        if (maxX > mRgba.width())
            maxX = mRgba.width();
        if (maxY > mRgba.height())
            maxX = mRgba.height();
        if (minX < 0)
            minX = 0;
        if (minY < 0)
            minY = 0;
        Point topLeft = new Point(minX, minY);
        Point topRight = new Point(maxX, minY);
        Point bottomLeft = new Point(maxX, maxY);
        Point bottomRight = new Point(minX, maxY);
        points.add(topLeft);
        points.add(topRight);
        points.add(bottomLeft);
        points.add(bottomRight);

        MatOfPoint temp = new MatOfPoint();
        temp.fromList(points);

        MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(temp);
        double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;

        // Approximate polygonal curves.
        MatOfPoint2f approx = new MatOfPoint2f();
        Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

        //if (isRectangle(approx, srcArea)) {
        rectangles.add(approx);
        //}
        return rectangles;
    }


    private boolean isRectangle(MatOfPoint2f polygon, int srcArea) {
        MatOfPoint polygonInt = MathUtils.toMatOfPointInt(polygon);

        if (polygon.rows() != 4) {
            return false;
        }

        double area = Math.abs(Imgproc.contourArea(polygon));
        if (area < srcArea * AREA_LOWER_THRESHOLD || area > srcArea * AREA_UPPER_THRESHOLD) {
            return false;
        }

        if (!Imgproc.isContourConvex(polygonInt)) {
            return false;
        }

        // Check if the all angles are more than 72.54 degrees (cos 0.3).
        double maxCosine = 0;
        Point[] approxPoints = polygon.toArray();
        for (int i = 2; i < 5; i++) {
            double cosine = Math.abs(MathUtils.angle(approxPoints[i % 4], approxPoints[i - 2], approxPoints[i - 1]));
            maxCosine = Math.max(cosine, maxCosine);
        }
        return !(maxCosine >= 0.3);
    }

    public Bitmap applyMagicFilter(Bitmap selectedImageBitmap) {
        Mat src_mat = new Mat();
        Utils.bitmapToMat(selectedImageBitmap, src_mat);
        /*Bitmap noiseRemoved = selectedImageBitmap.copy(selectedImageBitmap.getConfig(), true);
        noiseRemoved = removeNoise(noiseRemoved);
        Utils.bitmapToMat(noiseRemoved, src_mat);*/

        /*//Method 3 - Takes Time
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2.0, 2.0));
        Imgproc.erode(src_mat, src_mat, kernel);
        Imgproc.threshold(src_mat, src_mat, 127.0, 255.0, Imgproc.THRESH_BINARY);
        List<Mat> rgbList = new ArrayList<>();
        Core.split(src_mat, rgbList);
        Imgproc.cvtColor(src_mat, src_mat, Imgproc.COLOR_RGB2GRAY);
        Imgproc.GaussianBlur(src_mat, src_mat, new Size(3, 3), 0.0);
        Imgproc.threshold(src_mat, src_mat, 0, 255.0, Imgproc.THRESH_OTSU);
        Imgproc.cvtColor(src_mat, src_mat, Imgproc.COLOR_GRAY2RGB);
        Core.merge(rgbList, src_mat);*/

        // Method 4 - Codesquad
        Imgproc.cvtColor(src_mat, src_mat, Imgproc.COLOR_RGBA2GRAY);
        double colorGain = 1; // contrast
        double colorBias = 10; // bright
        src_mat.convertTo(src_mat, CvType.CV_8UC1, colorGain, colorBias);
        Imgproc.adaptiveThreshold(src_mat, src_mat, 255, 1, 0, 41, 7);

        Bitmap result = Bitmap.createBitmap(src_mat.cols(), src_mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(src_mat, result);
        src_mat.release();
        return result;
    }

    public Bitmap removeNoise(Bitmap bmap) {
        for (int x = 0; x < bmap.getWidth(); x++) {
            for (int y = 0; y < bmap.getHeight(); y++) {
                int pixel = bmap.getPixel(x, y);
                int R = Color.red(pixel);
                int G = Color.green(pixel);
                int B = Color.blue(pixel);
                if (R == 0 && G == 0 && B == 0)
                    bmap.setPixel(x, y, Color.BLACK);
            }
        }
        /*for (int  x = 0; x < bmap.getWidth(); x++) {
            for (int y = 0; y < bmap.getHeight(); y++) {
                int pixel = bmap.getPixel(x, y);
                int R = Color.red(pixel);
                int G = Color.green(pixel);
                int B = Color.blue(pixel);
                if (R > 162 && G > 162 && B > 162)
                    bmap.setPixel(x, y, Color.WHITE);
            }
        }*/
        return bmap;
    }
}
