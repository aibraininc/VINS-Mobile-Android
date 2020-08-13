package com.thkoeln.jmoeller.vins_mobile_androidport;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.aibrain.tyche.bluetoothle.TycheControlHelper;
import com.aibrain.tyche.bluetoothle.constants.Direction;
import com.aibrain.tyche.bluetoothle.constants.Mode;
import com.aibrain.tyche.bluetoothle.drive.Drive;
import com.aibrain.tyche.bluetoothle.drive.MoveDrive;
import com.aibrain.tyche.bluetoothle.drive.RotateDrive;
import com.aibrain.tyche.bluetoothle.drive.TimeDrive;
import com.aibrain.tyche.bluetoothle.exception.InvalidNumberException;
import com.aibrain.tyche.bluetoothle.exception.NotConnectedException;
import com.aibrain.tyche.bluetoothle.exception.NotEnoughBatteryException;
import com.aibrain.tyche.bluetoothle.exception.NotSupportSensorException;
import com.aibrain.tyche.bluetoothle.executor.Executor;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link MainActivity} only activity
 * manages camera input, texture output
 * textViews and buttons
 */
public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, View.OnClickListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE = 1234;

    // needed for permission request callback
    private static final int PERMISSIONS_REQUEST_CODE = 12345;

    // camera2 API Camera
    private CameraDevice camera;
    // Back cam, 1 would be the front facing one
    private String cameraID = "1";

    // Texture View to display the camera picture, or the vins output
    private TextureView textureView;
    private Size previewSize;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader imageReader;

    // Handler for Camera Thread
    private Handler handler;
    private HandlerThread threadHandler;

    // Cam parameters
    private final int imageWidth = 640;
    private final int imageHeight = 480;
    
    private final int framesPerSecond = 30;
    
    /** Adjustment to auto-exposure (AE) target image brightness in EV */
    private final int aeCompensation = 0;
