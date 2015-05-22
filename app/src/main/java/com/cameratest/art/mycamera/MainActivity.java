package com.cameratest.art.mycamera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class MainActivity extends Activity implements SensorEventListener {

    private final static String TAG = "MainActivity";
    private Size mPreviewSize;

    private TextureView mTextureView;
    private TextView mTextView;
    private CameraDevice mCameraDevice;
    private ImageView imgSource,imgTarget;
    private ImageView imgHue,imgSat,imgVal;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mPreviewSession;
    private int count=0;
    private Button mBtnShot;
    private SensorManager mSensorManager;
    private Sensor mOrientation;
    private float azimuth_angle,pitch_angle,roll_angle;
    private Vector<float[]> lights;
    private Vector<float[]> lightHSV;
    private float[] angles = {0.0f,0.0f,0.0f};
    private boolean isThereLight=false;
    private Bitmap img;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        mTextureView = (TextureView)findViewById(R.id.texture);
        mTextView = (TextView)findViewById(R.id.editText);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        lights = new Vector<float[]>();
        lightHSV=new Vector<float[]>();
        img=null;

    }

    public boolean checkLights(){
        float[] hsv = new float[3];

        for(int row=0;row<img.getHeight();row++){
            for(int col=0;col<img.getWidth();col++){
                int color = img.getPixel(col,row);
                int a = Color.alpha(color);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                Color.RGBToHSV(r, g, b, hsv);

                Log.v(TAG,"Looping through image");
                if(hsv[1]>0 && hsv[1]<0.3 && hsv[2]==255)
                    isThereLight=true;

                if(isThereLight){
                    //Log.v("MainActivity","THERE IS LIGHT###########################");
                    float[] pos = new float[3];
                    pos[0]=azimuth_angle-180;
                    pos[1]=pitch_angle;
                    pos[2]=roll_angle;
                    if(lights.size()==0) {
                        lights.add(pos);
                        lightHSV.add(hsv);
                    }else if((azimuth_angle+15.0f)%360.0f>lights.lastElement()[0]
                            ||(azimuth_angle-15.0f)%360.0f<lights.lastElement()[0])
                        {
                            boolean check = false;
                            boolean lightCol = false;
                            int index =0;
                            for(int i=0;i<lights.size();i++){
                                if(lights.get(i)[0]<azimuth_angle+15 ||lights.get(i)[0]>azimuth_angle-15)
                                    check=true;

                                if(lightHSV.get(i)[0]!=hsv[0]||lightHSV.get(i)[1]!=hsv[1]||lightHSV.get(i)[2]!=hsv[2]){
                                    lightCol=true;
                                }
                            }
                            if(!check) {
                                lights.add(pos);
                                lightHSV.add(hsv);
                                count++;
                                return true;
                            }else if(!lightCol){
                                lightHSV.set(index,hsv);
                            }
                        }
                }
            }
        }
        return false;
    }

    protected void takePicture() {
        Log.e(TAG, "takePicture");
        if(null == mCameraDevice) {
            Log.e(TAG, "mCameraDevice is null, return");
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraDevice.getId());

            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics
                        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            final File file = new File(Environment.getExternalStorageDirectory()+"/DCIM", "pic"+count+".jpg");

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {

                @Override
                public void onImageAvailable(ImageReader reader) {

                    Image image = null;
                    //try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        img = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                        if(img!=null) {
                            Log.v("MainActivity", "Yippeeeee");
                            if(checkLights());
                            {
                                Log.v(TAG,"------------Light found----------------");
//                                try{
//                                    save(bytes);
//                                }
//                                catch(IOException e){
//
//                                }
                            }
                        }
                        else Log.v("MainActivity","NOOOOOOOOO");

                        //save(bytes);
                    //} catch (FileNotFoundException e) {
                      //  e.printStackTrace();
                    //} //catch (IOException e) {
                    //    e.printStackTrace();
                    //} finally {
                    //    if (image != null) {
                    //        image.close();
                     //   }
                    //}
                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }

            };
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        mSensorManager.registerListener(this,mOrientation,SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void openCamera() {

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "openCamera E");
        try {
            String cameraId = manager.getCameraIdList()[1];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG,"Cannot open Camera");
        }
        Log.e(TAG, "openCamera X");
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener(){

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable, width="+width+",height="+height);
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                                int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            //Log.e(TAG, "onSurfaceTextureUpdated");
//            count++;
            mTextView.setText(""+azimuth_angle+","+pitch_angle+","+roll_angle);
            takePicture();
        }

    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {

            Log.e(TAG, "onOpened");
            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {

            Log.e(TAG, "onDisconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {

            Log.e(TAG, "onError");
        }

    };

    @Override
    protected void onPause() {

        Log.e(TAG, "onPause");
        super.onPause();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event){
        azimuth_angle=event.values[0];
        pitch_angle=event.values[1];
        roll_angle=event.values[2];
    }

    protected void startPreview() {

        if(null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            Log.e(TAG, "startPreview fail, return");
            return;
        }

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if(null == texture) {
            Log.e(TAG,"texture is null, return");
            return;
        }

        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {

            Log.e(TAG,"Cannot start preview");
        }
        mPreviewBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {

                    mPreviewSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                    Toast.makeText(MainActivity.this, "onConfigureFailed", Toast.LENGTH_LONG).show();
                }
            }, null);
        } catch (CameraAccessException e) {

            Log.e(TAG,"Cannot create camera capture session");
        }
    }

    protected void updatePreview() {

        if(null == mCameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }

        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        HandlerThread thread = new HandlerThread("CameraPreview");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        try {
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG,"Camera access exception");
        }
    }
}