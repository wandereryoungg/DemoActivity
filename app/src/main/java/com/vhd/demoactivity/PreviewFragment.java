package com.vhd.demoactivity;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class PreviewFragment extends Fragment {

    private String TAG = getClass().getSimpleName();

    private TextureView previewContent;
    private Matrix matrix;
    private Size mPreviewSize;
    private int mWidth = 1920;
    private int mHeight = 1080;
    private int mCameraId = 0;
    private boolean mFlashSupported;

    public HandlerThread mBackgroundThread;
    public Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private MediaCodec mCodec;
    private Surface mEncoderSurface;
    private String mMimeType = "video/avc";
    private String mCodecName = "OMX.amlogic.video.encoder.avc";
    private int mCodecFps = 30;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;


    static class Singleton {
        private static final PreviewFragment INSTANCE = new PreviewFragment();
    }

    public static PreviewFragment instance() {
        return Singleton.INSTANCE;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preview, container, false);
        previewContent = view.findViewById(R.id.preview_content);
        startBackgroundThread();
        startPreview();
        return view;
    }

    private void startPreview() {
        previewContent.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable");
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged");
            mWidth = width;
            mHeight = height;
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            Log.e(TAG, "onSurfaceTextureDestroyed");
            closeCameraDevice();
            stopCodec();
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            Log.e(TAG, "onSurfaceTextureUpdated");
        }

    };

    private void openCamera() {
        setUpCameraOutputs();
//        configureTransform(previewContent.getWidth(), previewContent.getHeight());
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            startCodec();
            manager.openCamera(String.valueOf(mCameraId), mStateCallback, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {


        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.e(TAG, "onOpened");
            synchronized (this) {
                try {
                    mCameraOpenCloseLock.release();
                    mCameraDevice = cameraDevice;
                    createCameraPreviewSession();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.e(TAG, "onDisconnected");
            synchronized (this) {
                try {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "onError");
            synchronized (this) {
                try {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };


    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void setUpCameraOutputs() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(String.valueOf(mCameraId));
            // Check if the flash is supported.
            Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Log.e(TAG, "flash supported: " + available);
            mFlashSupported = available == null ? false : available;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        try {
            mPreviewSize = new Size(1920, 1080);
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            Log.e(TAG, "display rotation: " + rotation);
            matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scalex = (float) viewWidth / mPreviewSize.getWidth();
                float scaley = (float) viewHeight / mPreviewSize.getHeight();
                matrix.postScale(scaley, scalex, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);

            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }
            previewContent.setTransform(matrix);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void startCodec() {
        try {
            mCodec = MediaCodec.createEncoderByType(mMimeType);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(mMimeType, mWidth, mHeight);

            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 3 * mWidth * mHeight);//500kbps
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mCodecFps);
//            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); //COLOR_FormatSurface
//            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
//                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);  //COLOR_FormatYUV420Planar
//            mediaFormat.setInteger(MediaFormat.KEY_ROTATION, 90);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 3);

            if (Build.VERSION.SDK_INT > 21) {
                mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            }
            mCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mEncoderSurface = mCodec.createInputSurface();
            mCodec.setCallback(new EncoderCallback());
            mCodec.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private class EncoderCallback extends MediaCodec.Callback {

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.e(TAG, "onInputBufferAvailable index: " + index);
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            Log.e(TAG, "onOutputBufferAvailable index: " + index);
            try {
                //帧数据发送给服务器。
                ByteBuffer outPutByteBuffer = mCodec.getOutputBuffer(index);
                byte[] outData = new byte[info.size];
                outPutByteBuffer.get(outData);
                mCodec.releaseOutputBuffer(index, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "onError message: " + e.getMessage());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.e(TAG, "onOutputFormatChanged");
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = previewContent.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mWidth, mHeight);

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(String.valueOf(mCameraId));
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] size = map.getOutputSizes(SurfaceHolder.class);
            for (int i = 0; i < size.length; i++) {
                Log.e(TAG, "resolution " + i + " width: " + size[i].getWidth() + "   height: " + size[i].getHeight());
            }

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

//            mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            for (int i = 0; i < fpsRanges.length; i++) {
                Log.e(TAG, "fpsRanges " + i + " fps: " + fpsRanges[i].toString());
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));

            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(mEncoderSurface);

            //create a CameraCaptureSession for camera preview.

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mEncoderSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigured");
                            synchronized (this) {
                                // The camera is already closed
                                if (null == mCameraDevice) {
                                    return;
                                }
                                // When the session is ready, we start displaying the preview.
                                mCaptureSession = cameraCaptureSession;
                                try {
                                    // Auto focus should be continuous for camera preview.
                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                    // Flash is automatically enabled when necessary.
                                    setAutoFlash(mPreviewRequestBuilder);
                                    // Finally, we start displaying the camera preview.
                                    mPreviewRequest = mPreviewRequestBuilder.build();
                                    mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "onConfigureFailed");
                        }
                    }, null
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            Log.e(TAG, "onCaptureProgressed");
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Log.e(TAG, "onCaptureCompleted");
        }

    };

    public void stopCodec() {
        try {
            if (mCodec != null) {
                synchronized (this) {
                    if (mCodec != null) {
                        mCodec.stop();
                        mCodec.release();
                        mCodec = null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            mCodec = null;
        }
    }

    @Override
    public void onDestroyView() {
        closeCameraDevice();
        stopCodec();
        super.onDestroyView();
    }

    public void closeCameraDevice() {
        if (mCameraDevice != null) {
            synchronized (this) {
                if (mCameraDevice != null) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
            }
        }
    }

}