//    private final int aeCompensation = -1;
    
    private Surface surface;
    
    // JNI Object
    private VinsJNI vinsJNI;

    // TextViews
    private TextView tvX;
    private TextView tvY;
    private TextView tvZ;
    private TextView tvTotal;
    private TextView tvLoop;
    private TextView tvFeature;
    private TextView tvBuf;

    private Button slamButton;
    private boolean isSLAM;
    // ImageView for initialization instructions
    private ImageView ivInit;

    // directory path for BRIEF config files
    private final String directoryPathBriefFiles = "/storage/emulated/0/VINS";

    // Distance of virtual Cam from Center
    // could be easily manipulated in UI later
    private float virtualCamDistance = 2;
    private final float minVirtualCamDistance = 2;
    private final float maxVirtualCamDistance = 40;

    // Tyche control instance
    private TycheControlHelper tycheControlHelper = new TycheControlHelper(this, new TycheControlHelper.OnChangeStatusListener() {
        @Override
        public void onConnectionStatusChange(boolean isConnect) {

        }

        @Override
        public void onStatusChange(StatusData status) {

        }

        @Override
        public void onObstacleDetected(int distance) {

        }

        @Override
        public void onNotEnoughBattery() {

        }
    });

    /**
     * Gets Called after App start
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // first make sure the necessary permissions are given
        checkPermissionsIfNeccessary();
        
        if(!checkBriefFileExistance()) {
            Log.e(TAG, "Brief files not found here: " + directoryPathBriefFiles);
            finish();
        }
        
        initLooper();
        initVINS();
        initViews();
        isSLAM = true;

        // tyche open
        tycheControlHelper.open();
        // enable obstacle detecting mode. Default is moving backward when Tyche meets obstacles.
        tycheControlHelper.enableObstacleDetector(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // tyche close
        tycheControlHelper.close(true);
    }

    /**
     * check if necessary files brief_k10L6.bin and brief_pattern.yml exist in the directoryPathBriefFiles
     * @return true if files are existent and read and writable
     */
    private boolean checkBriefFileExistance() {
        File directoryFile = new File(directoryPathBriefFiles);
        if(!directoryFile.exists())
            return false;

        String filepathVoc = directoryFile + File.separator + "brief_k10L6.bin";
        File vocFile = new File(filepathVoc);
        Log.d(TAG, "Filepath: " + filepathVoc + 
                   " File Exists: " + vocFile.exists() + 
                   " File Write: " + vocFile.canWrite() +  
                   " File Read: " + vocFile.canRead());
        if(!vocFile.exists() || !vocFile.canRead() || !vocFile.canWrite())
            return false;
        
        String filepathPattern = directoryFile + File.separator + "brief_pattern.yml";
        File patternFile = new File(filepathPattern);
        Log.d(TAG, "Filepath: " + filepathPattern + 
                   " File Exists: " + patternFile.exists() + 
                   " File Write: " + patternFile.canWrite() +  
                   " File Read: " + patternFile.canRead());
        if(!patternFile.exists() || !patternFile.canRead() || !patternFile.canWrite())
            return false;
        
        return true;
    }

    /**
     * Starting separate thread to handle camera input
     */
    private void initLooper() {
        threadHandler = new HandlerThread("Camera2Thread");
        threadHandler.start();
        handler = new Handler(threadHandler.getLooper());
    }

    /**
     * initializes an new VinsJNI Object
     */
    private void initVINS() {
        vinsJNI = new VinsJNI();
        vinsJNI.init();
    }

    /**
     * Finding all UI Elements,
     * Setting TextureView Listener to this object.
     */
    private void initViews() {

        slamButton = (Button) findViewById(R.id.button);
        slamButton.setOnClickListener(this);
//
        Button speechButton = (Button)findViewById(R.id.speech_button);
        speechButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                startActivityForResult(intent, REQUEST_CODE);
            }
        });

        tvX = (TextView) findViewById(R.id.x_Label);
        tvY = (TextView) findViewById(R.id.y_Label);
        tvZ = (TextView) findViewById(R.id.z_Label);
        tvTotal = (TextView) findViewById(R.id.total_odom_Label);
        tvLoop = (TextView) findViewById(R.id.loop_Label);
        tvFeature = (TextView) findViewById(R.id.feature_Label);
        tvBuf = (TextView) findViewById(R.id.buf_Label);
        
        ivInit = (ImageView) findViewById(R.id.init_image_view); 
        ivInit.setVisibility(View.VISIBLE);

        textureView = (TextureView) findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(this);

        // Define the Switch listeners
        Switch arSwitch = (Switch) findViewById(R.id.ar_switch);
        arSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG,"arSwitch State = " + isChecked);
                VinsJNI.onARSwitch(isChecked);
            }
        });
        
        Switch loopSwitch = (Switch) findViewById(R.id.loop_switch);
        loopSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG,"loopSwitch State = " + isChecked);
                VinsJNI.onLoopSwitch(isChecked);
            }
        });

        SeekBar zoomSlider = (SeekBar) findViewById(R.id.zoom_slider);
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                virtualCamDistance = minVirtualCamDistance + ((float)progress / 100) * (maxVirtualCamDistance - minVirtualCamDistance);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {  }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {  }
        });
    }
    
    /**
     * SurfaceTextureListener interface function 
     * used to set configuration of the camera and start it
     */
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width,
                                          int height) {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            // check permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                checkPermissionsIfNeccessary();
                return;
            }
            
            // start up Camera (not the recording)
            cameraManager.openCamera(cameraID, cameraDeviceStateCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            try {
                camera = cameraDevice;
                startCameraView(camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {}

        @Override
        public void onError(CameraDevice camera, int error) {}
    };

    /**
     * starts CameraView
     */
    private void startCameraView(CameraDevice camera) throws CameraAccessException {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        
        // to set CameraView size
        texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
        Log.d(TAG, "texture width: " + textureView.getWidth() + " height: " + textureView.getHeight());
        surface = new Surface(texture);
                
        try {
            // to set request for CameraView
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // to set the format of captured images and the maximum number of images that can be accessed in mImageReader
        imageReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);

        imageReader.setOnImageAvailableListener(onImageAvailableListener, handler);


        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraID);
        // get the StepSize of the auto exposure compensation
        Rational aeCompStepSize = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if(aeCompStepSize == null) {
            Log.e(TAG, "Camera doesn't support setting Auto-Exposure Compensation");
            finish();
        }
        Log.d(TAG, "AE Compensation StepSize: " + aeCompStepSize);
        
        int aeCompensationInSteps = aeCompensation * aeCompStepSize.getDenominator() / aeCompStepSize.getNumerator();
        Log.d(TAG, "aeCompensationInSteps: " + aeCompensationInSteps );
        previewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, aeCompensationInSteps);
        
        // set the camera output frequency to 30Hz
        previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(framesPerSecond, framesPerSecond));
        
        // the first added target surface is for CameraView display
        // the second added target mImageReader.getSurface() 
        // is for ImageReader Callback where it can be access EACH frame
        //mPreviewBuilder.addTarget(surface);
        previewBuilder.addTarget(imageReader.getSurface());

        //output Surface
        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(imageReader.getSurface());
        
        
        camera.createCaptureSession(outputSurfaces, sessionStateCallback, handler);
    }

    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                updateCameraView(session);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    /**
     * Starts the RepeatingRequest for 
     */
    private void updateCameraView(CameraCaptureSession session)
            throws CameraAccessException {
//        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

        session.setRepeatingRequest(previewBuilder.build(), null, handler);
    }
    
    /**
     *  At last the actual function with access to the image
     */
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        /*
         *  The following method will be called every time an image is ready
         *  be sure to use method acquireNextImage() and then close(), otherwise, the display may STOP
         */
        @Override
        public void onImageAvailable(ImageReader reader) {
            // get the newest frame
            Image image = reader.acquireNextImage();
            
            if (image == null) {
                return;
            }
                    
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                Log.e(TAG, "camera image is in wrong format");
            }

            //RGBA output
            Image.Plane Y_plane = image.getPlanes()[0];
            int Y_rowStride = Y_plane.getRowStride();
            Image.Plane U_plane = image.getPlanes()[1];
            int UV_rowStride = U_plane.getRowStride();  
            Image.Plane V_plane = image.getPlanes()[2];
            
            // pass the current device's screen orientation to the c++ part
            int currentRotation = getWindowManager().getDefaultDisplay().getRotation();
            boolean isScreenRotated = currentRotation != Surface.ROTATION_90;
            
            // pass image to c++ part
            VinsJNI.onImageAvailable(image.getWidth(), image.getHeight(), 
                                     Y_rowStride, Y_plane.getBuffer(), 
                                     UV_rowStride, U_plane.getBuffer(), V_plane.getBuffer(), 
                                     surface, image.getTimestamp(), isScreenRotated,
                                     virtualCamDistance);

            // run the updateViewInfo function on the UI Thread so it has permission to modify it
            runOnUiThread(new Runnable() {
                public void run() {
                    VinsJNI.updateViewInfo(tvX, tvY, tvZ, tvTotal, tvLoop, tvFeature, tvBuf, ivInit);

//                    String[] position = new String[3];
//                    if(tvX.getText().toString().split("X: ").length == 2){
//                        position[0] = tvX.getText().toString().split("X: ")[1];
//                    }
//                    if(tvY.getText().toString().split("Y: ").length == 2){
//                        position[1] = tvY.getText().toString().split("Y: ")[1];
//                    }
//                    if(tvZ.getText().toString().split("Z: ").length == 2){
//                        position[2] = tvZ.getText().toString().split("Z: ")[1];
//                    }


                    float [] position = VinsJNI.getPosition();
                    Log.e(TAG, "runOnUiThread X: " + position[0]);
                    Log.e(TAG, "runOnUiThread Y: " + position[1]);
                    Log.e(TAG, "runOnUiThread Z: " + position[2]);



                }
            });

            image.close();
        }
    };

    /**
     * shutting down onPause
     */
    protected void onPause() {
        Log.e("onPause","onPause");
//        if (null != camera) {
//            camera.close();
//            camera = null;
//        }
//        if (null != imageReader) {
//            imageReader.close();
//            imageReader = null;
//        }
//
//        VinsJNI.onPause();
        super.onPause();
    }

    /**
     * @return true if permissions where given
     */
    private boolean checkPermissionsIfNeccessary() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(this.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                List<String> permissionsNotGrantedYet = new ArrayList<>(info.requestedPermissions.length);
                for (String p : info.requestedPermissions) {
                    if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNotGrantedYet.add(p);
                    }
                }
                if(permissionsNotGrantedYet.size() > 0){
                    ActivityCompat.requestPermissions(this, permissionsNotGrantedYet.toArray(new String[permissionsNotGrantedYet.size()]),
                                                      PERMISSIONS_REQUEST_CODE);
                    return false;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {

        if(requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean hasAllPermissions = true;
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length == 0)
                hasAllPermissions = false;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    hasAllPermissions = false;
            }

            if(!hasAllPermissions){
                finish();
            }
        }
    }

    @Override
    public void onClick(View view) {
        if(isSLAM) {
            // stop slam
            vinsJNI.onStopSLAM();
            isSLAM = false;
        }
        else {
            // start slam
            vinsJNI.onRestartSLAM();
            isSLAM = true;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches_text;
            matches_text = data
                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            Log.v("onActivityResult", matches_text.get(0));
            Toast.makeText(getApplicationContext(), matches_text.get(0), Toast.LENGTH_LONG).show();

            if(matches_text.get(0).contains("시작")){
                vinsJNI.onRestartSLAM();
                isSLAM = true;
            }
            else if(matches_text.get(0).contains("그만")){
                vinsJNI.onStopSLAM();
                isSLAM = false;
            }

        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    private void tycheExample()
    {
        // tyche move forward with velocity 50 for 1000 ms
        TimeDrive d1 = new TimeDrive();
        d1.setDirection(Direction.FORWARD);
        d1.setDuration(1000);
        d1.setVelocity(50);
        d1.setRestTime(500);   // rest for 500ms after finishing moving

        // tyche move backward with velocity 40 as 20centimeters
        MoveDrive d2 = new MoveDrive();
        d2.setVelocity(40);
        d2.setDistance(-20);

        // tyche turns left as 90 degrees
        RotateDrive d3 = new RotateDrive(Mode.ENCODER);
        d3.setAngle(-90);
        d3.setRestTime(500);

        // tyche turn right as 180 degrees
        RotateDrive d4 = new RotateDrive(Mode.ENCODER);
        d4.setAngle(180);
        d4.setRestTime(500);   // rest for 500ms after finishing moving

        // tyche turn left with velocity 30 for 1 seconds
        TimeDrive d5 = new TimeDrive();
        d5.setDirection(Direction.LEFT);
        d5.setVelocity(30);
        d5.setDuration(1000);


        ArrayList<Drive> path = new ArrayList<>();
        path.add(d1);
        path.add(d2);
        path.add(d3);
        path.add(d4);
        path.add(d5);

        try {
            tycheControlHelper.drive(path);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /*
     * @centimeter backward if centimeter < 0
     * @velocity exception if velocity < 0
     */
    private void tycheMove(int centimeter, int velocity)
    {
        tycheMove(centimeter, velocity, null);
    }

    /*
     * @centimeter backward if centimeter < 0
     * @velocity exception if velocity < 0
     * @listener called if moving finished
     */
    private void tycheMove(int centimeter, int velocity, Executor.OnFinishListener listener)
    {
        MoveDrive d = new MoveDrive();
        d.setDistance(centimeter);
        d.setVelocity(velocity);

        try {
            tycheControlHelper.drive(d, listener);

        } catch (InvalidNumberException | NotEnoughBatteryException | NotSupportSensorException | NotConnectedException e) {
            e.printStackTrace();
        }
    }

    /*
     * @angle left if angle < 0, right if angle>0
     */
    private void tycheTurn(int angle)
    {
        tycheTurn(angle, null);
    }

    /*
     * @angle left if angle < 0, right if angle>0
     * @listener called if turning finished
     */
    private void tycheTurn(int angle, Executor.OnFinishListener listener)
    {
        RotateDrive d = new RotateDrive(Mode.ENCODER);
        d.setAngle(angle);
        try {
            tycheControlHelper.drive(d, listener);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /*
     * @velocity exception if velocity < 0
     * @duration turn left for duration milliseconds
     */
    private void tycheTurnLeft(int velocity, int duration)
    {
        tycheTurnLeft(velocity, duration, null);
    }

    /*
     * @velocity exception if velocity < 0
     * @duration turn left for duration milliseconds
     * @listener called if turning finished
     */
    private void tycheTurnLeft(int velocity, int duration, Executor.OnFinishListener listener)
    {
        TimeDrive d = new TimeDrive();
        d.setDirection(Direction.LEFT);
        d.setVelocity(velocity);
        d.setDuration(duration);
        try {
            tycheControlHelper.drive(d, listener);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /*
     * @velocity exception if velocity < 0
     * @duration turn right for duration milliseconds
     */
    private void tycheTurnRight(int velocity, int duration)
    {
        tycheTurnRight(velocity, duration, null);
    }

    /*
     * @velocity exception if velocity < 0
     * @duration turn right for duration milliseconds
     * @listener called if turning finished
     */
    private void tycheTurnRight(int velocity, int duration, Executor.OnFinishListener listener)
    {
        TimeDrive d = new TimeDrive();
        d.setDirection(Direction.RIGHT);
        d.setVelocity(velocity);
        d.setDuration(duration);
        try {
            tycheControlHelper.drive(d, listener);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /*
     * @leftVelocity left motor control, -100 <= leftVelocity <= 100, reverse if leftVelocity < 0
     * @rightVelocity right motor control, -100 <= rightVelocity <= 100, reverse if rightVelocity < 0
     */
    private void tycheMoveDirectly(int leftVelocity, int rightVelocity)
    {
        try {
            tycheControlHelper.operate(leftVelocity, rightVelocity);
        } catch (NotConnectedException | NotEnoughBatteryException e) {
            e.printStackTrace();
        }
    }
}
