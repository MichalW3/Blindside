package com.example.blindside;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Collections;

import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST = 1001;
    private static final int VIBRATE_REQUEST = 1002;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private String cameraId;
    private CameraCaptureSession cameraCaptureSession;
    private int cameraFacing;
    Size previewDimensions;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    RelativeLayout mainLayout;
    private int skipper = 0;
    private Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mainLayout = findViewById(R.id.mainLayout);
        mainLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int width = mainLayout.getMeasuredWidth();
                int height = (width * 4) / 3;
                int heightOld = textureView.getHeight();

                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) textureView.getLayoutParams();
                layoutParams.height = height;
                layoutParams.setMargins(0, (heightOld - height) / 2, 0, 0);
                textureView.setLayoutParams(layoutParams);
                mainLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

            }
        });
        openBackgroundThread();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }

        else {
            setUpCamera();
        }

    }

    private void setUpCamera() {
        textureView = findViewById(R.id.textureView);
        cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
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
        });
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
            previewDimensions = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
            cameraManager.openCamera(cameraId, stateCallBack, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void openBackgroundThread() {
        backgroundThread = new HandlerThread("camera_background_thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    CameraDevice.StateCallback stateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewDimensions.getWidth(), previewDimensions.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            try {
                final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(previewSurface);
                cameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            textureView = findViewById(R.id.textureView);

                            textureView.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                                @Override
                                public void onDraw() {
                                    if(skipper%10 == 9) {

                                        bitmap = textureView.getBitmap();
                                        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                                        int color;
                                        int accuracy = 20;
                                        int width = bitmap.getWidth();
                                        int height = bitmap.getHeight();
                                        //int[] pixels = new int[width*height];
                                        //bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                                        outer: for(int i=4;i<width-4;i=i+4)
                                        {
                                            for(int j=4;j<height-4;j=j+4)
                                            {
                                                 color = bitmap.getPixel(i,j);

                                                 if((Color.green(color) - Color.red(color))>accuracy && (Color.green(color) - Color.blue(color))>accuracy)
                                                 {
                                                     //bitmap.setPixel(i,j, Color.BLUE);
                                                         color = bitmap.getPixel(i+4,j+4);
                                                         if((Color.blue(color) - Color.red(color))>accuracy && (Color.blue(color) - Color.green(color))>accuracy)
                                                         {
                                                             Log.d(TAG, "Wykryto krawedz G-B!");
                                                             color = bitmap.getPixel(i-4, j+4);
                                                             if((Color.red(color) - Color.green(color)>accuracy) && (Color.red(color) - Color.blue(color))>accuracy)
                                                             {
                                                                 vibrator.vibrate(300);
                                                                 Log.d(TAG, "Wykryto znak!");
                                                                 break outer;
                                                             }
                                                         }
                                                 }
                                            }
                                        }
                                        //Log.d(TAG, "New image frame");
                                    }
                                    ++skipper;
                                }
                            });
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                    }
                }, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private static final String TAG = "MyActivity";
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {

            Image image = reader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Log.d(TAG, "New image frame");
            if (image != null)
                image.close();
        }
    };



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case CAMERA_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //setUpCamera();
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.VIBRATE}, VIBRATE_REQUEST);
                } else {
                    Toast.makeText(this, "Camera permission is required to run this application.", Toast.LENGTH_LONG).show();
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
                }
            }
            case VIBRATE_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    setUpCamera();
                } else {
                    Toast.makeText(this, "Vibrate permission is required to run this application.", Toast.LENGTH_LONG).show();
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.VIBRATE}, VIBRATE_REQUEST);
                }

            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundThread.quit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        openBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
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
            });
        }
    }
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

}

