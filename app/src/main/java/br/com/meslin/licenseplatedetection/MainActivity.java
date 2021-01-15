/*
From: https://www.youtube.com/watch?v=Tu1808Mum8Q
(and from: https://www.youtube.com/watch?v=pzuwrYgOnDQ)
 */
package br.com.meslin.licenseplatedetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static String TAG = "LicensePlateDetection";

    // Views
    private JavaCameraView javaCameraView;
    private ImageView imageView;

    private Mat mRGBA, mGrey;
    private File cascFile;
    private CascadeClassifier plateDetector;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            //Log.d(TAG, "onManagerConnected 0");
            byte[] buffer = new byte[4096];
            int bytesRead;

            switch(status) {
                case LoaderCallbackInterface.SUCCESS:
                    InputStream is = getResources().openRawResource(R.raw.haarcascade_russian_plate_number);
                    File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                    cascFile = new File(cascadeDir, "haarcascade_russian_plate_number.xml");
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(cascFile);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    while(true) {
                        try {
                            if (!((bytesRead = is.read(buffer)) != -1)) break;
                            fos.write(buffer, 0, bytesRead);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        is.close();
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    plateDetector = new CascadeClassifier(cascFile.getAbsolutePath());
                    if(plateDetector.empty()) {
                        plateDetector = null;
                    }
                    else {
                        cascadeDir.delete();
                    }
                    javaCameraView.enableView();
                    break;

                default:
                    super.onManagerConnected(status);
            }
            //Log.d(TAG, "onManagerConnected -0");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView) findViewById(R.id.imageView);

        // Checks for camera permission
        // From: https://developer.android.com/training/permissions/requesting#java
        if(!(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)) {
            // Ask user to grand camera permission
            // From: https://stackoverflow.com/questions/38552144/how-get-permission-for-camera-in-android-specifically-marshmallow
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }

        OpenCVLoader.initDebug();

        javaCameraView = (JavaCameraView) findViewById(R.id.javaCamView);
        if(OpenCVLoader.initDebug()) {
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, baseLoaderCallback);
        }

        javaCameraView.setCvCameraViewListener(this);
    }

    /**
     * This method is invoked when camera preview has started. After this method is invoked
     * the frames will start to be delivered to client via the onCameraFrame() callback.
     *
     * @param width  -  the width of the frames that will be delivered
     * @param height - the height of the frames that will be delivered
     */
    @Override
    public void onCameraViewStarted(int width, int height) {
        //Log.d(TAG, "onCameraViewStated 0");
        mRGBA = new Mat();
        mGrey = new Mat();
        //Log.d(TAG, "onCameraViewStated -0");
    }

    /**
     * This method is invoked when camera preview has been stopped for some reason.
     * No frames will be delivered via onCameraFrame() callback after this method is called.
     */
    @Override
    public void onCameraViewStopped() {
        //Log.d(TAG, "onCameraViewStopped 0");
        mRGBA.release();
        mGrey.release();
        //Log.d(TAG, "onCameraViewStopped -0");
    }

    /**
     * This method is invoked when delivery of the frame needs to be done.
     * The returned values - is a modified frame which needs to be displayed on the screen.
     * TODO: pass the parameters specifying the format of the frame (BPP, YUV or RGB and etc)
     *
     * @param inputFrame
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        long startTime, endTime, deltaTime;
        //Log.d(TAG, "onCameraFrame 0");
        mRGBA = inputFrame.rgba();
        mGrey = inputFrame.gray();

        // detect plate
        MatOfRect plateDetections = new MatOfRect();
        plateDetector.detectMultiScale(mRGBA, plateDetections);
        // Exibe os retângulos
        int maxArea =0;
        Mat plate =null;
        for (Rect rect : plateDetections.toArray()) {
            startTime = System.currentTimeMillis();
            Imgproc.rectangle(mRGBA, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(255,0,0));
            endTime = System.currentTimeMillis();
            deltaTime = endTime - startTime;
            try {
                Log.d(TAG, "Processing time: " + String.format("%d ms", deltaTime));
            } catch (Exception e) {
                Log.d(TAG, "imageView: " + e.toString());
            }
            int area = rect.width * rect.height;
            if(area > maxArea) {
                startTime = System.currentTimeMillis();
                plate = mRGBA.submat(rect);
                Bitmap bmp = Bitmap.createBitmap(plate.cols(), plate.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(plate, bmp);
                // The very first, and only the very first, image troughs an exception, and I don't know why!
                try {
                    imageView.setImageBitmap(bmp);
                } catch (Exception e) {
                    Log.d(TAG, "imageView: " + e.toString());
                }
                endTime = System.currentTimeMillis();
                deltaTime = endTime - startTime;
                Log.d(TAG, "Sending time: " + String.format("%d ms", deltaTime));
            }
        }

        //Log.d(TAG, "onCameraFrame -0");
        return mRGBA;
    }

    // Request permission result handler
    // From: https://stackoverflow.com/questions/38552144/how-get-permission-for-camera-in-android-specifically-marshmallow
    /**
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onRequestPermissionResult: camera permission granted");
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
                Log.d(TAG, "onRequestPermissionResult: camera permission denied");
            }
        }
    }
}
