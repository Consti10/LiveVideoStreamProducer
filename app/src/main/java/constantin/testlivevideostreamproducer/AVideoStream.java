package constantin.testlivevideostreamproducer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;

//Note: Pausing /resuming is not supported.
//Once started,everything runs until onDestroy() is called

public class AVideoStream extends AppCompatActivity{
    private static final String TAG="AVideoStream";

    private TextureView previewTextureView;
    private SurfaceTexture previewTexture;
    private Surface previewSurface;

    private Surface encoderInputSurface;

    private static final int W=1920;
    private static final int H=1080;
    private static final int MDEIACODEC_ENCODER_TARGET_FPS=60;

    private CameraDevice cameraDevice;
    private MediaCodec codec;

    //The MediaCodec output callbacks runs on this Handler
    //private Handler mBackgroundHandler;
    //private HandlerThread mBackgroundThread;

    private UDPSender mUDPSender;
    private Thread drainEncoderThread;


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avideo_stream);
        //mBackgroundThread = new HandlerThread("Encoder output");
        //mBackgroundThread.start();
        //mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        previewTextureView =findViewById(R.id.mTextureView);
        //Initialize UDP sender
        mUDPSender=new UDPSender(this);

        //This thread will be started once the MediaCodec encoder has been created.
        //When decoding in LiveVideo10ms, testing indicates lower latency when constantly pulling on the output with one Thread.
        //Also, we have to use another Thread for networking anyways
        drainEncoderThread=new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()){
                    final MediaCodec.BufferInfo bufferInfo=new MediaCodec.BufferInfo();
                    if(codec!=null){
                        final int outputBufferId = codec.dequeueOutputBuffer(bufferInfo,0);
                        if (outputBufferId >= 0) {
                            ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
                            MediaFormat bufferFormat = codec.getOutputFormat(outputBufferId); // option A
                            // bufferFormat is identical to outputFormat
                            //Log.d(TAG,"MediaCodec2::onOutputBufferAvailable");
                            mUDPSender.sendOnCurrentThread(outputBuffer);

                            // outputBuffer is ready to be processed or rendered.
                            codec.releaseOutputBuffer(outputBufferId,false);

                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // Subsequent data will conform to new format.
                            // Can ignore if using getOutputFormat(outputBufferId)
                            //codec.getOu
                            //outputFormat = codec.getOutputFormat(); // option B
                            //MediaFormat bufferFormat = codec.getOutputFormat();
                            //Log.d(TAG,"INFO_OUTPUT_FORMAT_CHANGED "+bufferFormat.toString());
                        }
                    }
                }
            }
        });
        //Create Decoder. We don't have to wait for anything here
        try {
            codec= MediaCodec.createEncoderByType("video/avc");
            MediaFormat format = MediaFormat.createVideoFormat("video/avc",W,H);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,10*1024*1024); //X MBit/s
            format.setInteger(MediaFormat.KEY_FRAME_RATE,MDEIACODEC_ENCODER_TARGET_FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,10);
            //format.setInteger(MediaFormat.KEY_LEVEL,MediaCodecInfo.CodecProfileLevel.AVCLevel32);
            //format.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderInputSurface = codec.createInputSurface();
            //codec.setCallback(mediaCodecCallback);
            codec.start();
            drainEncoderThread.start();
        } catch (Exception e) {
            e.printStackTrace();
            //There is no point of continuing when unable to create Encoder
            notifyUserAndFinishActivity( "Error MediaCodec.createEncoderByType");
        }
        //Open main camera. We don't need to wait for the surface texture, since we set the callback once camera was opened
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.d(TAG,"Opening camera");
        try {
            //Just assume there is a front camera
            final String cameraId = manager.getCameraIdList()[0];
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            Log.d(TAG,"Available fps range(s):"+Arrays.toString(fpsRanges));

            //StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            //imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
            //No point in continuing if we cannot open camera
            notifyUserAndFinishActivity("Cannot open camera");
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "CameraDevice onOpened");
            cameraDevice = camera;
            //Once the camera was opened, we add the listener for the TextureView.
            //Which will then call the startStream()
            previewTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };
    private final TextureView.SurfaceTextureListener surfaceTextureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            previewTexture=surface;
            startPreviewAndEncoding();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
    };

    private final MediaCodec.Callback mediaCodecCallback=new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            Log.d(TAG,"MediaCodec::onInputBufferAvailable");
            //This should never happen
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            //Log.d(TAG,"MediaCodec::onOutputBufferAvailable");
            final ByteBuffer bb= codec.getOutputBuffer(index);
            mUDPSender.sendAsync(bb);
            //DO something with the data
            codec.releaseOutputBuffer(index,false);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            Log.d(TAG,"MediaCodec::onError");
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
            final ByteBuffer csd0=format.getByteBuffer("csd-0");
            final ByteBuffer csd1=format.getByteBuffer("csd-1");
            mUDPSender.sendAsync(csd0);
            mUDPSender.sendAsync(csd1);
        }
    };


    private void startPreviewAndEncoding(){
        Log.d(TAG,"startPreviewAndEncoding(). Camera device id"+cameraDevice.getId()+"Surface Texture"+previewTexture.toString());
        //All Preconditions are fulfilled:
        //The Camera has been opened
        //The preview Surface texture has been created
        //The encoder surface has been created
        previewTexture.setDefaultBufferSize(W,H);
        previewSurface = new Surface(previewTexture);

        try {
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(encoderInputSurface);

            //Log.d("FPS", "SYNC_MAX_LATENCY_PER_FRAME_CONTROL: " + Arrays.toString(fpsRanges));
            Range<Integer> fpsRange=new Range<>(60,60);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fpsRange);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface,encoderInputSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // When the session is ready, we start displaying the preview.
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null,null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(AVideoStream.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if(cameraDevice!=null){
            cameraDevice.close();
            cameraDevice=null;
        }
        if(drainEncoderThread!=null){
            drainEncoderThread.interrupt();
            try {
                drainEncoderThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(codec!=null){
            codec.stop();
            codec.release();
            codec=null;
        }
        if(previewSurface!=null){
            previewSurface.release();
            previewSurface=null;
        }
        if(encoderInputSurface!=null){
            encoderInputSurface.release();
            encoderInputSurface=null;
        }
    }

    //has to be called on the ui thread
    private void notifyUserAndFinishActivity(final String message){
        Log.d(TAG,message);
        Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();
        finish();
    }

}
