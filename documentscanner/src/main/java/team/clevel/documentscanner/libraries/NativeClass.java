/*
 * *
 *  * Created by Muhammet Ali YÜCE on 3/5/19 4:26 PM
 *  * on Github: /mayuce
 *  * Copyright (c) 2019 . All rights reserved.
 *  * Last modified 3/4/19 4:56 PM
 *
 */

package team.clevel.documentscanner.libraries;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import team.clevel.documentscanner.helpers.ImageUtils;
import team.clevel.documentscanner.helpers.MathUtils;

public class NativeClass {

    static {
        System.loadLibrary("opencv_java3");
    }

    //private static final int THRESHOLD_LEVEL = 2;
    //private static final double AREA_LOWER_THRESHOLD = 0.2;
    //private static final double AREA_UPPER_THRESHOLD = 0.98;
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
    private List<MatOfPoint2f> getPoints(Mat src) {
        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint2f> rectangles = new ArrayList<>();
        //int srcArea = src.rows() * src.cols();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9.0, 9.0));
        Size size = new Size(src.size().width, src.size().height);
        Mat grayImage = new Mat(size, CvType.CV_8UC4);
        Mat cannedImage = new Mat(size, CvType.CV_8UC1);
        Mat dilate = new Mat(size, CvType.CV_8UC1);
        Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_RGBA2GRAY);
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5.0, 5.0), 0.0);
        //Imgproc.threshold(grayImage, grayImage, 20.0, 255.0, Imgproc.THRESH_TRIANGLE);
        //Imgproc.adaptiveThreshold(grayImage, grayImage, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 71, 7.0);
        Imgproc.Canny(grayImage, cannedImage, 255.0 / 3.0, 255.0);
        Imgproc.dilate(cannedImage, dilate, kernel);
        Mat hierarchy = new Mat();
        Imgproc.findContours(
                dilate,
                contours,
                hierarchy,
                Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE
        );
        for (MatOfPoint contour : contours) {
            MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
            double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;

            // Approximate polygonal curves.
             MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

            List<Point> approxPoints = Arrays.asList(approx.toArray());
            if (approxPoints.size() == 4) {
                //if(isRectangle(approx,srcArea))
                rectangles.add(approx);
            }
        }
        hierarchy.release();
        grayImage.release();
        cannedImage.release();
        kernel.release();
        dilate.release();
        return rectangles;
    }

    /*private boolean isRectangle(MatOfPoint2f polygon, int srcArea) {
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
        if (maxCosine >= 0.3) {
            return false;
        }
        return true;
    }*/

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

        Bitmap result = Bitmap.createBitmap(src_mat.cols(), src_mat.rows(),Bitmap.Config.ARGB_8888);
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
                if (R < 162 && G < 162 && B < 162)
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
