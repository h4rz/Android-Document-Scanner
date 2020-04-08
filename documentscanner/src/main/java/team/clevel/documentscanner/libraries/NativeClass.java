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
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
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

        return rectangles;
    }

    // CodeSquad's Code
    /*private List<MatOfPoint2f> getPoints(Mat src) {
        List<MatOfPoint> contours = new ArrayList<>();
        List<MatOfPoint2f> rectangles = new ArrayList<>();
        Mat grayImage;
        Mat cannedImage;
        Mat resizedImage;

        Size size = new Size(src.size().width, src.size().height);

        resizedImage = new Mat(size, CvType.CV_8UC4);
        grayImage = new Mat(size, CvType.CV_8UC4);
        cannedImage = new Mat(size, CvType.CV_8UC1);

        Imgproc.resize(src, resizedImage, size);
        Imgproc.cvtColor(resizedImage, grayImage, Imgproc.COLOR_RGBA2GRAY, 4);
        //Imgproc.equalizeHist(grayImage, grayImage);
        //CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        //clahe.apply(grayImage, grayImage);
        Imgproc.applyColorMap(grayImage, grayImage, Imgproc.COLORMAP_HSV);
        Imgproc.GaussianBlur(grayImage, grayImage, new Size(5, 5), 0);
        Imgproc.Canny(grayImage, cannedImage, 80, 100, 3, false);
        //Imgproc.Canny(grayImage, cannedImage, 75, 200, 3, false);
        Mat hierarchy = new Mat();

        Imgproc.findContours(cannedImage, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        hierarchy.release();

        Collections.sort(contours, new Comparator<MatOfPoint>() {

            @Override
            public int compare(MatOfPoint lhs, MatOfPoint rhs) {
                return Double.compare(Imgproc.contourArea(rhs), Imgproc.contourArea(lhs));
            }
        });

        resizedImage.release();
        grayImage.release();
        cannedImage.release();

        for (MatOfPoint contour : contours) {
            MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
            double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;

            // Approximate polygonal curves.
             MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

            Point[] approxPoints = sortPoints(approx.toArray());
            if (insideArea(approxPoints, size)) {
                //if(isRectangle(approx,srcArea))
                rectangles.add(approx);
            }
        }
        return rectangles;
    }

    private boolean insideArea(Point[] rp, Size size) {

        int width = Double.valueOf(size.width).intValue();
        int height = Double.valueOf(size.height).intValue();

        int minimumSize = width / 10;

        boolean isANormalShape = rp[0].x != rp[1].x && rp[1].y != rp[0].y && rp[2].y != rp[3].y && rp[3].x != rp[2].x;
        boolean isBigEnough = ((rp[1].x - rp[0].x >= minimumSize) && (rp[2].x - rp[3].x >= minimumSize)
                && (rp[3].y - rp[0].y >= minimumSize) && (rp[2].y - rp[1].y >= minimumSize));

        double leftOffset = rp[0].x - rp[3].x;
        double rightOffset = rp[1].x - rp[2].x;
        double bottomOffset = rp[0].y - rp[1].y;
        double topOffset = rp[2].y - rp[3].y;

        boolean isAnActualRectangle = ((leftOffset <= minimumSize && leftOffset >= -minimumSize)
                && (rightOffset <= minimumSize && rightOffset >= -minimumSize)
                && (bottomOffset <= minimumSize && bottomOffset >= -minimumSize)
                && (topOffset <= minimumSize && topOffset >= -minimumSize));

        return isANormalShape && isAnActualRectangle && isBigEnough;
    }

    private Point[] sortPoints(Point[] src) {

        ArrayList<Point> srcPoints = new ArrayList<>(Arrays.asList(src));

        Point[] result = { null, null, null, null };

        Comparator<Point> sumComparator = new Comparator<Point>() {
            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.compare(lhs.y + lhs.x, rhs.y + rhs.x);
            }
        };

        Comparator<Point> diffComparator = new Comparator<Point>() {

            @Override
            public int compare(Point lhs, Point rhs) {
                return Double.compare(lhs.y - lhs.x, rhs.y - rhs.x);
            }
        };

        // top-left corner = minimal sum
        result[0] = Collections.min(srcPoints, sumComparator);

        // bottom-right corner = maximal sum
        result[2] = Collections.max(srcPoints, sumComparator);

        // top-right corner = minimal difference
        result[1] = Collections.min(srcPoints, diffComparator);

        // bottom-left corner = maximal difference
        result[3] = Collections.max(srcPoints, diffComparator);

        return result;
    }*/

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
