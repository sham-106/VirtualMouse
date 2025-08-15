package me.shambhavi;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.highgui.HighGui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

/**
 * Virtual Mouse implementation using OpenCV for color-based fingertip tracking
 * and Java Robot API for controlling mouse movements and clicks.
 */
public class Main {
    // Load the OpenCV native library
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    // === CONFIGURABLE HSV COLOR RANGES ===
    // Blue marker (index finger) HSV range - broadened to handle lighting variation
    private static final Scalar LOWER_BLUE = new Scalar(90, 60, 60);
    private static final Scalar UPPER_BLUE = new Scalar(150, 255, 255);

    // Red marker (thumb) HSV ranges (two ranges to cover hue wrap-around)
    private static final Scalar LOWER_RED1 = new Scalar(0, 80, 80);
    private static final Scalar UPPER_RED1 = new Scalar(10, 255, 255);
    private static final Scalar LOWER_RED2 = new Scalar(170, 80, 80);
    private static final Scalar UPPER_RED2 = new Scalar(180, 255, 255);

    public static void main(String[] args) throws AWTException {

        // Initialize video capture (camera index 0)
        VideoCapture camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.err.println("Error: Camera not detected.");
            return;
        }

        // Create a single Swing window for previewing the camera feed
        JFrame frameWindow = new JFrame("Virtual Mouse Preview");
        frameWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel imageLabel = new JLabel();
        frameWindow.getContentPane().add(imageLabel);
        frameWindow.pack();
        frameWindow.setVisible(true);

        // Prepare OpenCV matrix for frames and Robot for controlling mouse
        Mat frame = new Mat();
        Robot robot = new Robot();

        // Determine screen dimensions for mapping
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;

        // Smoothing factor for cursor movement to reduce jitter
        double smoothFactor = 0.85;
        Point prevMousePos = new Point(screenWidth / 2, screenHeight / 2);

        // State for click detection to prevent repeated clicks
        boolean clickActive = false;
        long clickCooldown = 300; // milliseconds between clicks
        long lastClickTime = 0;

        // Radius for drawing detection circles and click threshold distance
        int drawRadius = 20;
        double clickDistance = drawRadius * 2.0;

        // Main processing loop
        while (true) {
            // Read a frame from the camera
            if (!camera.read(frame)) continue;
            // Mirror the frame for intuitive interaction
            Core.flip(frame, frame, 1);

            // Convert RGB frame to HSV color space for color detection
            Mat hsv = new Mat();
            Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV);

            // Generate binary masks for blue (index) and red (thumb)
            Mat maskBlue = new Mat();
            Core.inRange(hsv, LOWER_BLUE, UPPER_BLUE, maskBlue);
            Mat maskRed1 = new Mat(), maskRed2 = new Mat(), maskRed = new Mat();
            Core.inRange(hsv, LOWER_RED1, UPPER_RED1, maskRed1);
            Core.inRange(hsv, LOWER_RED2, UPPER_RED2, maskRed2);
            Core.add(maskRed1, maskRed2, maskRed);

            // Morphs the image so it's easier to process
            // Noise reduction using morphological opening (erosion followed by dilation)
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3,3));
            Imgproc.morphologyEx(maskBlue, maskBlue, Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_OPEN, kernel);

            // Processes the morphed image
            // Detect contours in each mask
            List<MatOfPoint> contoursBlue = new ArrayList<>();
            List<MatOfPoint> contoursRed = new ArrayList<>();
            Imgproc.findContours(maskBlue, contoursBlue, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            Imgproc.findContours(maskRed, contoursRed, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Compute the centers of the largest blue and red contours
            Point indexTip = getLargestContourCenter(contoursBlue);
            Point thumbTip = getLargestContourCenter(contoursRed);

            // If blue index tip detected, map its position to screen and move cursor
            if (indexTip != null) {
                // Draw a circle at the detected index tip location
                Imgproc.circle(frame, indexTip, drawRadius, new Scalar(255,0,0), -1);
                // Map camera coordinates proportionally to screen coordinates
                double mappedX = (indexTip.x / frame.width()) * screenWidth;
                double mappedY = (indexTip.y / frame.height()) * screenHeight;
                // Smooth movement by combining previous and new positions
                double smoothX = smoothFactor * prevMousePos.x + (1 - smoothFactor) * mappedX;
                double smoothY = smoothFactor * prevMousePos.y + (1 - smoothFactor) * mappedY;

                // Use the robot class to move mouse in the system
                robot.mouseMove((int)smoothX, (int)smoothY);
                prevMousePos.x = smoothX;
                prevMousePos.y = smoothY;
            }

            // If both tips detected, compute distance and trigger click when they touch
            if (indexTip != null && thumbTip != null) {

                Imgproc.circle(frame, thumbTip, drawRadius, new Scalar(0,0,255), -1);
                double dist = Math.hypot(indexTip.x - thumbTip.x, indexTip.y - thumbTip.y);
                long now = System.currentTimeMillis();

                // Trigger click when distance below threshold and cooldown elapsed
                if (dist < clickDistance && !clickActive && now - lastClickTime > clickCooldown) {
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    clickActive = true;
                    lastClickTime = now;
                } else if (dist >= clickDistance) {
                    // Reset click state when fingers separate
                    clickActive = false;
                }
            }

            // Update the single Swing preview window
            BufferedImage img = matToBufferedImage(frame);
            imageLabel.setIcon(new ImageIcon(img));
            frameWindow.pack();
        }
    }

    /**
     * Returns the center point of the largest contour from a list.
     * @param contours List of contours to examine
     * @return Center of bounding rect of largest contour, or null if none
     */
    private static Point getLargestContourCenter(List<MatOfPoint> contours) {
        double maxArea = 0;
        Rect maxRect = null;

        // Loop through all available matches for points and picks the biggest one
        for (MatOfPoint c : contours) {
            double area = Imgproc.contourArea(c);
            if (area > maxArea) {
                maxArea = area;
                maxRect = Imgproc.boundingRect(c);
            }
        }

        // Sends the location of the biggest one, used earlier within our code
        return (maxRect != null)
                ? new Point(maxRect.x + maxRect.width/2, maxRect.y + maxRect.height/2)
                : null;
    }

    /**
     * Converts an OpenCV Mat object to a BufferedImage for Swing display.
     * @param mat Input BGR or grayscale Mat
     * @return BufferedImage representation
     */
    private static BufferedImage matToBufferedImage(Mat mat) {
        int type = mat.channels() == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
        BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
        mat.get(0, 0, ((DataBufferByte)image.getRaster().getDataBuffer()).getData());
        return image;
    }
}
