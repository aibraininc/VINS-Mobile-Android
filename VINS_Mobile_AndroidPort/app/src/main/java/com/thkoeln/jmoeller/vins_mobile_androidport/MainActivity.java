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
import android.os.Message;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
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
import com.aibrain.tyche.bluetoothle.drive.ControlTimeDrive;
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
import java.util.Random;

/**
 * {@link MainActivity} only activity
 * manages camera input, texture output
 * textViews and buttons
 */
public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener, View.OnClickListener, TextToSpeech.OnInitListener {

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
    private boolean vinsDisabled;

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

    public TextToSpeech tts;
    public static float[] robotPosition;
    NavigationHelper navigationHelper;

    // handler for movement
    private final Handler movementHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
         movement(msg.what, msg.arg1);
        }
    } ;

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
//        tycheControlHelper.open();
        tts = new TextToSpeech(MainActivity.this, this);

        // enable obstacle detecting mode. Default is moving backward when Tyche meets obstacles.
        tycheControlHelper.enableObstacleDetector(true);

        // 네비게이션 헬퍼 객체 생성
        navigationHelper = new NavigationHelper();
    }

    @Override
    protected void onDestroy() {
        Log.e("onDestroy","onDestroy");
        super.onDestroy();
        deleteVINS();
        if (null != camera) {
            camera.close();
            camera = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }


        VinsJNI.onPause();
        vinsJNI.release();



        // tyche close
//        tycheControlHelper.close(true);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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
        vinsDisabled = false;
        vinsJNI = new VinsJNI();
        vinsJNI.init();
    }

    private void deleteVINS() {
        vinsDisabled = true;
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
                if(!vinsDisabled)
                    VinsJNI.onARSwitch(isChecked);
            }
        });
        
        Switch loopSwitch = (Switch) findViewById(R.id.loop_switch);
        loopSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG,"loopSwitch State = " + isChecked);
                if(!vinsDisabled)
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
            if(!vinsDisabled)
                VinsJNI.onImageAvailable(image.getWidth(), image.getHeight(),
                                     Y_rowStride, Y_plane.getBuffer(),
                                     UV_rowStride, U_plane.getBuffer(), V_plane.getBuffer(),
                                     surface, image.getTimestamp(), isScreenRotated,
                                     virtualCamDistance);

            // run the updateViewInfo function on the UI Thread so it has permission to modify it
            runOnUiThread(new Runnable() {
                public void run() {
                    if(!vinsDisabled)
                        VinsJNI.updateViewInfo(tvX, tvY, tvZ, tvTotal, tvLoop, tvFeature, tvBuf, ivInit);
                    // Get position from Vins and store the position to global variable "robotPosition"
                    if(!vinsDisabled)
                        robotPosition = VinsJNI.getPosition();
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
            if(!vinsDisabled)
                vinsJNI.onStopSLAM();
            isSLAM = false;
        }
        else {
            // start slam
            if(!vinsDisabled)
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

            // 음성인식한 텍스트를 출력한다.
            Toast.makeText(getApplicationContext(), matches_text.get(0), Toast.LENGTH_LONG).show();

//            if(matches_text.get(0).contains("시작")){
//                vinsJNI.onRestartSLAM();
//                isSLAM = true;
//            }

            // 타이키 slam을 멈춘다.
            if(matches_text.get(0).contains("그만")){

            }

            // 앞으로 이동한다.
            else if(matches_text.get(0).contains("앞으로")){
                this.tycheMove(50,30);
            }

            // 뒤로 이동한다.
            else if(matches_text.get(0).contains("뒤로")){
                this.tycheMove(-50,30);
            }

            // 왼쪽으로 이동한다.
            else if(matches_text.get(0).contains("왼쪽")){
                this.tycheTurnLeft(60,4000,1000);
            }

            // 오른쪽으로 이동한다.
            else if(matches_text.get(0).contains("오른쪽")){
                this.tycheTurnRight(60,4000,1000);
            }

            // 회전한다.
            else if(matches_text.get(0).contains("회전")){
                this.tycheTurnLeft(60,4000,300);
                this.tycheTurnRight(60,8000,300);
                this.tycheTurnLeft(60,4000,300);
            }


            // 움직이던 타이키를 멈춘다.
            else if(matches_text.get(0).contains("멈춰")){
                this.tycheStop();
            }

            // 탐사 동작을 수행한다. 장애물을 보면 90도 회전한다.
            else if(matches_text.get(0).contains("탐사")){
                this.tycheMoveAround1();
            }

            // 장소를 저장한다.
            else if(matches_text.get(0).contains("여기는")){
                navigationHelper.addPlace(matches_text.get(0), robotPosition[0],robotPosition[1],robotPosition[2]);
            }

            // 0번 목적지까지의 각도를 계산한다. 각도가 잘 계산되는지 확인하기 위해 사용한다.
            else if(matches_text.get(0).contains("각도 계산")) {
                // goal position
                float angle_1 = navigationHelper.calculateAngle(navigationHelper.placeInfos.get(0).x,navigationHelper.placeInfos.get(0).y);

                // robot position
                float angle_2 = navigationHelper.calculateAngle(robotPosition[0],robotPosition[1]);


                float angle_for_rotation = navigationHelper.calculateAngle(navigationHelper.placeInfos.get(0).x - robotPosition[0], navigationHelper.placeInfos.get(0).y- robotPosition[1]);
                float angle_yaw = (float)(180* robotPosition[3] / 3.141592);
                Toast.makeText(getApplicationContext(), angle_1+","+angle_2+","+angle_yaw + ", "+ (angle_for_rotation - angle_yaw), Toast.LENGTH_LONG).show();
            }

            // 목적이로 이동한다.
            // ex) 미팅룸으로 "이동"
            else if(matches_text.get(0).contains("이동")) {
                // search text
                int idx= navigationHelper.searchPlaceByName(matches_text.get(0));
                this.movement(10, idx);
            }

            // 어디야라고 말하면, 제일 가까운 위치를 말해준다.
            else if(matches_text.get(0).contains("어디야")){
                String test = navigationHelper.searchPlace(robotPosition[0], robotPosition[1], robotPosition[2]).name;
                Toast.makeText(getApplicationContext(), test, Toast.LENGTH_LONG).show();

                // 음성출력한다.
                this.speakJust(test);
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void movement(int num, int thePlaceIdx) {
        PlaceInfo thePlace = navigationHelper.placeInfos.get(thePlaceIdx);
        float angle_1 = navigationHelper.calculateAngle(thePlace.x,thePlace.y);
        float angle_for_rotation = navigationHelper.calculateAngle(thePlace.x - robotPosition[0], thePlace.y- robotPosition[1]);
        float angle_yaw = (float)(180* robotPosition[3] / 3.141592);


        float distance = thePlace.calculateDistance2D(robotPosition[0],robotPosition[1]);
        float rotation = angle_for_rotation - angle_yaw;

        if(distance < 0.5) // 50cm 이내 들어오면 이동을 멈춘다.
        {
            return;
        }

        if(Math.abs(rotation) < 30) // 각도가 30도 이내일 경우, 전진한다.
        {
            this.tycheMove(30,30);
        }
        if(rotation>0) // 각도가 양수일 경우, 왼쪽으로 회전한다.
        {
            this.tycheTurnLeft(45,500,500);
        }
        else // 각도가 음수일 경우, 오른쪽으로 회전한다.
        {
            this.tycheTurnRight(45,500,500);
        }
        num--;

        // num이 0이되면 움직이지 않는다.
        if(num > 0) {
            Message message = handler.obtainMessage() ;
            message.what = num;
            message.arg1 = thePlaceIdx;
            movementHandler.sendMessageDelayed(message,3000) ;
        }
    }



    public void tycheStop() {
        tycheControlHelper.skipAllDrives();
    }


    public void tycheInitMotion() {
        // init motion
        TimeDrive left = new TimeDrive();
        left.setDirection(Direction.LEFT);
        left.setVelocity(35);
        left.setDuration(2000);
        left.setRestTime(500);

        TimeDrive right = new TimeDrive();
        right.setDirection(Direction.RIGHT);
        right.setVelocity(35);
        right.setDuration(2000);
        right.setRestTime(500);

        TimeDrive forward = new TimeDrive();
        forward.setDirection(Direction.FORWARD);
        forward.setVelocity(35);
        forward.setDuration(300);
        forward.setRestTime(500);

        TimeDrive backward = new TimeDrive();
        backward.setDirection(Direction.BACKWARD);
        backward.setVelocity(35);
        backward.setDuration(300);
        backward.setRestTime(500);


        ArrayList<Drive> path = new ArrayList();
        Random random = new Random();
        path.add(backward);

        for(int i=0; i<1; i++) {
            path.add(left);
            path.add(right);
            path.add(right);
            path.add(left);
            path.add(left);
            path.add(right);
            int rand = random.nextInt(2);
            if(rand==0) path.add(forward);
            else path.add(backward);
        }

        try {
            tycheControlHelper.drive(path);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    public void tycheMoveAround() {
//        tycheInitMotion();

        final int DURATION = 2000;
        final int TURN_SPEED = 30;
        final int SPEED = 20;
        final int ONE_SPEED = 40;

        TimeDrive forward = new TimeDrive();
        forward.setDirection(Direction.FORWARD);
        forward.setDuration(DURATION);
        forward.setVelocity(SPEED);
        forward.setRestTime(500);

        TimeDrive left = new TimeDrive();
        left.setDirection(Direction.LEFT);
        left.setVelocity(TURN_SPEED);
        left.setDuration(DURATION);
        left.setRestTime(500);

        TimeDrive right = new TimeDrive();
        right.setDirection(Direction.RIGHT);
        right.setVelocity(TURN_SPEED);
        right.setDuration(DURATION);
        right.setRestTime(500);

        TimeDrive backward = new TimeDrive();
        backward.setDirection(Direction.BACKWARD);
        backward.setVelocity(SPEED);
        backward.setDuration(DURATION);
        backward.setRestTime(500);

        ControlTimeDrive leftForward = new ControlTimeDrive();
        leftForward.setLeftVelocity(ONE_SPEED);
        leftForward.setDuration(DURATION);
        leftForward.setRestTime(500);

        ControlTimeDrive rightForward = new ControlTimeDrive();
        rightForward.setRightVelocity(ONE_SPEED);
        rightForward.setDuration(DURATION);
        rightForward.setRestTime(500);

        ControlTimeDrive leftBackward = new ControlTimeDrive();
        leftBackward.setLeftVelocity(-ONE_SPEED);
        leftBackward.setDuration(DURATION);
        leftBackward.setRestTime(500);

        ControlTimeDrive rightBackward = new ControlTimeDrive();
        rightBackward.setRightVelocity(-ONE_SPEED);
        rightBackward.setDuration(DURATION);
        rightBackward.setRestTime(500);


        ArrayList<Drive> path = new ArrayList();


        Random random = new Random();
        // random motion
        for(int i=0; i<20; i++) {
            int rand = random.nextInt(8);
            switch (rand) {
                case 0: path.add(forward);
                    break;
                case 1: path.add(left);
                    break;
                case 2: path.add(right);
                    break;
                case 3: path.add(backward);
                    break;
                case 4: path.add(leftForward);
                    break;
                case 5: path.add(rightForward);
                    break;
                case 6: path.add(leftBackward);
                    break;
                case 7: path.add(rightBackward);
                    break;
            }
        }
        try {
            tycheControlHelper.drive(path);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    public void tycheMoveAround1() {
//        tycheInitMotion();

        TimeDrive backward = new TimeDrive();
        backward.setDirection(Direction.BACKWARD);
        backward.setVelocity(25);
        backward.setDuration(500);

        RotateDrive turnLeft = new RotateDrive(Mode.ENCODER);
        turnLeft.setAngle(90);

        final TimeDrive forward = new TimeDrive();
        forward.setDirection(Direction.FORWARD);
        forward.setVelocity(25);
        forward.setDuration(20000);

        ArrayList<Drive> path = new ArrayList();
        path.add(backward);
        path.add(turnLeft);

        tycheControlHelper.enableObstacleDetector(true);
        tycheControlHelper.setObstacleDetectedDrives(path, new Executor.OnFinishListener() {
            @Override
            public void onFinish(boolean isCanceled) {
                try {
                    tycheControlHelper.drive(forward);
                } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            tycheControlHelper.drive(forward);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /////////////////// Tyche control //////////////////////////

    private void tycheExample() {
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
        d5.setVelocity(40);
        d5.setDuration(1000);

        RotateDrive left90 = new RotateDrive(Mode.ENCODER);
        left90.setAngle(-90);
        left90.setRestTime(2000);   // millisecond

        RotateDrive rigth180 = new RotateDrive(Mode.ENCODER);
        rigth180.setAngle(180);
        rigth180.setRestTime(2000);   // millisecond

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

    public void speakJust(String text) {
        if(!tts.isSpeaking()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onInit(int i) {

    }

    /*
     * @centimeter backward if centimeter < 0
     * @velocity exception if velocity < 0
     */
    private void tycheMove(int centimeter, int velocity) {
        tycheMove(centimeter, velocity, null);
    }

    /*
     * @centimeter backward if centimeter < 0
     * @velocity exception if velocity < 0
     * @listener called if moving finished
     */
    private void tycheMove(int centimeter, int velocity, Executor.OnFinishListener listener) {
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
    private void tycheTurn(int angle) {
        tycheTurn(angle, null);
    }

    /*
     * @angle left if angle < 0, right if angle>0
     * @listener called if turning finished
     */
    private void tycheTurn(int angle, Executor.OnFinishListener listener) {
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
    private void tycheTurnLeft(int velocity, int duration, int restDuration)
    {
        tycheTurnLeft(velocity, duration, restDuration, null);
    }

    /*
     * @velocity
     * @duration turn left for duration milliseconds
     * @listener called if turning finished
     */
    private void tycheTurnLeft(int velocity, int duration, int restDuration, Executor.OnFinishListener listener)
    {
        TimeDrive d = new TimeDrive();
        d.setDirection(Direction.LEFT);
        d.setVelocity(velocity);
        d.setDuration(duration);
        d.setRestTime((restDuration));
        try {
            tycheControlHelper.drive(d, listener);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /*
     * @velocity
     * @duration turn right for duration milliseconds
     */
    private void tycheTurnRight(int velocity, int duration, int restDuration)
    {
        tycheTurnRight(velocity, duration, restDuration, null);
    }

    /*
     * @velocity
     * @duration turn right for duration milliseconds
     * @listener called if turning finished
     */
    private void tycheTurnRight(int velocity, int duration, int restDuration, Executor.OnFinishListener listener)
    {
        TimeDrive d = new TimeDrive();
        d.setDirection(Direction.RIGHT);
        d.setVelocity(velocity);
        d.setDuration(duration);
        d.setRestTime(restDuration);
        try {
            tycheControlHelper.drive(d, listener);
        } catch (NotConnectedException | NotEnoughBatteryException | InvalidNumberException | NotSupportSensorException e) {
            e.printStackTrace();
        }
    }

    /*
     * @leftVelocity    -100 <= leftVelocity <= 100
     * @rightVelocity   -100 <= rightVelocity <= 100
     * @duration turn right for duration milliseconds
     */
    private void tycheControl(int leftVelocity, int rightVelocity, int duration) {
        tycheControl(leftVelocity, rightVelocity, duration, null);
    }

    /*
     * @leftVelocity    -100 <= leftVelocity <= 100
     * @rightVelocity   -100 <= rightVelocity <= 100
     * @duration turn right for duration milliseconds
     * @listener called if turning finished
     */
    private void tycheControl(int leftVelocity, int rightVelocity, int duration, Executor.OnFinishListener listener) {
        ControlTimeDrive d = new ControlTimeDrive();
        d.setLeftVelocity(leftVelocity);
        d.setRightVelocity(rightVelocity);
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

    /////////////////////////////////////////////
}
