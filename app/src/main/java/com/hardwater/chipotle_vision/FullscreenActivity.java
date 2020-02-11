package com.hardwater.chipotle_vision;

import android.Manifest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.Collections;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {

    CameraManager cam;
    String camID;
    TextureView texView;
    TextureView.SurfaceTextureListener stl;
    Size previewSize;
    HandlerThread handlerThread;
    Handler handler;
    CameraDevice.StateCallback callback;
    CameraDevice device;
    CameraCaptureSession session;
    CaptureRequest captureRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ask for permissions
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);

        // Get the texture view from the layout
        setContentView(R.layout.activity_fullscreen);
        texView = findViewById(R.id.textureView);

        // Grab the camera manager
        cam = (CameraManager)(getSystemService(CAMERA_SERVICE));

        callback = new CameraDevice.StateCallback() { // device callback
            @Override
            public void onOpened(CameraDevice cameraDevice) {
                device = cameraDevice;
                createPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                cameraDevice.close();
                device = null;
            }

            @Override
            public void onError(CameraDevice cameraDevice, int error) {
                cameraDevice.close();
                device = null;
            }
        };


        stl = new TextureView.SurfaceTextureListener() { // listener for the texture view's surface
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                // initialize camera
                setUpCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };

        texView.setSurfaceTextureListener(stl); // add the listener to the texture view
    }

    /**
     * After initializing the camera, attach it to the surface texture and create the capture session
     */
    private void createPreviewSession() {
        try {
            SurfaceTexture texture = texView.getSurfaceTexture(); // get the surface texture
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight()); // set the texture size to the max
            Surface surface = new Surface(texture);
            final CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(surface);

            device.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession ccs) {

                    if(device == null) // make sure the device exists
                        return;

                    try {
                        captureRequest = builder.build();
                        session = ccs;
                        session.setRepeatingRequest(captureRequest, null, handler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the camera
     */
    private void openCamera() {

        try {
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cam.openCamera(camID, callback, handler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        openBackgroundThread();

        // resume the thread and reopen the camera
        if(texView != null) {
            if (texView.isAvailable()) {
                setUpCamera();
                openCamera();
            } else {
                texView.setSurfaceTextureListener(stl);
            }
        }
    }

    private void setUpCamera() {

        try {
            CameraCharacteristics characteristics;
            for(String cameraID : cam.getCameraIdList()) { // iterate through all found camera devices to find the back-facing camera
                characteristics = cam.getCameraCharacteristics(cameraID);
                if(characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    // set preview size to the max possible for the surface texture
                    previewSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class)[0];
                }
                camID = "0"; // TODO: work out how to find the primary rear camera; this fix is temporary
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
        closeBackgroundThread();
    }

    private void closeCamera() {
        if (session != null) {
            session.close();
            session = null;
        }

        if (device != null) {
            device.close();
            device = null;
        }
    }

    private void closeBackgroundThread() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
            handler = null;
        }
    }

    private void openBackgroundThread() {
        handlerThread = new HandlerThread("camera_background_thread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }


}
